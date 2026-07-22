// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.item

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.mixin.PictureInPictureRendererAccessor
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withAlpha
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import kotlin.math.roundToInt

internal object ItemPictureBlitter {
    fun queue(
        renderer: PictureInPictureRenderer<*>,
        picture: RotatingItemPicture,
        gui: GuiRenderState,
    ) {
        val texture = (renderer as PictureInPictureRendererAccessor).skysoftGetTextureView()
        val sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
        gui.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(texture, sampler),
                picture.pose(),
                picture.x0(),
                picture.y0(),
                picture.x1(),
                picture.y1(),
                0f,
                1f,
                1f,
                0f,
                opacityColor(picture.opacity),
                picture.scissorArea(),
                null,
            ),
        )
    }

    private fun opacityColor(opacity: Float): Int {
        val alpha = (opacity.coerceIn(0f, 1f) * COLOR_CHANNEL_MAX).roundToInt()
        return RGB_MASK.withAlpha(alpha)
    }
}
