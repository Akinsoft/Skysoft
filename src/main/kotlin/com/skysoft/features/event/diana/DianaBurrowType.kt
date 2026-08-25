package com.skysoft.features.event.diana

import java.awt.Color
import java.util.Locale

private const val KNOWN_LINE_WIDTH = 3
private const val GUESS_LINE_WIDTH = 2

enum class DianaBurrowType(
    val label: String,
    val outlineColor: Color,
    val fillColor: Color,
    val lineWidth: Int,
) {
    START(
        "Start",
        Color(85, 255, 85, 230),
        Color(85, 255, 85, 60),
        KNOWN_LINE_WIDTH,
    ),
    MOB(
        "Mob",
        Color(255, 85, 85, 230),
        Color(255, 85, 85, 60),
        KNOWN_LINE_WIDTH,
    ),
    TREASURE(
        "Treasure",
        Color(255, 170, 0, 230),
        Color(255, 170, 0, 60),
        KNOWN_LINE_WIDTH,
    ),
    GUESS(
        "Guess",
        Color(255, 255, 255, 210),
        Color(170, 85, 255, 45),
        GUESS_LINE_WIDTH,
    ),
    ;

    companion object {
        fun parse(value: String?): DianaBurrowType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) }

        fun names(): String = entries.joinToString("/") { it.name.lowercase(Locale.ROOT) }
    }
}
