package com.skysoft.features.spotify

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.BrowserUtilities
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.sun.net.httpserver.HttpExchange
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft

object SpotifyAuthentication {
    private val lock = Any()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var storedLoaded = false
    private var storedAuthorization: StoredAuthorization? = null
    private var accessToken: AccessToken? = null
    private var refreshFuture: CompletableFuture<String?>? = null
    private var pendingAuthorization: PendingAuthorization? = null
    private var sessionVersion = 0

    fun register() {
        SkysoftClientEvents.onClientStopping("Spotify authentication cleanup") { stopPendingAuthorization() }
    }

    fun openDashboard() {
        if (BrowserUtilities.open(SPOTIFY_DASHBOARD) == BrowserUtilities.OpenResult.FAILED) {
            SkysoftChat.error("Could not open the Spotify dashboard.")
        }
    }

    fun copyRedirectUri() {
        Minecraft.getInstance().keyboardHandler.setClipboard(SpotifyOAuth.CALLBACK_URI)
        SkysoftChat.chat("Spotify Redirect URI copied.")
    }

    fun connect() {
        val clientId = settings().clientId.trim()
        if (clientId.isEmpty()) {
            SkysoftChat.error("Enter your Spotify Client ID before connecting.")
            return
        }
        val pending = synchronized(lock) {
            if (pendingAuthorization != null) return@synchronized null
            try {
                SpotifyOAuth.createPending(clientId, sessionVersion + 1, ::handleCallback).also {
                    sessionVersion++
                    pendingAuthorization = it
                }
            } catch (failure: Exception) {
                SkysoftMod.LOGGER.warn("Could not start Spotify authorization", failure)
                null
            }
        }
        if (pending == null) {
            SkysoftChat.error(
                "Spotify connection could not start. Check that port ${SpotifyOAuth.CALLBACK_PORT} is available.",
            )
            return
        }
        SkysoftConfigGui.config().saveNow()
        pending.server.start()
        if (BrowserUtilities.open(SpotifyOAuth.authorizationUrl(pending)) == BrowserUtilities.OpenResult.FAILED) {
            didStopPendingAuthorization(pending)
            SkysoftChat.error("Could not open Spotify in your browser.")
            return
        }
        CompletableFuture.delayedExecutor(AUTHORIZATION_TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES).execute {
            if (didStopPendingAuthorization(pending)) {
                Minecraft.getInstance().execute { SkysoftChat.error("Spotify connection timed out.") }
            }
        }
        SkysoftChat.chat("Finish connecting Spotify in your browser.")
    }

    fun disconnect() {
        synchronized(lock) {
            sessionVersion++
            accessToken = null
            refreshFuture?.cancel(true)
            refreshFuture = null
            storedLoaded = true
            storedAuthorization = null
        }
        stopPendingAuthorization()
        runCatching { SkysoftConfigFiles.deleteWithBackups(SkysoftConfigFiles.spotifyAuthentication) }
            .onFailure { SkysoftMod.LOGGER.warn("Could not delete Spotify authentication", it) }
        SpotifyDisplay.clear()
        SkysoftChat.chat("Spotify disconnected.")
    }

    internal fun accessToken(): CompletableFuture<String?> = synchronized(lock) {
        val current = accessToken
        if (current != null && current.expiresAtMillis > System.currentTimeMillis() + EXPIRY_MARGIN_MILLIS) {
            return@synchronized CompletableFuture.completedFuture(current.value)
        }
        refreshFuture?.let { return@synchronized it }
        val stored = loadStoredAuthorization()
        if (stored == null || stored.clientId != settings().clientId.trim()) {
            return@synchronized CompletableFuture.completedFuture(null)
        }
        val version = sessionVersion
        refresh(stored).thenApply { response ->
            synchronized(lock) {
                if (version != sessionVersion) return@synchronized null
                installToken(stored.clientId, stored.refreshToken, response)
            }
        }.whenComplete { _, failure ->
            synchronized(lock) {
                refreshFuture = null
            }
            val cause = failure?.unwrap() as? SpotifyAuthenticationException ?: return@whenComplete
            if (cause.statusCode in CLIENT_AUTHENTICATION_FAILURES) {
                clearStoredAuthorization()
                Minecraft.getInstance().execute { SkysoftChat.error("Spotify needs to be connected again.") }
            }
        }.also { refreshFuture = it }
    }

    internal fun invalidateAccessToken() {
        synchronized(lock) { accessToken = null }
    }

    private fun handleCallback(pending: PendingAuthorization, exchange: HttpExchange) {
        try {
            val parameters = SpotifyOAuth.parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val valid = synchronized(lock) { pendingAuthorization === pending } && parameters["state"] == pending.state
            val code = parameters["code"]
            when {
                !valid -> SpotifyOAuth.respond(exchange, HTTP_BAD_REQUEST, "Spotify connection could not be verified.")
                code == null -> SpotifyOAuth.respond(exchange, HTTP_BAD_REQUEST, "Spotify connection was cancelled.")
                else -> {
                    SpotifyOAuth.respond(exchange, HTTP_OK, "Spotify is connected to Skysoft. You can close this page.")
                    exchangeCode(pending, code)
                }
            }
        } finally {
            didStopPendingAuthorization(pending)
        }
    }

