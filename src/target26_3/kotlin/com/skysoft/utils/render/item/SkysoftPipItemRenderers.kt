// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.item

import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry

object SkysoftPipItemRenderers {
    fun register() {
        PictureInPictureRendererRegistry.register { RotatingItemPictureRenderer() }
    }
}
