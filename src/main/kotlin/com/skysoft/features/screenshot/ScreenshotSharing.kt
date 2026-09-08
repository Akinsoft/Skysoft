package com.skysoft.features.screenshot

import com.skysoft.SkysoftMod
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.net.KeyedAsyncRequestSlots
import java.net.URI
import java.nio.file.Path
import java.time.Instant
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

internal object ScreenshotSharing {
    private val statuses = mutableMapOf<String, ScreenshotShareStatus>()
    private val uploadRequests = KeyedAsyncRequestSlots<String, ScreenshotUpload>(Minecraft.getInstance())

    fun status(path: Path): ScreenshotShareStatus = synchronized(statuses) {
        val key = path.normalizedScreenshotPath()
        statuses[key]?.takeUnless {
            it is ScreenshotShareStatus.Uploaded && it.upload.expiresAtEpochSecond <= Instant.now().epochSecond
        } ?: ScreenshotUploadMetadataStore.uploadFor(path).let { stored ->
            if (stored == null) {
                ScreenshotShareStatus.Ready
            } else {
                ScreenshotShareStatus.Uploaded(stored)
            }.also { statuses[key] = it }
        }
    }

    fun request(path: Path, parent: Screen?) {
        val current = status(path)
        if (current is ScreenshotShareStatus.Uploaded) {
            copyLink(current.upload)
            return
        }
        if (current == ScreenshotShareStatus.Uploading) return

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
        val key = path.normalizedScreenshotPath()
        synchronized(statuses) {
            val current = status(path)
            if (current is ScreenshotShareStatus.Uploaded) {
                copyLink(current.upload)
                return
            }
            if (current == ScreenshotShareStatus.Uploading) return

            statuses[key] = ScreenshotShareStatus.Uploading
            uploadRequests.startIfIdle(
                key,
                { SkysoftScreenshotUploadProvider.upload(path) },
            ) { upload, failure -> completeUpload(path, key, upload, failure) }
        }
    }

    fun buttonLabel(path: Path): String =
        when (status(path)) {
            ScreenshotShareStatus.Ready -> "Share"
            ScreenshotShareStatus.Uploading -> "Uploading..."
            is ScreenshotShareStatus.Uploaded -> "Copy Link"
            ScreenshotShareStatus.Failed -> "Retry Share"
        }

    fun invalidate(path: Path) {
        val key = path.normalizedScreenshotPath()
        synchronized(statuses) {
            uploadRequests.cancel(key)
            statuses.remove(key)
            ScreenshotUploadMetadataStore.forget(path)
        }
    }

    private fun completeUpload(path: Path, key: String, upload: ScreenshotUpload?, failure: Throwable?) {
        synchronized(statuses) {
            if (statuses[key] != ScreenshotShareStatus.Uploading) return
            if (failure != null || upload == null) {
                statuses[key] = ScreenshotShareStatus.Failed
                SkysoftMod.LOGGER.warn("Screenshot upload failed", failure)
                SkysoftChat.error("Could not upload the screenshot. See the log for details.")
            } else {
                ScreenshotUploadMetadataStore.remember(path, upload)
                statuses[key] = ScreenshotShareStatus.Uploaded(upload)
                copyLink(upload)
                announce(upload)
            }
        }
    }

    private fun copyLink(upload: ScreenshotUpload) {
        Minecraft.getInstance().keyboardHandler.clipboard = upload.pageUrl
        SkysoftChat.success("Copied the screenshot link to your clipboard.")
    }

    private fun announce(upload: ScreenshotUpload) {
        val link = Component.literal(upload.pageUrl).withStyle { style ->
            style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(ClickEvent.OpenUrl(URI.create(upload.pageUrl)))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Click to open")))
        }
        SkysoftChat.chat(Component.literal("Screenshot uploaded: ").append(link))
    }
}

internal sealed interface ScreenshotShareStatus {
    data object Ready : ScreenshotShareStatus
    data object Uploading : ScreenshotShareStatus
    data class Uploaded(val upload: ScreenshotUpload) : ScreenshotShareStatus
    data object Failed : ScreenshotShareStatus
}