    private fun exchangeCode(pending: PendingAuthorization, code: String) {
        SpotifyOAuth.tokenRequest(
            "client_id" to pending.clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to SpotifyOAuth.CALLBACK_URI,
            "code_verifier" to pending.verifier,
        ).thenApply(::parseTokenResponse).whenComplete { response, failure ->
            Minecraft.getInstance().execute {
                if (response != null) {
                    synchronized(lock) {
                        if (pending.sessionVersion != sessionVersion) return@execute
                        installToken(pending.clientId, response.refreshToken.orEmpty(), response)
                    }
                    SkysoftChat.success("Spotify connected.")
                    SpotifyDisplay.requestRefresh()
                } else {
                    SkysoftMod.LOGGER.warn("Spotify token exchange failed", failure?.unwrap())
                    SkysoftChat.error("Spotify connection failed. Try connecting again.")
                }
            }
        }
    }

    private fun refresh(stored: StoredAuthorization): CompletableFuture<TokenResponse> =
        SpotifyOAuth.tokenRequest(
            "client_id" to stored.clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to stored.refreshToken,
        ).thenApply(::parseTokenResponse)

    private fun parseTokenResponse(response: java.net.http.HttpResponse<String>): TokenResponse {
        if (response.statusCode() !in HTTP_SUCCESS) {
            throw SpotifyAuthenticationException(response.statusCode())
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        return TokenResponse(
            accessToken = json.get("access_token").asString,
            expiresInSeconds = json.get("expires_in").asLong,
            refreshToken = json.get("refresh_token")?.takeUnless { it.isJsonNull }?.asString,
        )
    }

    private fun installToken(clientId: String, previousRefreshToken: String, response: TokenResponse): String {
        accessToken = AccessToken(
            response.accessToken,
            System.currentTimeMillis() + response.expiresInSeconds * MILLIS_PER_SECOND,
        )
        val refreshToken = response.refreshToken ?: previousRefreshToken
        if (refreshToken.isNotEmpty()) saveStoredAuthorization(StoredAuthorization(clientId, refreshToken))
        return response.accessToken
    }

    private fun loadStoredAuthorization(): StoredAuthorization? {
        if (storedLoaded) return storedAuthorization
        storedLoaded = true
        val path = SkysoftConfigFiles.spotifyAuthentication
        storedAuthorization = runCatching {
            if (!SkysoftConfigFiles.hasFileOrBackup(path)) return@runCatching null
            SkysoftConfigFiles.readWithBackup(path) { source ->
                Files.newBufferedReader(source).use { reader ->
                    val json = JsonParser.parseReader(reader).asJsonObject
                    StoredAuthorization(json.get("clientId").asString, json.get("refreshToken").asString)
                }
            }
        }.onFailure { SkysoftMod.LOGGER.warn("Could not load Spotify authentication", it) }.getOrNull()
        return storedAuthorization
    }

    private fun saveStoredAuthorization(authorization: StoredAuthorization) {
        storedLoaded = true
        storedAuthorization = authorization
        val json = mapOf("clientId" to authorization.clientId, "refreshToken" to authorization.refreshToken)
        SkysoftConfigFiles.writeStringSafely(SkysoftConfigFiles.spotifyAuthentication, gson.toJson(json))
    }

    private fun clearStoredAuthorization() {
        synchronized(lock) {
            accessToken = null
            storedLoaded = true
            storedAuthorization = null
        }
        runCatching { SkysoftConfigFiles.deleteWithBackups(SkysoftConfigFiles.spotifyAuthentication) }
            .onFailure { SkysoftMod.LOGGER.warn("Could not clear Spotify authentication", it) }
    }

    private fun didStopPendingAuthorization(pending: PendingAuthorization): Boolean {
        val stopped = synchronized(lock) {
            if (pendingAuthorization !== pending) return@synchronized false
            pendingAuthorization = null
            true
        }
        if (stopped) pending.server.stop(0)
        return stopped
    }

    private fun stopPendingAuthorization() {
        val pending = synchronized(lock) { pendingAuthorization.also { pendingAuthorization = null } }
        pending?.server?.stop(0)
    }

    private fun settings() = SkysoftConfigGui.config().gui.spotifyDisplay.settings
    private fun Throwable.unwrap(): Throwable = (this as? java.util.concurrent.CompletionException)?.cause ?: this

    private const val SPOTIFY_DASHBOARD = "https://developer.spotify.com/dashboard"
    private const val AUTHORIZATION_TIMEOUT_MINUTES = 5L
    private const val EXPIRY_MARGIN_MILLIS = 30_000L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val HTTP_OK = 200
    private const val HTTP_BAD_REQUEST = 400
    private val HTTP_SUCCESS = 200..299
    private val CLIENT_AUTHENTICATION_FAILURES = setOf(400, 401)
}

private data class StoredAuthorization(val clientId: String, val refreshToken: String)
private data class AccessToken(val value: String, val expiresAtMillis: Long)
private data class TokenResponse(val accessToken: String, val expiresInSeconds: Long, val refreshToken: String?)
private class SpotifyAuthenticationException(val statusCode: Int) :
    IllegalStateException("Spotify authentication returned HTTP $statusCode")
