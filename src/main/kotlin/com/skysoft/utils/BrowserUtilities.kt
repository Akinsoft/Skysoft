package com.skysoft.utils

import com.skysoft.SkysoftMod
import net.minecraft.util.Util

internal object BrowserUtilities {
    fun tryOpen(url: String): Boolean =
        try {
            Util.getPlatform().openUri(url)
            true
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to open browser for {}", url, e)
            false
        }
}
