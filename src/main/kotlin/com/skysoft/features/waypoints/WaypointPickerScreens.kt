package com.skysoft.features.waypoints

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal class WaypointIslandScreen private constructor(
    parent: Screen?,
    private val selected: SkyBlockIsland?,
    private val onSelect: (SkyBlockIsland) -> Unit,
) : WaypointDialog("Choose Island", parent, primaryLabel = null) {
    private val search = form.field("").also { it.focused = true }
    private var lastSearch = ""
    private val choices: List<SkyBlockIsland> get() = SkyBlockIsland.entries.filter {
        it.displayName.contains(search.text, ignoreCase = true)
    }
    override val contentHeight: Int get() = BUTTON_ROW + choices.size * BUTTON_ROW

    override fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        if (lastSearch != search.text) {
            lastSearch = search.text
            scroll = 0
        }
        form.draw(context, opacity, search, "Search islands", Rect(x, y, width, FIELD_HEIGHT), showLabel = false)
        choices.forEachIndexed { index, island ->
            val rowY = y + BUTTON_ROW + index * BUTTON_ROW
            if (isBodyRowVisible(rowY, FIELD_HEIGHT)) {
                painter.textControl(Rect(x, rowY, width, FIELD_HEIGHT), island.displayName, selected = island == selected) {
                    onSelect(island)
                    onClose()
                }
            }
        }
    }

    override fun confirm() = Unit

    companion object {
        fun open(selected: SkyBlockIsland?, onSelect: (SkyBlockIsland) -> Unit) {
            MinecraftClient.setScreen(WaypointIslandScreen(MinecraftClient.screen(), selected, onSelect))
        }
    }
}

internal object WaypointPresetList {
    private val search = TextFieldState(maxLength = MAX_WAYPOINT_NAME)
    private var lastSearch = ""
    private var allIslands = false
    private var searchScreen: Screen? = null
    private val island get() = Waypoints.browseIsland ?: Waypoints.island
    val groups: List<WaypointGroup> get() = WaypointLibrary.groups.filter { group ->
        (allIslands || island == null || group.island == island) &&
            (group.name.contains(search.text, ignoreCase = true) || group.island.displayName.contains(search.text, ignoreCase = true))
    }
    private val canSearch: Boolean get() = WaypointPanel.isBrowsingPresets && WaypointPanel.isVisible() &&
        WaypointPanel.isInteractive && WaypointPanel.layout != null && !WaypointSettingsPanel.isOpen &&
        MinecraftClient.screen() != null && MinecraftClient.screen() !is WaypointDialog
    private val isSearchActive: Boolean get() = canSearch && search.focused && searchScreen === MinecraftClient.screen()

    fun reset() {
        blur()
        search.text = ""
        lastSearch = ""
        allIslands = false
    }

    fun blur() {
        search.focused = false
        searchScreen = null
    }

    fun tick() {
        if (!isSearchActive) blur()
    }

    fun didClick(click: MouseButtonEvent, x: Int, y: Int): Boolean {
        val bounds = WaypointPanel.layout?.search
        if (!canSearch || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || bounds?.contains(x, y) != true) {
            blur()
            return false
        }
        searchScreen = MinecraftClient.screen()
        search.focused = true
        search.placeCursorAt(x, bounds.x, bounds.width)
        return true
    }

