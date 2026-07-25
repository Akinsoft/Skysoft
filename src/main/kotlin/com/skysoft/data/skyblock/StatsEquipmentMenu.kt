package com.skysoft.data.skyblock

internal object StatsEquipmentMenu {
    fun isTitle(title: String?): Boolean = title != null && title in TITLES

    private val TITLES = setOf("Your Equipment and Stats", "Stats & Equipment")
}
