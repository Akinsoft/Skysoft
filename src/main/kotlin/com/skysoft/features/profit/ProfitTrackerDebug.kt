package com.skysoft.features.profit

import com.skysoft.SkysoftMod
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.utils.MinecraftClient

object ProfitTrackerDebug {
    internal fun record(
        batch: SkyBlockItemChangeBatch,
        unsuppressedChanges: Map<String, Int>,
        preset: ProfitTrackerPreset?,
        appliedChanges: Map<String, Int>,
    ) {
        SkysoftMod.LOGGER.info(
            "[Profit Tracker] source={} window={} screen={} raw={} gross={} unsuppressed={} preset={} applied={}",
            batch.source,
            batch.sackWindowSeconds,
            MinecraftClient.screen()?.title?.string,
            batch.changes,
            batch.grossGains,
            unsuppressedChanges,
            preset?.name,
            appliedChanges,
        )
    }
}
