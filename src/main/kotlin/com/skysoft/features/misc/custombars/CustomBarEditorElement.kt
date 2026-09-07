package com.skysoft.features.misc.custombars

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.misc.custombars.CustomBarPart.Companion.MIN_RESOURCE_HEIGHT
import com.skysoft.features.misc.custombars.CustomBarPart.Companion.MIN_RESOURCE_WIDTH
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorSnapshot
import com.skysoft.gui.hudEditorSnapshot
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class CustomBarEditorElement(private val part: CustomBarPart) : HudEditorElement {
    override val id: String = "custom_bars_${part.name.lowercase()}"
    override val label: String = part.label
    override val position get() = part.position()
    override val snapGroup: String = id
    override val layoutOffsetX: Int get() = part.layoutOffsetX()
    override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
    override val hasEditorBackground: Boolean = false
    override val canScale: Boolean get() = !part.usesVanillaDisplay() && part.dimensions == null
    override val canResizeWidth: Boolean get() = part.editorDimensions() != null
    override val canResizeHeight: Boolean get() = part.editorDimensions() != null
    override fun width(): Int = part.width
    override fun height(): Int = part.height
    override fun isVisible(): Boolean = config.enabled && part.isEditorVisible()
    override fun renderEditor(context: GuiGraphicsExtractor) {
        if (part.usesVanillaDisplay()) {
            part.vanillaDisplay().renderPreview(context, !config.settings.numbers.experience)
        } else {
            CustomBarRenderable.create(part, previewAir = part == CustomBarPart.AIR).render(context)
        }
    }
    override fun resizeEditor(width: Int, height: Int) = part.resize(width, height)
    override fun minEditorWidth(): Int = MIN_RESOURCE_WIDTH
    override fun minEditorHeight(): Int = MIN_RESOURCE_HEIGHT
    override fun resetEditorState() {
        super.resetEditorState()
        part.editorDimensions()?.resetToDefault()
    }
    override fun captureEditorState(): HudEditorSnapshot {
        val position = part.position()
        val positionSnapshot = position.snapshot()
        val dimensions = part.editorDimensions()
        val dimensionsSnapshot = dimensions?.snapshot()
        return hudEditorSnapshot(positionSnapshot to dimensionsSnapshot) {
            position.restore(positionSnapshot)
            if (dimensions != null && dimensionsSnapshot != null) dimensions.restore(dimensionsSnapshot)
        }
    }
    override fun openConfig() = SkysoftConfigGui.open("Custom Bars")
}

private val config get() = SkysoftConfigGui.config().gui.customBars