    fun didPressKey(event: KeyEvent): Boolean {
        if (!isSearchActive) return false
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) blur() else search.keyPressed(event)
        return true
    }

    fun didType(event: CharacterEvent): Boolean {
        if (!isSearchActive) return false
        if (event.isAllowedChatCharacter) search.charTyped(event)
        return true
    }

    fun render(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        if (lastSearch != search.text) {
            lastSearch = search.text
            WaypointPanel.scrollOffset = 0
        }
        renderFilter(painter, layout.row(layout.bounds.y + WaypointPanelLayout.HEADER))
        val field = layout.search
        search.render(context, field.x, field.y, field.width, field.height, "", alpha = WaypointPanel.opacity)
        val results = groups
        if (results.isEmpty()) painter.info(layout.row(layout.list.y), WaypointLibrary.loadError ?: EMPTY_MESSAGE)
        results.drop(WaypointPanel.scrollOffset).take(layout.visibleRows).forEachIndexed { index, group ->
            val row = layout.row(layout.list.y + index * WaypointPanelLayout.ROW_HEIGHT)
            painter.textControl(
                row.copy(width = row.width - OPEN_WIDTH - VISIBILITY_WIDTH), group.name,
                selected = group.id == Waypoints.selection.groupId, leftAligned = true, tooltip = waypointPresetTooltip(group),
            ) { Waypoints.select(group) }
            painter.cycle(
                Rect(row.x + row.width - OPEN_WIDTH - VISIBILITY_WIDTH, row.y, VISIBILITY_WIDTH, row.height),
                "Visibility", listOf(false, true), group.enabled, label = { if (it) "On" else "Off" },
                color = if (group.enabled) ENABLED_COLOR else PixelControlColors.MUTED_TEXT,
            ) { Waypoints.updateGroup(group.copy(enabled = it)) }
            painter.action(Rect(row.x + row.width - OPEN_WIDTH, row.y, OPEN_WIDTH, row.height), "Open") {
                Waypoints.select(group)
                WaypointPanel.showPage(presets = false)
            }
        }
        if (layout.showFooter) renderFooter(painter, layout.row(layout.actionsTop))
    }

    private fun renderFilter(painter: WaypointPanelPainter, row: Rect) {
        val heading = "Search presets on "
        val headingWidth = Minecraft.getInstance().font.width(heading)
        painter.text(row.copy(width = headingWidth), heading)
        val filter = Rect(row.x + headingWidth, row.y, row.width - headingWidth, row.height)
        val island = island
        if (island == null) {
            painter.action(filter, "Choose island") {
                WaypointIslandScreen.open(null) {
                    Waypoints.browseIsland = it
                    allIslands = false
                    WaypointPanel.scrollOffset = 0
                }
            }
        } else {
            painter.cycle(
                filter, "Islands", listOf(false, true), allIslands,
                label = { if (it) "All Islands" else island.displayName }, leftAligned = true,
            ) {
                allIslands = it
                WaypointPanel.scrollOffset = 0
            }
        }
    }

    private fun renderFooter(painter: WaypointPanelPainter, bounds: Rect) {
        val settingsLabel = if (Waypoints.config.enabled) "Settings" else "Enable"
        val cells = WaypointPanelLayout.columns(bounds, "New preset", "Import", "Share", settingsLabel)
        val canEdit = WaypointLibrary.loadError == null
        painter.action(cells[0], "New preset", enabled = canEdit && island != null) { WaypointGroupScreen.create() }
        painter.action(cells[1], "Import", enabled = canEdit) { WaypointImportScreen.open() }
        painter.action(cells[SHARE_COLUMN], "Share", enabled = WaypointLibrary.groups.isNotEmpty()) { WaypointShareScreen.open() }
        painter.action(cells.last(), settingsLabel, color = 0xFF55FFFF.toInt()) {
            if (Waypoints.config.enabled) WaypointSettingsPanel.toggle() else WaypointPanel.enable()
        }
    }

    private const val OPEN_WIDTH = 42
    private const val VISIBILITY_WIDTH = 36
    private const val SHARE_COLUMN = 2
    private const val ENABLED_COLOR = 0xFF55FF55.toInt()
    private const val EMPTY_MESSAGE = "No presets found. Create one below or import a route."
}

internal fun waypointPresetTooltip(group: WaypointGroup, islandKnown: Boolean = true): List<String> = buildList {
    add("§e${group.name}")
    add(if (islandKnown) "§7Island: §f${group.island.displayName}" else "§eChoose an island")
    add("§7Points: §f${group.points.size}")
    add("§7Scope: §f${group.scope.label}")
    if (group.scope == WaypointScope.PROFILE) add("§7Profile: §f${group.scopeLabel}")
    add(if (group.route) "§7Ordered route" else "§7Fixed markers")
}
