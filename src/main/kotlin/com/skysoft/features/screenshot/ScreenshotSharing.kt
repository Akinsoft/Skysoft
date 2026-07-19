package com.skysoft.features.screenshot

import com.skysoft.SkysoftMod
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

internal object ScreenshotSharing {
    private val uploadProvider: ScreenshotUploadProvider = SkysoftScreenshotUploadProvider
    private val statuses = ConcurrentHashMap<String, ScreenshotShareStatus>()

    fun status(path: Path): ScreenshotShareStatus {
        val key = normalizedPath(path)
        statuses[key]?.let { return it }
        val stored = ScreenshotUploadMetadataStore.uploadFor(path)
        return if (stored == null) {
            ScreenshotShareStatus(ScreenshotShareState.READY)
        } else {
            ScreenshotShareStatus(ScreenshotShareState.UPLOADED, stored)
        }.also { statuses[key] = it }
    }

    fun request(path: Path, parent: Screen?) {
        val current = status(path)
        if (current.state == ScreenshotShareState.UPLOADED) {
            current.upload?.let(::copyLink)
            return
        }
        if (current.state == ScreenshotShareState.UPLOADING) return

        val minecraft = Minecraft.getInstance()
        MinecraftClient.setScreen(
            ConfirmScreen(
                { accepted ->
                    MinecraftClient.setScreen(parent)
                    if (accepted) share(path)
                },
                Component.literal("Share Screenshot"),
                Component.literal(
                    "This uploads the screenshot publicly to ImgBB for 30 days. Anyone with the link can view it.",
                ),
                Component.literal("Upload & Copy Link"),
                Component.literal("Cancel"),
            ),
        )
    }

    fun share(path: Path) {
        val current = status(path)
        if (current.state == ScreenshotShareState.UPLOADED) {
            current.upload?.let(::copyLink)
            return
        }
        if (current.state == ScreenshotShareState.UPLOADING) return

        val key = normalizedPath(path)
        statuses[key] = ScreenshotShareStatus(ScreenshotShareState.UPLOADING)
        uploadProvider.upload(path).whenComplete { upload, failure ->
            Minecraft.getInstance().execute {
                if (failure != null || upload == null) {
                    statuses[key] = ScreenshotShareStatus(ScreenshotShareState.FAILED)
                    SkysoftMod.LOGGER.warn("Screenshot upload failed", failure)
                    SkysoftChat.error("Could not upload the screenshot. See the log for details.")
                } else {
                    ScreenshotUploadMetadataStore.remember(path, upload)
                    statuses[key] = ScreenshotShareStatus(ScreenshotShareState.UPLOADED, upload)
                    copyLink(upload)
                    announce(upload)
                }
            }
        }
    }

    fun buttonLabel(path: Path): String =
        when (status(path).state) {
            ScreenshotShareState.READY -> "Share"
            ScreenshotShareState.UPLOADING -> "Uploading..."
            ScreenshotShareState.UPLOADED -> "Copy Link"
            ScreenshotShareState.FAILED -> "Retry Share"
        }

    private fun copyLink(upload: ScreenshotUpload) {
        Minecraft.getInstance().keyboardHandler.clipboard = upload.imageUrl
        SkysoftChat.success("Copied the screenshot link to your clipboard.")
    }

    private fun announce(upload: ScreenshotUpload) {
        val link = Component.literal(upload.imageUrl).withStyle { style ->
            style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(ClickEvent.OpenUrl(URI.create(upload.imageUrl)))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Click to open")))
        }
        SkysoftChat.chat(Component.literal("Screenshot uploaded: ").append(link))
    }

    private fun normalizedPath(path: Path): String = path.toAbsolutePath().normalize().toString()
}

internal data class ScreenshotShareStatus(
    val state: ScreenshotShareState,
    val upload: ScreenshotUpload? = null,
)

internal enum class ScreenshotShareState {
    READY,
    UPLOADING,
    UPLOADED,
    FAILED,
}
