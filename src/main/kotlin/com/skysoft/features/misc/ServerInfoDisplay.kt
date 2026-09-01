package com.skysoft.features.misc

import com.skysoft.config.DisplayLabelStyle
import com.skysoft.config.ServerInfoDisplayStyle
import com.skysoft.config.ServerInfoLayout
import com.skysoft.config.ServerInfoMetric
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.container.horizontalLayout
import com.skysoft.utils.renderables.container.verticalLayout
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import java.util.Locale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.util.Util

object ServerInfoDisplay {
    private val pingTracker = ServerPingTracker()
    private var isPingTrackingActive = false
    private var hasActiveMetrics = false
    private val config get() = SkysoftConfigGui.config().gui.serverInfoDisplay

    fun register() {
        ServerTpsProvider.registerConsumer(TPS_CONSUMER_ID)
        SkysoftClientEvents.onEndTick(
            "Server Info metrics",
            isActive = { isConfigured() || hasActiveMetrics },
        ) { minecraft -> updateMetrics(minecraft) }
        SkysoftClientEvents.onJoin("Server Info join reset", ::resetMeasurements)
        SkysoftClientEvents.onDisconnect("Server Info disconnect reset", ::resetMeasurements)
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "server_info_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = { canRenderLive() },
                render = { context, _ -> renderHud(context) },
            ),
            object : HudEditorElement {
                override val id: String = "server_info_display"
                override val label: String = "Server Info Display"
                override val position get() = config.position
                override val hasEditorBackground: Boolean get() = !config.details.background
                override fun width(): Int = currentSimpleRenderable()?.width ?: 0
                override fun height(): Int = currentSimpleRenderable()?.height ?: 0
                override fun isVisible(): Boolean =
                    config.enabled &&
                        config.details.style == ServerInfoDisplayStyle.SIMPLE &&
                        configuredMetrics().isNotEmpty()
                override fun renderEditor(context: GuiGraphicsExtractor) {
                    currentSimpleRenderable()?.render(context)
                }
                override fun openConfig() = SkysoftConfigGui.open("Server Info Display")
            },
        )
        ServerInfoMetric.entries.forEach { metric ->
            HudEditorRegistry.register(object : HudEditorElement {
                override val id: String = "server_info_display_${metric.name.lowercase(Locale.ROOT)}"
                override val label: String = "Server Info: $metric"
                override val position get() = config.splitPosition(metric)
                override val hasEditorBackground: Boolean get() = !config.details.background
                override fun width(): Int = currentSplitRenderable(metric).width
                override fun height(): Int = currentSplitRenderable(metric).height
                override fun isVisible(): Boolean =
                    config.enabled &&
                        config.details.style == ServerInfoDisplayStyle.SPLIT &&
                        metric in configuredMetrics()
                override fun renderEditor(context: GuiGraphicsExtractor) = currentSplitRenderable(metric).render(context)
                override fun openConfig() = SkysoftConfigGui.open("Server Info Display")
            })
        }
    }

    internal fun recordPong(requestId: Long, timestampNanos: Long): PingSampleResult {
        if (!isPingTrackingActive) return PingSampleResult.IGNORED_INACTIVE
        return pingTracker.recordPong(requestId, timestampNanos)
    }

    internal val isPingMeasurementActive: Boolean
        get() = isPingTrackingActive

    private fun renderHud(context: GuiGraphicsExtractor) {
        if (!canRenderLive()) return
        val metrics = configuredMetrics()
        val values = currentValues()
        when (config.details.style) {
            ServerInfoDisplayStyle.SIMPLE -> {
                val renderable = simpleRenderable(metrics, values) ?: return
                config.position.renderRenderable(context, renderable)
            }
            ServerInfoDisplayStyle.SPLIT -> metrics.forEach { metric ->
                config.splitPosition(metric).renderRenderable(context, splitRenderable(metric, values))
            }
        }
    }

    private fun canRenderLive(minecraft: Minecraft = Minecraft.getInstance()): Boolean =
        config.enabled &&
            configuredMetrics().isNotEmpty() &&
            isRemoteServer(minecraft) &&
            !MinecraftClient.isGuiHidden(minecraft)

    private fun isRemoteServer(minecraft: Minecraft): Boolean =
        minecraft.connection != null && minecraft.level != null && minecraft.player != null && !minecraft.isLocalServer

    private fun configuredMetrics(): List<ServerInfoMetric> = config.settings.metrics.get()

    private fun updateMetrics(minecraft: Minecraft) {
        val activeMetrics = if (config.enabled && isRemoteServer(minecraft)) configuredMetrics() else emptyList()
        hasActiveMetrics = activeMetrics.isNotEmpty()
        ServerTpsProvider.updateConsumerState(TPS_CONSUMER_ID, ServerInfoMetric.TPS in activeMetrics)
        updatePing(minecraft, ServerInfoMetric.PING in activeMetrics)
    }

    private fun updatePing(minecraft: Minecraft, canMeasurePing: Boolean) {
        if (!canMeasurePing) {
            if (isPingTrackingActive) {
                pingTracker.reset()
                isPingTrackingActive = false
            }
            return
        }

        if (!isPingTrackingActive) {
            pingTracker.reset()
            isPingTrackingActive = true
        }
        val requestId = pingTracker.requestForTick(System.nanoTime(), Util.getMillis()) ?: return
        minecraft.connection?.send(ServerboundPingRequestPacket(requestId))
    }

    private fun resetMeasurements() {
        ServerTpsProvider.updateConsumerState(TPS_CONSUMER_ID, false)
        pingTracker.reset()
        isPingTrackingActive = false
        hasActiveMetrics = false
    }

    private fun isConfigured(): Boolean = config.enabled && configuredMetrics().isNotEmpty()

    private fun currentSimpleRenderable(): GuiRenderable? =
        simpleRenderable(configuredMetrics(), currentValues())

    private fun currentSplitRenderable(metric: ServerInfoMetric): GuiRenderable =
        splitRenderable(metric, currentValues())

    private fun simpleRenderable(metrics: List<ServerInfoMetric>, values: ServerInfoValues): GuiRenderable? {
        if (metrics.isEmpty()) return null
        val renderables = metrics.map { metric -> metricRenderable(metric, values) }
        val content = when (config.details.layout) {
            ServerInfoLayout.VERTICAL -> verticalLayout(renderables)
            ServerInfoLayout.HORIZONTAL -> horizontalLayout(renderables, spacing = 4)
        }
        return content.withOverlayPanel(config.details.background)
    }

    private fun splitRenderable(metric: ServerInfoMetric, values: ServerInfoValues): GuiRenderable =
        metricRenderable(metric, values).withOverlayPanel(config.details.background)

    private fun metricRenderable(metric: ServerInfoMetric, values: ServerInfoValues): GuiRenderable {
        val labelStyle = config.details.labelStyle
        val color = config.details.color(metric).get().toColor().rgb
        val text = StringRenderable(
            serverInfoText(
                metric,
                values,
                if (labelStyle == DisplayLabelStyle.SYMBOLS) DisplayLabelStyle.VALUES_ONLY else labelStyle,
            ),
            color = color,
        )
        if (labelStyle != DisplayLabelStyle.SYMBOLS) return text
        val icon = if (metric == ServerInfoMetric.PING) {
            SignalBarsRenderable(color)
        } else {
            StringRenderable(metric.symbol, color = color)
        }
        return horizontalLayout(listOf(icon, text), spacing = 2)
    }

    private fun currentValues(minecraft: Minecraft = Minecraft.getInstance()): ServerInfoValues = ServerInfoValues(
        fps = minecraft.fps,
        tps = ServerTpsProvider.tps,
        ping = pingTracker.pingMs,
    )
}

