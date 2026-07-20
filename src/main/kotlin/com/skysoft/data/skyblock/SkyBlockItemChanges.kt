package com.skysoft.data.skyblock

import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary

object SkyBlockItemChanges {
    private var listeners: List<Listener> = emptyList()
    private val pendingChanges = SkyBlockItemChangeAccumulator()

    fun register() {
        SkyBlockDataRepository.Demand.register("SkyBlock Item Changes", ::hasActiveListeners)
        SkyBlockInventoryChanges.onChange(
            "SkyBlock inventory item changes",
            isActive = ::hasActiveListeners,
        ) { inventoryChange ->
            pendingChanges.add(SkyBlockItemChangeSource.INVENTORY, inventoryChange.changes)
        }
        SkyBlockSackChanges.onChange(
            "SkyBlock Sacks item changes",
            isActive = ::hasActiveListeners,
        ) { sackChange ->
            val changes = buildMap {
                sackChange.changes.forEach { change ->
                    val itemId = SkyBlockItemNames.itemId(change.displayName) ?: return@forEach
                    put(itemId, getOrDefault(itemId, 0) + change.amount)
                }
            }
            pendingChanges.add(SkyBlockItemChangeSource.SACKS, changes)
        }
        SkysoftClientEvents.onEndTick(
            "SkyBlock item changes",
            isActive = { hasActiveListeners() || pendingChanges.isNotEmpty },
        ) {
            pendingChanges.drain()?.let(::dispatch)
        }
        SkysoftClientEvents.onDisconnect("SkyBlock item changes reset", pendingChanges::clear)
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockItemChangeBatch) -> Unit,
    ) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun dispatch(batch: SkyBlockItemChangeBatch) {
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(batch) }
            }
        }
    }

    private fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockItemChangeBatch) -> Unit,
    )
}

internal class SkyBlockItemChangeAccumulator {
    private val changes = linkedMapOf<String, Int>()
    private val sources = mutableSetOf<SkyBlockItemChangeSource>()

    val isNotEmpty: Boolean
        get() = changes.isNotEmpty()

    fun add(source: SkyBlockItemChangeSource, additions: Map<String, Int>) {
        if (additions.isEmpty()) return
        sources += source
        additions.forEach { (itemId, amount) ->
            changes[itemId] = changes.getOrDefault(itemId, 0) + amount
        }
    }

    fun drain(): SkyBlockItemChangeBatch? {
        val netChanges = changes.filterValues { amount -> amount != 0 }
        val source = when (sources.size) {
            0 -> null
            1 -> sources.single()
            else -> SkyBlockItemChangeSource.MIXED
        }
        clear()
        return source?.let { SkyBlockItemChangeBatch(it, netChanges) }?.takeIf { it.changes.isNotEmpty() }
    }

    fun clear() {
        changes.clear()
        sources.clear()
    }
}

data class SkyBlockItemChangeBatch(
    val source: SkyBlockItemChangeSource,
    val changes: Map<String, Int>,
)

enum class SkyBlockItemChangeSource {
    INVENTORY,
    SACKS,
    MIXED,
}
