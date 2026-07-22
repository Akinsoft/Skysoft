package com.skysoft.features.spotify

import com.skysoft.SkysoftMod
import com.skysoft.utils.net.SkysoftHttp
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

internal object SpotifyOAuth {
    const val CALLBACK_PORT = 25566
    const val CALLBACK_URI = "http://127.0.0.1:$CALLBACK_PORT/callback"

    fun createPending(
        clientId: String,
        sessionVersion: Int,
        callback: (PendingAuthorization, HttpExchange) -> Unit,
    ): PendingAuthorization {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getByName(CALLBACK_HOST), CALLBACK_PORT),
            SERVER_BACKLOG,
        )
        return PendingAuthorization(
            clientId,
            randomUrlToken(PKCE_RANDOM_BYTES),
            randomUrlToken(STATE_RANDOM_BYTES),
            server,
            sessionVersion,
        ).also { pending ->
            server.createContext(CALLBACK_PATH) { exchange -> callback(pending, exchange) }
        }
    }

    fun authorizationUrl(pending: PendingAuthorization): String {
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        return "$AUTHORIZE_ENDPOINT?" + formBody(
            "client_id" to pending.clientId,
            "response_type" to "code",
            "redirect_uri" to CALLBACK_URI,
            "state" to pending.state,
            "scope" to SCOPES,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
        )
    }

    fun tokenRequest(
        vararg parameters: Pair<String, String>,
    ): java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<String>> {
        val request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
            .timeout(Duration.ofSeconds(TOKEN_TIMEOUT_SECONDS))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "Skysoft/${SkysoftMod.VERSION}")
            .POST(HttpRequest.BodyPublishers.ofString(formBody(*parameters)))
            .build()
        return SkysoftHttp.sendString(request)
    }

    fun parseQuery(query: String): Map<String, String> = query.split('&').mapNotNull { parameter ->
        val separator = parameter.indexOf('=')
        if (separator < 0) null else decode(parameter.substring(0, separator)) to decode(parameter.substring(separator + 1))
    }.toMap()

    fun respond(exchange: HttpExchange, status: Int, message: String) {
        val body = """
            <!doctype html><html><head><meta charset="utf-8"><title>Skysoft Spotify</title></head>
            <body style="background:#101418;color:#eef6ff;font:18px sans-serif;text-align:center;padding-top:12vh">
            <h1 style="color:#45a3ff">Skysoft</h1><p>$message</p></body></html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun formBody(vararg parameters: Pair<String, String>): String = parameters.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun randomUrlToken(byteCount: Int): String = ByteArray(byteCount).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    private const val CALLBACK_HOST = "127.0.0.1"
    private const val CALLBACK_PATH = "/callback"
    private const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
    private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    private const val SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state"
    private const val PKCE_RANDOM_BYTES = 64
    private const val STATE_RANDOM_BYTES = 24
    private const val SERVER_BACKLOG = 1
    private const val TOKEN_TIMEOUT_SECONDS = 20L
}

internal data class PendingAuthorization(
    val clientId: String,
    val verifier: String,
    val state: String,
    val server: HttpServer,
    val sessionVersion: Int,
)
