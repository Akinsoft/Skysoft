package com.skysoft.features.inventory

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.InventoryButtonIcons.IconCandidate
import com.skysoft.utils.gui.TextFieldState
import kotlin.math.ceil
import kotlin.math.min

internal class InventoryButtonIconSearch(
    private val columns: Int,
    private val visibleRows: Int,
) {
    val field = TextFieldState(maxLength = 64)
    var scrollRow = 0
        private set
    private var lastSearch: String? = null
    private var catalogVersion = -1L
    private var cachedCandidates: List<IconCandidate> = emptyList()

    fun reset() {
        field.text = ""
        field.focused = false
        resetScroll()
        lastSearch = null
        cachedCandidates = emptyList()
    }

    fun resetScroll() {
        scrollRow = 0
    }

    fun scrollBy(amount: Int) {
        val maxScroll = maxScrollRow()
        scrollRow = (scrollRow - amount).coerceIn(0, maxScroll)
    }

    fun clampScroll() {
        scrollRow = scrollRow.coerceIn(0, maxScrollRow())
    }

    fun candidates(): List<IconCandidate> {
        updateCandidates()
        scrollRow = min(scrollRow, maxScrollRow())
        return cachedCandidates
    }

    fun maxScrollRow(): Int {
        updateCandidates()
        val totalRows = ceil(cachedCandidates.size / columns.toDouble()).toInt()
        return (totalRows - visibleRows).coerceAtLeast(0)
    }

    private fun updateCandidates() {
        val search = field.text
        val version = SkyBlockDataRepository.snapshotVersion
        if (search == lastSearch && catalogVersion == version) return
        cachedCandidates = InventoryButtonIcons.searchIconCandidates(search)
        lastSearch = search
        catalogVersion = version
    }
}
