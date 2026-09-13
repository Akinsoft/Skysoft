package com.skysoft.features.waypoints

import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.WorldVec
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.toWorldVec
import kotlin.math.ceil
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import org.joml.Matrix4f
import org.joml.Vector4f

internal object WaypointWorldSelection {
    private val targets = mutableListOf<Target>()
    private val projection = Matrix4f()
    private var camera = WorldVec(0.0, 0.0, 0.0)
    private var width = 0
    private var height = 0
    private var capturedLevel: ClientLevel? = null
    private var verticalProjection = 0f
    private var hovered: WaypointSelection? = null

    fun beginFrame(context: SkysoftRenderContext) {
        targets.clear()
        if (!WaypointPanel.canSelectWorld) {
            hovered = null
            capturedLevel = null
            return
        }
        capturedLevel = Minecraft.getInstance().level
        camera = context.camera.position().toWorldVec()
        projection.set(context.cameraRenderState.projectionMatrix).mul(context.cameraRenderState.viewRotationMatrix)
        verticalProjection = context.cameraRenderState.projectionMatrix.m11()
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val scale = GuiScaleController.resolve(MinecraftClient.screen(minecraft), window).normal()
        width = ceil(window.screenWidth / scale.toDouble()).toInt()
        height = ceil(window.screenHeight / scale.toDouble()).toInt()
    }

    fun collect(group: WaypointGroup, point: WaypointPoint, label: WorldVec, text: Component, style: WorldLabelStyle) {
        if (!WaypointPanel.canSelectWorld) return
        val anchor = if (group.pointStyles(point).isEmpty()) null else project(group.pointTarget(point))
        val labelPosition = project(label)
        if (anchor == null && labelPosition == null) return
        val distance = camera.distance(label).coerceAtLeast(MIN_DISTANCE)
        val pixelsPerWorldUnit = height * kotlin.math.abs(verticalProjection) / ((labelPosition?.depth ?: distance) * 2)
        val pixelsPerTextUnit = style.worldScale * style.scaleAt(distance) * pixelsPerWorldUnit
        val halfWidth = (Minecraft.getInstance().font.width(text) * pixelsPerTextUnit / 2).roundToInt().coerceAtLeast(MIN_HIT_RADIUS)
        val halfHeight = (style.lineHeight * pixelsPerTextUnit / 2).roundToInt().coerceAtLeast(MIN_HIT_RADIUS)
        val hit = anchor?.let { Rect(it.x - MIN_HIT_RADIUS, it.y - MIN_HIT_RADIUS, MIN_HIT_RADIUS * 2, MIN_HIT_RADIUS * 2) }
        val labelHit = labelPosition?.takeIf { text.string.isNotBlank() }?.let {
            Rect(it.x - halfWidth, it.y - halfHeight, halfWidth * 2, halfHeight * 2)
        }
        targets += Target(group.id, point.id, hit, labelHit, distance)
    }

    fun containsPoint(x: Int, y: Int): Boolean = targetAt(x, y) != null

    fun didSelectAt(x: Int, y: Int): Boolean {
        val target = targetAt(x, y) ?: return false
        val group = WaypointLibrary.groups.firstOrNull {
            it.id == target.groupId && it.enabled && Waypoints.isInContext(it)
        } ?: return false
        val point = group.points.firstOrNull { it.id == target.pointId && it.enabled } ?: return false
        Waypoints.select(group, point)
        return true
    }

    fun finishFrame() {
        hovered = null
        if (!WaypointPanel.canSelectWorld || MinecraftClient.screen() == null) return
        val mouse = InputUtilities.scaledMousePosition(Minecraft.getInstance())
        val (x, y) = OverlayControlMouse.normalPoint(mouse.x, mouse.y)
        val (screenX, screenY) = OverlayControlMouse.screenPoint(mouse.x, mouse.y)
        if (WaypointPanel.containsPoint(x, y) || WaypointPanelInput.isWorldPointCovered(screenX, screenY)) return
        hovered = targetAt(x, y)?.let { WaypointSelection(it.groupId, it.pointId) }
    }

    fun isHovered(group: WaypointGroup, point: WaypointPoint): Boolean =
        WaypointPanel.canSelectWorld && hovered?.groupId == group.id && hovered?.pointId == point.id

    private fun targetAt(x: Int, y: Int): Target? {
        if (!WaypointPanel.canSelectWorld || capturedLevel !== Minecraft.getInstance().level) return null
        return targets.asSequence().filter { it.bounds?.contains(x, y) == true || it.labelBounds?.contains(x, y) == true }
            .minByOrNull { it.distance }
    }

    private fun project(position: WorldVec): Projected? {
        val offset = position - camera
        val clip = projection.transform(Vector4f(offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat(), 1f))
        if (clip.w <= 0f) return null
        val x = clip.x / clip.w
        val y = clip.y / clip.w
        if (x !in -1f..1f || y !in -1f..1f) return null
        return Projected(((x + 1) * width / 2).roundToInt(), ((1 - y) * height / 2).roundToInt(), clip.w.toDouble())
    }

    private data class Target(
        val groupId: String,
        val pointId: String,
        val bounds: Rect?,
        val labelBounds: Rect?,
        val distance: Double,
    )

    private data class Projected(val x: Int, val y: Int, val depth: Double)

    private const val MIN_HIT_RADIUS = 7
    private const val MIN_DISTANCE = 0.001
}
