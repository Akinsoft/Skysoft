package com.skysoft.gui

object BottomHudLayout {
    private val reservations = linkedMapOf<String, () -> Int>()

    fun registerReservation(id: String, height: () -> Int) {
        check(reservations.putIfAbsent(id, height) == null) { "Bottom HUD reservation is already registered: $id" }
    }

    fun reservedHeight(): Int = reservations.values.maxOfOrNull { it().coerceAtLeast(0) } ?: 0
}
