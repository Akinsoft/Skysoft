package com.skysoft.features.misc

import com.terraformersmc.modmenu.gui.widget.ModMenuButtonWidget
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents

internal object HiddenMenuLayout {
    private const val PAUSE_MENU_WIDTH = 204
    private const val BUTTON_GAP = 4
    fun compact(
        screen: Screen,
        widgets: MutableList<AbstractWidget>,
        original: List<AbstractWidget>,
        fullWidthModsButton: Boolean,
    ) {
        val width = if (screen is PauseScreen) PAUSE_MENU_WIDTH else Button.BIG_WIDTH
        val remaining = original.filter { it in widgets }.toMutableList()
        val mods = if (screen is PauseScreen && fullWidthModsButton) replaceModsButton(screen, widgets, remaining) else null
        val rows = remaining.groupBy { it.y }.toSortedMap().values.map { it.sortedBy(AbstractWidget::getX) }.toMutableList()
        if (mods != null) {
            val optionsRow = rows.indexOfFirst { row -> row.any { translationKey(it) == "menu.options" } }
            if (optionsRow >= 0) rows.add(optionsRow, listOf(mods))
        }
        val totalHeight = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1).coerceAtLeast(0) * BUTTON_GAP
        var y = maxOf((screen.height - totalHeight) / 2, original.minOf { it.y })
        for (row in rows) {
            if (row.size == 1 && row.single().width > row.single().height) row.single().width = width
            val rowWidth = row.sumOf { it.width } + (row.size - 1) * BUTTON_GAP
            var x = screen.width / 2 - rowWidth / 2
            for (button in row) {
                button.setPosition(x, y)
                x += button.width + BUTTON_GAP
            }
            y += row.maxOf { it.height } + BUTTON_GAP
        }
    }

    private fun replaceModsButton(
        screen: Screen,
        widgets: MutableList<AbstractWidget>,
        remaining: MutableList<AbstractWidget>,
    ): AbstractWidget? {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return null
        if (remaining.none { translationKey(it) == "menu.options" }) return null
        val original = remaining.firstOrNull { translationKey(it) == "modmenu.title" } ?: return null
        val replacement = ModMenuButtonWidget(
            0, 0, PAUSE_MENU_WIDTH, Button.DEFAULT_HEIGHT, Component.translatable("modmenu.title"), screen,
        )
        replacement.active = original.active
        widgets[widgets.indexOf(original)] = replacement
        remaining.remove(original)
        return replacement
    }

    private fun translationKey(widget: AbstractWidget): String? =
        (widget.message.contents as? TranslatableContents)?.key
}
