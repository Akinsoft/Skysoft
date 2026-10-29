package com.skysoft.utils

import com.skysoft.SkysoftMod
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
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
        SkysoftClientEvents.onEndTick(
            "Pending error reports",
            isActive = { pendingReports.isNotEmpty() },
        ) { flushPendingReports() }
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
    ) = aroundUnit(boundary, Unit, { original() }) { callOriginal ->
        action { callOriginal(Unit) }
    }

    internal fun <T> aroundUnit(
        boundary: String,
        initialArgument: T,
        original: (T) -> Unit,
        action: ((T) -> Unit) -> Unit,
    ) {
        var isOriginalCalled = false
        var originalFailure: Throwable? = null
        run(boundary) {
            action { argument ->
                if (!isOriginalCalled) {
                    isOriginalCalled = true
                    try {
                        original(argument)
                    } catch (failure: Throwable) {
                        originalFailure = failure
                    }
                }
            }
        }
        if (!isOriginalCalled) original(initialArgument)
        originalFailure?.let { throw it }
    }

    @PublishedApi
    internal fun disable(boundary: String, exception: Exception) {
        if (!disabledBoundaries.add(boundary)) return
        SkysoftMod.LOGGER.error("Skysoft disabled $boundary after an error", exception)
        try {
            pendingReports.add(formatReport(boundary, exception))
        } catch (reportingException: Exception) {
            SkysoftMod.LOGGER.error("Skysoft could not create an error report", reportingException)
        }
    }

    private fun formatReport(boundary: String, exception: Exception): String {
        val trace = StringWriter().also { writer ->
            exception.printStackTrace(PrintWriter(writer))
        }.toString().trimEnd()
        return "```\nSkysoft ${SkysoftMod.VERSION} / Minecraft ${SkysoftMod.modVersion("minecraft")}\n" +
            "Context: $boundary\n\n$trace\n```"
    }

    private fun errorMessage(report: String): Component {
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

    internal fun acknowledgeClipboardCopy(report: String) {
        if (report !in clipboardReports) return
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
            val report = pendingReports.poll() ?: return
            try {
                SkysoftChat.chat(errorMessage(report))
            } catch (exception: Exception) {
                SkysoftMod.LOGGER.error("Skysoft could not show an error report in chat", exception)
            }
        }
    }
}
