package com.skysoft.features.misc

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
    private val config get() = SkysoftConfigGui.config().gui.serverInfoDisplay

    fun register() {
        ServerTpsProvider.registerConsumer(TPS_CONSUMER_ID)
        SkysoftClientEvents.onEndTick("Server Info metrics") { minecraft -> updateMetrics(minecraft) }
        SkysoftClientEvents.onJoin("Server Info join reset", ::resetMeasurements)
        SkysoftClientEvents.onDisconnect("Server Info disconnect reset", ::resetMeasurements)
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "server_info_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = { canRenderLive() },
                render = { context, _ -> renderHud(context) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "server_info_display"
            override val label: String = "Server Info Display"
            override val position get() = config.position
            override val hasEditorBackground: Boolean get() = !config.details.background
            override fun width(): Int = currentRenderable()?.width ?: 0
            override fun height(): Int = currentRenderable()?.height ?: 0
            override fun isVisible(): Boolean = config.enabled && configuredMetrics().isNotEmpty()
            override fun renderDummy(context: GuiGraphicsExtractor) {
                currentRenderable()?.render(context)
            }
            override fun openConfig() = SkysoftConfigGui.open("Server Info Display")
        })
    }

    internal fun recordPong(requestId: Long, timestampNanos: Long): PingSampleResult {
        if (!isPingTrackingActive) return PingSampleResult.IGNORED_INACTIVE
        return pingTracker.recordPong(requestId, timestampNanos)
    }

    internal fun diagnosticSnapshot(): ServerInfoDiagnosticSnapshot {
        val minecraft = Minecraft.getInstance()
        val renderable = currentRenderable()
        val values = currentValues(minecraft)
        return ServerInfoDiagnosticSnapshot(
            enabled = config.enabled,
            metrics = configuredMetrics(),
            background = config.details.background,
            positionX = config.position.x,
            positionY = config.position.y,
            positionScale = config.position.scale,
            connectionLoaded = minecraft.connection != null,
            levelLoaded = minecraft.level != null,
            playerLoaded = minecraft.player != null,
            isLocalServer = minecraft.isLocalServer,
            isRemoteServer = isRemoteServer(minecraft),
            isGuiHidden = MinecraftClient.isGuiHidden(minecraft),
            canRenderLive = canRenderLive(minecraft),
            values = values,
            renderedLines = serverInfoLines(configuredMetrics(), values),
            renderedWidth = renderable?.width,
            renderedHeight = renderable?.height,
            tps = ServerTpsProvider.snapshot(),
            isPingTrackingActive = isPingTrackingActive,
            ping = pingTracker.snapshot(),
        )
    }

    private fun renderHud(context: GuiGraphicsExtractor) {
        if (!canRenderLive()) return
        val renderable = currentRenderable() ?: return
        config.position.renderRenderable(context, renderable)
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
    }

    private fun currentRenderable(): GuiRenderable? {
        val metrics = configuredMetrics()
        if (metrics.isEmpty()) return null
        val color = config.details.color.get().toColor().rgb
        return verticalLayout(serverInfoLines(metrics, currentValues()).map { StringRenderable(it, color = color) })
            .withOverlayPanel(config.details.background)
    }

    private fun currentValues(minecraft: Minecraft = Minecraft.getInstance()): ServerInfoValues = ServerInfoValues(
        fps = minecraft.fps,
        tps = ServerTpsProvider.tps,
        ping = pingTracker.pingMs,
    )
}

internal data class ServerInfoValues(
    val fps: Int,
    val tps: Double?,
    val ping: Int?,
)

internal fun serverInfoLines(metrics: List<ServerInfoMetric>, values: ServerInfoValues): List<String> =
    metrics.map { metric ->
        when (metric) {
            ServerInfoMetric.FPS -> "FPS: ${values.fps}"
            ServerInfoMetric.TPS -> "TPS: ${values.tps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "--"}"
            ServerInfoMetric.PING -> "Ping: ${values.ping?.let { "$it ms" } ?: "--"}"
        }
    }

internal data class ServerInfoDiagnosticSnapshot(
    val enabled: Boolean,
    val metrics: List<ServerInfoMetric>,
    val background: Boolean,
    val positionX: Int,
    val positionY: Int,
    val positionScale: Float,
    val connectionLoaded: Boolean,
    val levelLoaded: Boolean,
    val playerLoaded: Boolean,
    val isLocalServer: Boolean,
    val isRemoteServer: Boolean,
    val isGuiHidden: Boolean,
    val canRenderLive: Boolean,
    val values: ServerInfoValues,
    val renderedLines: List<String>,
    val renderedWidth: Int?,
    val renderedHeight: Int?,
    val tps: ServerTpsProviderSnapshot,
    val isPingTrackingActive: Boolean,
    val ping: ServerPingSnapshot,
)

private const val TPS_CONSUMER_ID = "Server Info Display"
