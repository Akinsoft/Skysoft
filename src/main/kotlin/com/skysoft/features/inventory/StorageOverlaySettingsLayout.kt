package com.skysoft.features.inventory

import com.skysoft.config.StorageOverlayConfigBounds
import com.skysoft.config.StorageOverlayMode
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal enum class StorageVisualSetting(val label: String, val isToggle: Boolean = false) {
    MODE("Mode", true),
    COLUMNS("Columns"),
    HEIGHT("Height"),
    SCROLL_SPEED("Scroll Speed"),
    AUTO_OPEN_PREVIOUS("Reopen Previous", true),
    AUTOFOCUS("Autofocus", true),
    SHORTCUT("Shortcut", true),
    DIM_BACKGROUND("Dim Background", true),
    ;

    fun value(): Int = when (this) {
        MODE -> if (config.settings.mode == StorageOverlayMode.MODERN) 1 else 0
        COLUMNS -> config.details.columns
        HEIGHT -> config.details.height
        SCROLL_SPEED -> config.details.scrollSpeed
        AUTO_OPEN_PREVIOUS -> if (config.settings.autoOpenPrevious) 1 else 0
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
        MODE, AUTO_OPEN_PREVIOUS, AUTOFOCUS, SHORTCUT, DIM_BACKGROUND -> 0..1
    }

    fun step(): Int = if (this == HEIGHT) StorageOverlayConfigBounds.HEIGHT_STEP else 1

    fun set(value: Int) {
        when (this) {
            MODE -> {
                config.settings.mode = if (value != 0) StorageOverlayMode.MODERN else StorageOverlayMode.CLASSIC
                resetModernTransientState()
                resetStorageScroll()
            }
            COLUMNS -> config.details.columns = value
            HEIGHT -> config.details.height = value
            SCROLL_SPEED -> config.details.scrollSpeed = value
            AUTO_OPEN_PREVIOUS -> config.settings.autoOpenPrevious = value != 0
            AUTOFOCUS -> config.settings.isAutofocusEnabled = value != 0
            SHORTCUT -> config.settings.miniMenu = value != 0
            DIM_BACKGROUND -> config.details.dimBackground = value != 0
        }
        config.repairLoadedValues()
    }

    fun displayValue(): String = if (isToggle) {
        if (this == MODE) config.settings.mode.toString() else if (value() != 0) "On" else "Off"
    } else {
        value().toString()
    }
}

internal fun visibleStorageSettings(): List<StorageVisualSetting> = StorageVisualSetting.entries.filter { setting ->
    when (setting) {
        StorageVisualSetting.HEIGHT,
        StorageVisualSetting.AUTOFOCUS,
        StorageVisualSetting.SHORTCUT,
        -> !isModernStorageOverlay

        StorageVisualSetting.AUTO_OPEN_PREVIOUS -> isModernStorageOverlay
        else -> true
    }
}

