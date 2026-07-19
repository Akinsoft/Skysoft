package com.skysoft.features.inventory

import com.skysoft.config.StorageOverlayConfigBounds
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal enum class StorageVisualSetting(val label: String, val isToggle: Boolean = false) {
    COLUMNS("Columns"),
    HEIGHT("Height"),
    SCROLL_SPEED("Scroll Speed"),
    AUTOFOCUS("Autofocus", true),
    SHORTCUT("Shortcut", true),
    DIM_BACKGROUND("Dim Background", true),
    ;

    fun value(): Int = when (this) {
        COLUMNS -> config.details.columns
        HEIGHT -> config.details.height
        SCROLL_SPEED -> config.details.scrollSpeed
        AUTOFOCUS -> if (config.settings.isAutofocusEnabled) 1 else 0
        SHORTCUT -> if (config.settings.miniMenu) 1 else 0
        DIM_BACKGROUND -> if (config.details.dimBackground) 1 else 0
    }

    fun range(screenWidth: Int, screenHeight: Int, measurements: Measurements): IntRange = when (this) {
        COLUMNS -> StorageOverlayConfigBounds.MIN_COLUMNS..maximumStorageColumns(screenWidth)
        HEIGHT -> {
            val isSelectorStacked = measurements.isSelectorVisible &&
                StorageSelectorLayout.sideX(measurements.playerBounds.x) == null
            val stackedHeight = if (isSelectorStacked) StorageSelector.HEIGHT + StorageSelector.STACKED_GAP else 0
            StorageOverlayConfigBounds.MIN_HEIGHT..maximumStorageHeight(screenHeight, stackedHeight)
        }
        SCROLL_SPEED -> StorageOverlayConfigBounds.MIN_SCROLL_SPEED..StorageOverlayConfigBounds.MAX_SCROLL_SPEED
        AUTOFOCUS, SHORTCUT, DIM_BACKGROUND -> 0..1
    }

    fun step(): Int = if (this == HEIGHT) StorageOverlayConfigBounds.HEIGHT_STEP else 1

    fun set(value: Int) {
        when (this) {
            COLUMNS -> config.details.columns = value
            HEIGHT -> config.details.height = value
            SCROLL_SPEED -> config.details.scrollSpeed = value
            AUTOFOCUS -> config.settings.isAutofocusEnabled = value != 0
            SHORTCUT -> config.settings.miniMenu = value != 0
            DIM_BACKGROUND -> config.details.dimBackground = value != 0
        }
        config.repairLoadedValues()
    }

    fun displayValue(): String = if (isToggle) {
        if (value() != 0) "On" else "Off"
    } else {
        value().toString()
    }
}

