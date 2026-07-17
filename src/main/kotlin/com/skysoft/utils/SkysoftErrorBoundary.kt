package com.skysoft.utils

import com.skysoft.SkysoftMod
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

internal object SkysoftErrorBoundary {
    @PublishedApi
    internal val disabledBoundaries: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pendingReports = ConcurrentLinkedQueue<String>()
    private val clipboardReports: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val isRegistered = AtomicBoolean(false)

    fun register() {
        if (!isRegistered.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register { flushPendingReports() }
    }

    internal inline fun run(boundary: String, action: () -> Unit) {
        value(boundary, Unit, action)
    }

    internal inline fun <T> value(boundary: String, fallback: T, action: () -> T): T {
        if (boundary in disabledBoundaries) return fallback
        return try {
            action()
        } catch (exception: Exception) {
            disable(boundary, exception)
            fallback
        }
    }

    internal fun aroundUnit(
        boundary: String,
        original: () -> Unit,
        action: (() -> Unit) -> Unit,
    ) {
        var isOriginalCalled = false
        var originalFailure: Throwable? = null
        run(boundary) {
            action {
                if (!isOriginalCalled) {
                    isOriginalCalled = true
                    try {
                        original()
                    } catch (failure: Throwable) {
                        originalFailure = failure
                    }
                }
            }
        }
        if (!isOriginalCalled) original()
        originalFailure?.let { throw it }
    }

    @PublishedApi
    internal fun disable(boundary: String, exception: Exception) {
        if (!disabledBoundaries.add(boundary)) return
        SkysoftMod.LOGGER.error("Skysoft disabled $boundary after an error", exception)
        try {
            pendingReports.add(
                formatReport(
                    boundary,
                    exception,
                    SkysoftMod.VERSION,
                    modVersion("minecraft"),
                ),
            )
        } catch (reportingException: Exception) {
            SkysoftMod.LOGGER.error("Skysoft could not create an error report", reportingException)
        }
    }

    internal fun formatReport(
        boundary: String,
        exception: Exception,
        skysoftVersion: String,
        minecraftVersion: String,
    ): String {
        val trace = StringWriter().also { writer ->
            exception.printStackTrace(PrintWriter(writer))
        }.toString().trimEnd()
        return "```\nSkysoft $skysoftVersion / Minecraft $minecraftVersion\n" +
            "Context: $boundary\n\n$trace\n```"
    }

    internal fun errorMessage(report: String): Component {
        clipboardReports.add(report)
        val hover = HoverEvent.ShowText(
            Component.literal("Click to copy to clipboard, so you can share in Akincord.")
                .withStyle(ChatFormatting.GRAY),
        )
        return Component.empty()
            .append(Component.literal("An error occurred. The affected part of Skysoft has been disabled until restart. "))
            .append(Component.literal("Click here to copy the error report.").withStyle(ChatFormatting.UNDERLINE))
            .withStyle { style ->
                style.withColor(ChatFormatting.RED)
                    .withClickEvent(ClickEvent.CopyToClipboard(report))
                    .withHoverEvent(hover)
            }
    }

    internal fun pollPendingReport(): String? = pendingReports.poll()

    internal fun isErrorReport(report: String): Boolean = report in clipboardReports

    internal fun acknowledgeClipboardCopy(report: String) {
        if (!isErrorReport(report)) return
        try {
            SkysoftChat.chat("Copied the error report to your clipboard.")
        } catch (exception: Exception) {
            SkysoftMod.LOGGER.error("Skysoft could not acknowledge an error report copy", exception)
        }
    }

    internal fun onClientThread(boundary: String, action: () -> Unit) {
        run(boundary) {
            Minecraft.getInstance().execute { run(boundary, action) }
        }
    }

    private fun flushPendingReports() {
        if (Minecraft.getInstance().player == null) return
        while (true) {
            val report = pollPendingReport() ?: return
            try {
                SkysoftChat.chat(errorMessage(report))
            } catch (exception: Exception) {
                SkysoftMod.LOGGER.error("Skysoft could not show an error report in chat", exception)
            }
        }
    }

    private fun modVersion(modId: String): String =
        FabricLoader.getInstance().getModContainer(modId)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
}
