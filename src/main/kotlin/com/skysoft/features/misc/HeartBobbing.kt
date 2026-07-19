package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary

object HeartBobbing {
    fun resolveRegenerationOffset(vanillaValue: Int): Int =
        SkysoftErrorBoundary.value("Heart bobbing", vanillaValue) {
            if (SkysoftConfigGui.config().gui.isHeartBobbingDisabled) NO_HEART_OFFSET else vanillaValue
        }

    fun resolveLowHealthOffset(vanillaValue: Int): Int =
        SkysoftErrorBoundary.value("Heart bobbing", vanillaValue) {
            if (SkysoftConfigGui.config().gui.isHeartBobbingDisabled) 0 else vanillaValue
        }

    private const val NO_HEART_OFFSET = -1
}
