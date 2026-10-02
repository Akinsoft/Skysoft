package com.skysoft.utils

import com.skysoft.SkysoftMod
import com.mojang.blaze3d.Blaze3D

internal object BrowserUtilities {
    fun tryOpen(url: String): Boolean =
        try {
            Blaze3D.openUri(java.net.URI.create(url))
            true
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to open browser for {}", url, e)
            false
        }
}
