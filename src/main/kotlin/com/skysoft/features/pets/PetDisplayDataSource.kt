package com.skysoft.features.pets

import com.skysoft.data.SkyBlockIsland

internal enum class PetDisplayDataSource(
    val canRefreshDisplay: Boolean,
    val requiresStableTabData: Boolean,
    val shouldWarnAboutMissingWidget: Boolean,
) {
    PET_WIDGET(
        canRefreshDisplay = true,
        requiresStableTabData = true,
        shouldWarnAboutMissingWidget = false,
    ),
    TRACKED_DUNGEON_PET(
        canRefreshDisplay = true,
        requiresStableTabData = false,
        shouldWarnAboutMissingWidget = false,
    ),
    CACHED(
        canRefreshDisplay = false,
        requiresStableTabData = true,
        shouldWarnAboutMissingWidget = true,
    ),
}

internal fun petDisplayDataSource(
    isPetWidgetReady: Boolean,
    currentIsland: SkyBlockIsland?,
): PetDisplayDataSource = when {
    isPetWidgetReady -> PetDisplayDataSource.PET_WIDGET
    currentIsland == SkyBlockIsland.DUNGEONS -> PetDisplayDataSource.TRACKED_DUNGEON_PET
    else -> PetDisplayDataSource.CACHED
}