internal data class StorageSettingsPanelLayout(
    val button: Rect,
    val panel: Rect,
    val isBesideInventory: Boolean,
    val settings: List<StorageVisualSetting>,
) {
    val close: Rect
        get() = Rect(
            panel.x + panel.width - StorageSettingsPanel.CLOSE_RIGHT_INSET,
            panel.y + StorageSettingsPanel.HEADER_BUTTON_Y,
            StorageSettingsPanel.CLOSE_SIZE,
            StorageSettingsPanel.HEADER_BUTTON_HEIGHT,
        )

    fun row(setting: StorageVisualSetting): Rect {
        val index = settings.indexOf(setting)
        require(index >= 0) { "Storage setting is not visible: $setting" }
        return Rect(
            panel.x + StorageSettingsPanel.INSET,
            panel.y +
                StorageSettingsPanel.HEADER_HEIGHT +
                StorageSettingsPanel.CONTENT_GAP +
                index * StorageSettingsPanel.ROW_HEIGHT,
            panel.width - StorageSettingsPanel.INSET * 2,
            StorageSettingsPanel.ROW_HEIGHT,
        )
    }

    fun track(setting: StorageVisualSetting): Rect {
        val row = row(setting)
        return Rect(
            row.x + StorageSettingsPanel.LABEL_WIDTH,
            row.y + StorageSettingsPanel.TRACK_Y,
            row.width - StorageSettingsPanel.RESERVED_WIDTH,
            StorageSettingsPanel.TRACK_HEIGHT,
        )
    }

    fun toggle(setting: StorageVisualSetting): Rect {
        val row = row(setting)
        val width = if (setting == StorageVisualSetting.MODE) {
            StorageSettingsPanel.MODE_TOGGLE_WIDTH
        } else {
            StorageSettingsPanel.TOGGLE_WIDTH
        }
        return Rect(
            row.x + row.width - width,
            row.y + (row.height - StorageSettingsPanel.TOGGLE_HEIGHT) / 2,
            width,
            StorageSettingsPanel.TOGGLE_HEIGHT,
        )
    }

    fun settingAt(mouseX: Int, mouseY: Int): StorageVisualSetting? =
        settings.firstOrNull { row(it).contains(mouseX, mouseY) }

    fun animatedPanel(progress: Float): Rect = Rect(
        lerp(button.x, panel.x, progress),
        lerp(button.y, panel.y, progress),
        lerp(button.width, panel.width, progress),
        lerp(button.height, panel.height, progress),
    )

    companion object {
        fun create(
            screenWidth: Int,
            screenHeight: Int,
            playerBounds: Rect,
            settings: List<StorageVisualSetting> = StorageVisualSetting.entries,
            buttonAnchor: Rect = playerBounds,
        ): StorageSettingsPanelLayout {
            val panelHeight = StorageSettingsPanel.height(settings.size)
            val panelWidth = (screenWidth - StoragePanel.EDGE_MARGIN * 2)
                .coerceAtMost(StorageSettingsPanel.WIDTH)
                .coerceAtLeast(1)
            val preferredX = playerBounds.x + playerBounds.width + StorageSelector.SIDE_GAP
            val maximumPanelX = (screenWidth - StoragePanel.EDGE_MARGIN - panelWidth)
                .coerceAtLeast(StoragePanel.EDGE_MARGIN)
            val panelX = preferredX.coerceAtMost(maximumPanelX)
            val maximumPanelY = (screenHeight - StoragePanel.EDGE_MARGIN - panelHeight)
                .coerceAtLeast(StoragePanel.EDGE_MARGIN)
            val panelY = (playerBounds.y + playerBounds.height - panelHeight)
                .coerceIn(StoragePanel.EDGE_MARGIN, maximumPanelY)
            val preferredButtonX = (buttonAnchor.x + buttonAnchor.width + StorageSelector.SIDE_GAP).coerceAtMost(
                (screenWidth - StoragePanel.EDGE_MARGIN - StorageSettingsPanel.BUTTON_SIZE)
                    .coerceAtLeast(StoragePanel.EDGE_MARGIN),
            )
            val buttonY = (buttonAnchor.y + buttonAnchor.height - StorageSettingsPanel.BUTTON_SIZE)
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
                panel = Rect(panelX, panelY, panelWidth, panelHeight),
                isBesideInventory = panelX == preferredX,
                settings = settings,
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
    const val WIDTH = 224
    const val INSET = 9
    const val HEADER_HEIGHT = 24
    const val HEADER_BUTTON_Y = 5
    const val HEADER_BUTTON_HEIGHT = 14
    const val CLOSE_RIGHT_INSET = 18
    const val CLOSE_SIZE = 14
    const val CONTENT_GAP = 4
    const val ROW_HEIGHT = 22
    const val TEXT_Y = 6
    const val TRACK_Y = 9
    const val TRACK_HEIGHT = 3
    const val LABEL_WIDTH = 88
    const val RESERVED_WIDTH = 124
    const val TOGGLE_WIDTH = 44
    const val MODE_TOGGLE_WIDTH = 48
    const val TOGGLE_HEIGHT = 16
    const val BOTTOM_INSET = 8
    fun height(settingCount: Int): Int = HEADER_HEIGHT + CONTENT_GAP + settingCount * ROW_HEIGHT + BOTTOM_INSET
}
