package com.skysoft.utils

import com.skysoft.SkysoftMod

internal object BrowserUtilities {
    fun tryOpen(url: String): Boolean =
        try {
            MinecraftClient.openUri(url)
            true
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to open browser for {}", url, e)
            false
        }
}