private class SignalBarsRenderable(private val color: Int) : GuiRenderable {
    override val width: Int = SIGNAL_ICON_WIDTH
    override val height: Int get() = Minecraft.getInstance().font.lineHeight

    override fun render(context: GuiGraphicsExtractor) {
        val top = (height - SIGNAL_ICON_HEIGHT) / 2
        for (index in 0 until SIGNAL_BAR_COUNT) {
            val barHeight = (index + 1) * 2
            val x = index * 2
            context.fill(x, top + SIGNAL_ICON_HEIGHT - barHeight, x + 1, top + SIGNAL_ICON_HEIGHT, color)
        }
    }
}

internal data class ServerInfoValues(
    val fps: Int,
    val tps: Double?,
    val ping: Int?,
)

internal fun serverInfoText(
    metric: ServerInfoMetric,
    values: ServerInfoValues,
    labelStyle: DisplayLabelStyle,
): String {
    val value = when (metric) {
        ServerInfoMetric.FPS -> values.fps.toString()
        ServerInfoMetric.TPS -> values.tps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "--"
        ServerInfoMetric.PING -> values.ping?.let { "$it ms" } ?: "--"
    }
    val suffix = if (labelStyle == DisplayLabelStyle.TEXT) {
        ""
    } else {
        when (metric) {
            ServerInfoMetric.FPS -> " FPS"
            ServerInfoMetric.TPS -> " TPS"
            ServerInfoMetric.PING -> ""
        }
    }
    return labelStyle.prefix(metric.toString(), metric.symbol) + value + suffix
}

private const val SIGNAL_ICON_WIDTH = 7
private const val SIGNAL_ICON_HEIGHT = 8
private const val SIGNAL_BAR_COUNT = 4
private const val TPS_CONSUMER_ID = "Server Info Display"
