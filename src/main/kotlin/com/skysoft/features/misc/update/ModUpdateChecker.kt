package com.skysoft.features.misc.update

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skysoft.SkysoftMod
import com.skysoft.utils.BrowserUtilities
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.net.SkysoftHttp
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.metadata.CustomValue
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture

object ModUpdateChecker {
    private const val API = "https://api.modrinth.com/v2/project"
    private const val RELEASE = "release"
    private const val FABRIC = "fabric"

    private val gson = Gson()
    private val versionListType = object : TypeToken<List<ModrinthVersionInfo>>() {}.type

    @Volatile
    var status: UpdateStatus = UpdateStatus(UpdateState.NOT_CHECKED)
        private set

    private var registered = false
    private var activeRequest: CompletableFuture<*>? = null
    private var announcedVersion: String? = null

    fun register() {
        if (registered) return
        registered = true

        SkysoftClientEvents.onJoin("Mod Update check") {
            check(announce = true)
        }
        SkysoftClientEvents.onDisconnect("Mod Update disconnect reset") {
            announcedVersion = null
        }
    }

    fun check(force: Boolean = false, announce: Boolean = false) {
        val current = status
        if (current.state == UpdateState.CHECKING) return
        if (!force && current.state != UpdateState.NOT_CHECKED && current.state != UpdateState.FAILED) {
            if (announce) announceUpdate()
            return
        }

        val metadata = runCatching { updateMetadata() }.getOrElse {
            fail(it, chat = force)
            return
        }

        status = UpdateStatus(UpdateState.CHECKING)
        activeRequest?.cancel(true)
        activeRequest = SkysoftHttp.getString(metadata.url(), Duration.ofSeconds(UPDATE_REQUEST_TIMEOUT_SECONDS))
            .thenApply { response -> latestUpdate(response, metadata.project) }
            .whenComplete { update, error ->
                SkysoftErrorBoundary.onClientThread("Mod Update async completion") {
                    if (error != null) {
                        fail(error, chat = force)
                        return@onClientThread
                    }
                    if (update == null) {
                        status = UpdateStatus(UpdateState.CURRENT)
                        if (force) SkysoftChat.success("Skysoft is up to date.")
                        return@onClientThread
                    }
                    status = UpdateStatus(UpdateState.AVAILABLE, update)
                    if (force || announce) announceUpdate()
                }
            }
    }

    fun openDownload(): DownloadOpenResult {
        val update = status.update ?: return DownloadOpenResult.NOT_READY
        return when (BrowserUtilities.open(update.url)) {
            BrowserUtilities.OpenResult.OPENED -> DownloadOpenResult.OPENED
            BrowserUtilities.OpenResult.FAILED -> {
                SkysoftChat.error("Could not open the Skysoft download page.")
                DownloadOpenResult.FAILED
            }
        }
    }

    fun buttonText(): String =
        when (status.state) {
            UpdateState.NOT_CHECKED -> "Check"
            UpdateState.CHECKING -> "Checking..."
            UpdateState.CURRENT -> "Check Again"
            UpdateState.AVAILABLE -> "Download"
            UpdateState.FAILED -> "Retry"
        }

    fun statusText(currentStatus: UpdateStatus = status): String =
        when (currentStatus.state) {
            UpdateState.NOT_CHECKED -> "Not checked"
            UpdateState.CHECKING -> "Checking..."
            UpdateState.CURRENT -> "Up to date"
            UpdateState.AVAILABLE -> currentStatus.update?.let { "${it.version} available" } ?: "Update available"
            UpdateState.FAILED -> "Check failed"
        }

    internal fun latestUpdate(
        response: String,
        project: String,
        currentVersion: String = SkysoftMod.VERSION,
    ): SkysoftUpdate? {
        val versions = requireNotNull(gson.fromJson<List<ModrinthVersionInfo>>(response, versionListType)) {
            "Modrinth returned an empty version response"
        }
        versions.forEach { version ->
            require(version.id.isNotBlank()) { "Modrinth returned a version without an id" }
            require(version.versionNumber.isNotBlank()) { "Modrinth returned a version without a number" }
            require(version.versionType.isNotBlank()) { "Modrinth returned a version without a type" }
        }
        return versions.asSequence()
            .filter { it.versionType == RELEASE }
            .filter { compareVersions(currentVersion, it.versionNumber) < 0 }
            .maxWithOrNull { a, b -> compareVersions(a.versionNumber, b.versionNumber) }
            ?.let { SkysoftUpdate(it.versionNumber, "https://modrinth.com/mod/$project/version/${it.id}") }
    }

    private fun announceUpdate() {
        val update = status.update ?: return
        if (announcedVersion == update.version) return
        announcedVersion = update.version
        SkysoftChat.link(
            "Update available: ${SkysoftMod.VERSION} -> ${update.version}. Click to download.",
            update.url,
            "Open Skysoft ${update.version} on Modrinth",
        )
    }

    private fun fail(error: Throwable, chat: Boolean) {
        status = UpdateStatus(UpdateState.FAILED)
        SkysoftMod.LOGGER.warn("Skysoft update check failed", error)
        if (chat) SkysoftChat.error("Could not check for Skysoft updates. See the log for details.")
    }

    private fun updateMetadata(): UpdateMetadata {
        val data = FabricLoader.getInstance().getModContainer(SkysoftMod.MOD_ID)
            .orElse(null)
            ?.metadata
            ?.getCustomValue(SkysoftMod.MOD_ID)
            ?.asObject
        return UpdateMetadata(
            project = metadataString(data, "modrinth_project", "Modrinth project"),
            minecraftVersion = metadataString(data, "minecraft_version", "Minecraft version"),
        )
    }

    private fun UpdateMetadata.url(): String {
        val loader = encode(gson.toJson(listOf(FABRIC)))
        val gameVersion = encode(gson.toJson(listOf(minecraftVersion)))
        return "$API/$project/version?loaders=$loader&game_versions=$gameVersion"
    }

    internal fun compareVersions(first: String, second: String): Int =
        Version.parse(first).compareTo(Version.parse(second))

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun metadataString(data: CustomValue.CvObject?, key: String, label: String): String {
        val value = data?.get(key)?.getAsString()
        require(!value.isNullOrBlank()) { "Missing $label metadata" }
        return value
    }

    private const val UPDATE_REQUEST_TIMEOUT_SECONDS = 15L
}

data class UpdateStatus(
    val state: UpdateState,
    val update: SkysoftUpdate? = null,
)

enum class UpdateState {
    NOT_CHECKED,
    CHECKING,
    CURRENT,
    AVAILABLE,
    FAILED,
}

private data class UpdateMetadata(
    val project: String,
    val minecraftVersion: String,
)