internal data class StorageSettingsPanelLayout(
    val button: Rect,
    val panel: Rect,
    val isBesideInventory: Boolean,
) {
    val close = button

    fun row(setting: StorageVisualSetting): Rect = Rect(
        panel.x + StorageSettingsPanel.CONTROL_INSET,
        panel.y + StorageSettingsPanel.CONTROL_TOP + setting.ordinal * StorageSettingsPanel.CONTROL_STEP,
        panel.width - StorageSettingsPanel.CONTROL_INSET * 2,
        StorageSettingsPanel.CONTROL_HEIGHT,
    )

    fun track(setting: StorageVisualSetting): Rect {
        val row = row(setting)
        return Rect(
            row.x,
            row.y + StorageSettingsPanel.TRACK_TOP,
            row.width,
            StorageSettingsPanel.TRACK_HEIGHT,
        )
    }

    fun toggle(setting: StorageVisualSetting): Rect {
        val row = row(setting)
        return Rect(
            row.x + row.width - StorageSettingsPanel.TOGGLE_WIDTH,
            row.y,
            StorageSettingsPanel.TOGGLE_WIDTH,
            StorageSettingsPanel.TOGGLE_HEIGHT,
        )
    }

    fun settingAt(mouseX: Int, mouseY: Int): StorageVisualSetting? =
        StorageVisualSetting.entries.firstOrNull { row(it).contains(mouseX, mouseY) }

    fun animatedPanel(progress: Float): Rect = Rect(
        lerp(button.x, panel.x, progress),
        lerp(button.y, panel.y, progress),
        lerp(button.width, panel.width, progress),
        lerp(button.height, panel.height, progress),
    )

    companion object {
        fun create(screenWidth: Int, screenHeight: Int, playerBounds: Rect): StorageSettingsPanelLayout {
            val preferredX = playerBounds.x + playerBounds.width + StorageSelector.SIDE_GAP
            val maximumPanelX = (screenWidth - StoragePanel.EDGE_MARGIN - StorageSelector.WIDTH)
                .coerceAtLeast(StoragePanel.EDGE_MARGIN)
            val panelX = preferredX.coerceAtMost(maximumPanelX)
            val maximumPanelY = (screenHeight - StoragePanel.EDGE_MARGIN - StorageSelector.HEIGHT)
                .coerceAtLeast(StoragePanel.EDGE_MARGIN)
            val panelY = (playerBounds.y + playerBounds.height - StorageSelector.HEIGHT)
                .coerceIn(StoragePanel.EDGE_MARGIN, maximumPanelY)
            val preferredButtonX = preferredX.coerceAtMost(
                (screenWidth - StoragePanel.EDGE_MARGIN - StorageSettingsPanel.BUTTON_SIZE)
                    .coerceAtLeast(StoragePanel.EDGE_MARGIN),
            )
            val buttonY = (playerBounds.y + playerBounds.height - StorageSettingsPanel.BUTTON_SIZE)
                .coerceIn(
                    StoragePanel.EDGE_MARGIN,
                    (screenHeight - StoragePanel.EDGE_MARGIN - StorageSettingsPanel.BUTTON_SIZE)
                        .coerceAtLeast(StoragePanel.EDGE_MARGIN),
                )
            return StorageSettingsPanelLayout(
                button = Rect(
                    preferredButtonX,
                    buttonY,
                    StorageSettingsPanel.BUTTON_SIZE,
                    StorageSettingsPanel.BUTTON_SIZE,
                ),
                panel = Rect(panelX, panelY, StorageSelector.WIDTH, StorageSelector.HEIGHT),
                isBesideInventory = panelX == preferredX,
            )
        }
    }
}

internal fun storageSettingValueAt(pointerX: Int, track: Rect, range: IntRange, step: Int): Int {
    if (range.first >= range.last) return range.first
    val progress = ((pointerX - track.x).toDouble() / track.width.coerceAtLeast(1)).coerceIn(0.0, 1.0)
    val raw = range.first + (range.last - range.first) * progress
    return (raw / step).roundToInt().times(step).coerceIn(range)
}

internal fun maximumStorageColumns(screenWidth: Int): Int =
    ((screenWidth - StoragePanel.PADDING * 2) / (StoragePages.WIDTH + StoragePages.PADDING))
        .coerceIn(StorageOverlayConfigBounds.MIN_COLUMNS, StorageOverlayConfigBounds.MAX_COLUMNS)

internal fun maximumStorageHeight(screenHeight: Int, stackedSelectorHeight: Int): Int =
    (
        screenHeight -
            StoragePlayerInventory.HEIGHT -
            StorageSearch.HEIGHT -
            StoragePanel.VERTICAL_RESERVED_SPACE -
            stackedSelectorHeight
        ).coerceIn(StorageOverlayConfigBounds.MIN_HEIGHT, StorageOverlayConfigBounds.MAX_HEIGHT)
        .let { maximum -> maximum - maximum % StorageOverlayConfigBounds.HEIGHT_STEP }
        .coerceAtLeast(StorageOverlayConfigBounds.MIN_HEIGHT)

private fun lerp(start: Int, end: Int, progress: Float): Int =
    (start + (end - start) * progress.coerceIn(0f, 1f)).roundToInt()

internal object StorageSettingsPanel {
    const val BUTTON_SIZE = StorageSlots.SIZE
    const val CONTROL_INSET = 5
    const val CONTROL_TOP = 4
    const val CONTROL_STEP = 15
    const val CONTROL_HEIGHT = 14
    const val TRACK_TOP = 10
    const val TRACK_HEIGHT = 3
    const val TOGGLE_WIDTH = 30
    const val TOGGLE_HEIGHT = 12
}
