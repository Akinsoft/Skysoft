package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

internal class WaypointShareScreen private constructor(parent: Screen?) : WaypointDialog("Share Waypoints", parent, primaryLabel = null) {
    private val selected = Waypoints.group
    private val snapshot = WaypointLibrary.groups.toList()
    private val island = selected?.island ?: Waypoints.browseIsland ?: Waypoints.island
    private val scopes = ExportScope.entries.filter {
        (it != ExportScope.GROUP || selected != null) && (it != ExportScope.ISLAND || island != null)
    }
    private var scope = if (selected == null) ExportScope.LIBRARY else ExportScope.GROUP
    private var status = ""
    override val preferredWidth = 240
    override val contentHeight: Int get() = BUTTON_ROW * STATUS_ROW +
        if (status.isEmpty() && !busy) 0 else waypointNoteHeight(if (busy) "Preparing export…" else status, bodyWidth) + GAP
    private val groups get() = when (scope) {
        ExportScope.GROUP -> listOfNotNull(selected)
        ExportScope.ISLAND -> snapshot.filter { it.island == island }
        ExportScope.LIBRARY -> snapshot
    }

    override fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        val labelWidth = font.width("Share: ")
        painter.text(Rect(x, y + GAP, labelWidth, TEXT_HEIGHT), "Share: ")
        painter.cycle(
            Rect(x + labelWidth, y, width - labelWidth, FIELD_HEIGHT), "Share", scopes, scope, ::scopeLabel,
            enabled = !busy, leftAligned = true,
        ) {
            scope = it
            status = ""
        }
        painter.action(Rect(x, y + BUTTON_ROW, width, FIELD_HEIGHT), "Copy share", enabled = !busy && groups.isNotEmpty()) {
            val copy = groups
            transfer({ WaypointImportCodec.export(copy) }) { code ->
                Minecraft.getInstance().keyboardHandler.clipboard = code
                status = "Copied."
            }
        }
        painter.action(Rect(x, y + BUTTON_ROW * FILE_ROW, width, FIELD_HEIGHT), "Save JSON file", enabled = !busy && groups.isNotEmpty()) {
            val copy = groups
            transfer({ WaypointTransferFiles.save(WaypointImportCodec.exportJson(copy)) }) { path ->
                status = if (path == null) "File selection cancelled." else "Saved ${path.fileName}."
            }
        }
        painter.note(if (busy) "Preparing export…" else status, x, y + BUTTON_ROW * STATUS_ROW, width)
    }

    private fun scopeLabel(scope: ExportScope): String = when (scope) {
        ExportScope.GROUP -> requireNotNull(selected).name
        ExportScope.ISLAND -> requireNotNull(island).displayName
        ExportScope.LIBRARY -> "Entire library"
    }

    override fun confirm() = Unit

    private enum class ExportScope {
        GROUP,
        ISLAND,
        LIBRARY,
    }

    companion object {
        private const val FILE_ROW = 2
        private const val STATUS_ROW = 3
        fun open() {
            MinecraftClient.setScreen(WaypointShareScreen(MinecraftClient.screen()))
        }
    }
}
