package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.utils.WorldVec

internal fun closestPendingRareMobSignal(
    signals: Collection<DianaRareMobSignal>,
    mob: DianaRareMobOption,
    playerLocation: WorldVec?,
): DianaRareMobSignal? {
    playerLocation ?: return null
    return signals
        .asSequence()
        .filter { signal -> signal.mob == mob }
        .filter { signal -> signal.location.distance(playerLocation) <= LOCAL_SPAWN_LINK_DISTANCE }
        .minByOrNull { signal -> signal.location.distanceSq(playerLocation) }
}

private const val LOCAL_SPAWN_LINK_DISTANCE = 35.0
