package com.skysoft.features.profit

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.utils.TextUtilities.removeColor
import io.github.notenoughupdates.moulconfig.common.IFontRenderer
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.platform.MoulConfigItemStack

internal class TrackerItemIconRenderContext(
    private val delegate: RenderContext,
    private val itemIdsByLabel: Map<String, String>,
) : RenderContext by delegate {
    override fun drawStringScaledMaxWidth(
        text: StructuredText,
        fontRenderer: IFontRenderer,
        x: Int,
        y: Int,
        shadow: Boolean,
        width: Int,
        color: Int,
    ) {
        delegate.drawStringScaledMaxWidth(text, fontRenderer, x, y, shadow, width, color)
        val itemId = itemIdsByLabel[text.text.removeColor()] ?: return
        val stack = SkyBlockDataRepository.displayStack(SkyBlockDataRepository.itemKey(itemId)) ?: return
        val textScale = (width.toFloat() / fontRenderer.getStringWidth(text)).coerceIn(MIN_TEXT_SCALE, 1f)
        delegate.pushMatrix()
        delegate.translate(x.toFloat(), y.toFloat())
        delegate.scale(textScale * ICON_SCALE, textScale * ICON_SCALE)
        delegate.renderItemStack(MoulConfigItemStack(stack), 0, 0, StructuredText.empty())
        delegate.popMatrix()
    }

    private companion object {
        const val ICON_SCALE = 0.5f
        const val MIN_TEXT_SCALE = 0.1f
    }
}
