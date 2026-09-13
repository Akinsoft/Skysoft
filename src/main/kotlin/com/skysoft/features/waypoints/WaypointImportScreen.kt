package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

internal class WaypointImportScreen private constructor(parent: Screen?) : WaypointDialog("Import Waypoints", parent, "Import selected") {
    private var preview: WaypointImport? = null
    private val selected = linkedSetOf<String>()
    private var status = ""
    override val preferredWidth = 300
    override val contentHeight: Int
        get() {
            val data = preview
            return if (data == null) {
                BUTTON_ROW + intro.sumOf { waypointNoteHeight(it, bodyWidth) + GAP } +
                    waypointNoteHeight(if (busy) "Reading waypoints…" else status, bodyWidth)
            } else PREVIEW_TOP + data.groups.size * GROUP_HEIGHT + data.warnings.sumOf { waypointNoteHeight(it, bodyWidth) + GAP }
        }

    override fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        val half = (width - GAP) / 2
        painter.action(Rect(x, y, half, FIELD_HEIGHT), "Paste clipboard", enabled = !busy) {
            val text = Minecraft.getInstance().keyboardHandler.clipboard
            load { text }
        }
        painter.action(Rect(x + half + GAP, y, half, FIELD_HEIGHT), "Open file", enabled = !busy) { load(WaypointTransferFiles::read) }
        val data = preview
        if (data == null) {
            var noteY = y + BUTTON_ROW
            intro.forEach { text ->
                painter.note(text, x, noteY, width)
                noteY += waypointNoteHeight(text, width) + GAP
            }
            painter.note(if (busy) "Reading waypoints…" else status, x, noteY, width)
            return
        }
        painter.info(Rect(x, y + BUTTON_ROW, width, TEXT_HEIGHT), status)
        val selectY = y + BUTTON_ROW + TEXT_HEIGHT + GAP
        painter.action(Rect(x, selectY, half, FIELD_HEIGHT), "Select all", enabled = !busy) { selected.addAll(data.groups.map { it.id }) }
        painter.action(Rect(x + half + GAP, selectY, half, FIELD_HEIGHT), "Select none", enabled = !busy) { selected.clear() }
        data.groups.forEachIndexed { index, group ->
            val rowY = y + PREVIEW_TOP + index * GROUP_HEIGHT
            if (isBodyRowVisible(rowY, GROUP_HEIGHT)) drawGroup(painter, x, rowY, width, group, data)
        }
        var warningY = y + PREVIEW_TOP + data.groups.size * GROUP_HEIGHT
        data.warnings.forEach { warning ->
            val height = waypointNoteHeight(warning, width)
            if (isBodyRowVisible(warningY, height)) painter.note(warning, x, warningY, width)
            warningY += height + GAP
        }
    }

    private fun drawGroup(painter: WaypointPanelPainter, x: Int, y: Int, width: Int, group: WaypointGroup, data: WaypointImport) {
        val checked = group.id in selected
        val missing = group.id in data.needsIsland
        painter.textControl(
            Rect(x, y, width, FIELD_HEIGHT), "${if (checked) "[x]" else "[ ]"} ${group.name}", selected = checked,
            tooltip = waypointPresetTooltip(group, islandKnown = !missing),
        ) {
            if (!selected.remove(group.id)) selected.add(group.id)
        }
        val half = (width - GAP) / 2
        painter.action(Rect(x, y + BUTTON_ROW, half, FIELD_HEIGHT), if (missing) "Choose island…" else group.island.displayName) {
            WaypointIslandScreen.open(group.island.takeUnless { missing }) { island ->
                preview = preview?.let { current ->
                    current.copy(
                        groups = current.groups.map {
                            if (it.id == group.id) it.copy(island = island, scope = Waypoints.defaultScope(island)) else it
                        },
                        needsIsland = current.needsIsland - group.id,
                    )
                }
            }
        }
        painter.cycle(
            Rect(x + half + GAP, y + BUTTON_ROW, half, FIELD_HEIGHT), "Scope", WaypointScope.entries, group.scope, WaypointScope::label,
        ) { scope ->
            preview = data.copy(groups = data.groups.map { if (it.id == group.id) it.copy(scope = scope) else it })
        }
    }

    private fun load(read: () -> String?) {
        check(!busy) { "Wait for the current import to finish." }
        preview = null
        selected.clear()
        transfer({ read()?.let(WaypointImportCodec::decode) }, onFailure = { status = "Import could not be read." }) { result ->
            if (result != null) {
                preview = result.copy(groups = result.groups.map { it.copy(scope = Waypoints.defaultScope(it.island)) })
                selected.addAll(result.groups.map { it.id })
                status = "Imported from ${result.source}"
                scroll = 0
            } else status = "File selection cancelled."
        }
    }

    override fun isPrimaryEnabled(): Boolean = !busy && selected.isNotEmpty() &&
        preview?.needsIsland?.none { it in selected } == true

    override fun confirm() {
        check(!busy) { "Wait for the import preview." }
        val data = preview ?: error("Load a share or file first.")
        require(selected.isNotEmpty()) { "Select at least one preset." }
        require(data.needsIsland.none { it in selected }) { "Choose an island for each selected preset." }
        val groups = data.groups.filter { it.id in selected }.map { Waypoints.scoped(it, it.scope) }
        Waypoints.updateGroups(WaypointLibrary.groups + groups, WaypointSelection(groups.first().id))
        Waypoints.browseIsland = groups.first().island
        WaypointPanel.notify("Imported ${groups.size} presets. Undo is available.")
        onClose()
    }

    companion object {
        fun open() {
            MinecraftClient.setScreen(WaypointImportScreen(MinecraftClient.screen()))
        }

        private const val PREVIEW_TOP = BUTTON_ROW * 2 + TEXT_HEIGHT + GAP
        private const val GROUP_HEIGHT = BUTTON_ROW * 2 + GAP
        private val intro = listOf(
            "Paste a share code or open a json/text file.",
            "You can also import from other mods, like Waypointer, Skytils, Skyblocker, SkyHanni, Coleweight, Soopy and Firmament.",
            "If you have trouble with importing, let a contributor know.",
        )
    }
}
