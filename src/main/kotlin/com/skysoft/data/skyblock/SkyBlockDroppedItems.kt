package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.world.item.ItemStack
import kotlin.math.min

object SkyBlockDroppedItems {
    private var listeners: List<Listener> = emptyList()
    private val intents = SkyBlockDropIntents()

    fun register() {
        SkyBlockInventoryChanges.onChange(
            "SkyBlock dropped items",
            isActive = ::hasActiveListeners,
        ) { change ->
            intents.confirm(change.changes).forEach(::dispatch)
        }
        SkysoftClientEvents.onDisconnect("SkyBlock dropped items reset", intents::clear)
    }

    fun onDrop(boundary: String, isActive: () -> Boolean, listener: (SkyBlockDroppedItem) -> Unit) {
        listeners += Listener(boundary, isActive, listener)
    }

    fun recordIntent(stack: ItemStack, amount: Int) {
        if (!HypixelLocationState.inSkyBlock || !hasActiveListeners()) return
        val itemId = stack.skyBlockId() ?: return
        intents.add(itemId, min(amount, stack.count))
    }

    private fun dispatch(drop: SkyBlockDroppedItem) {
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(drop) }
            }
        }
    }

    private fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockDroppedItem) -> Unit,
    )
}

internal class SkyBlockDropIntents(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val pending = mutableMapOf<String, Intent>()

    fun add(itemId: String, amount: Int) {
        if (amount <= 0) return
        discardExpired()
        val current = pending[itemId]
        pending[itemId] = Intent((current?.amount ?: 0) + amount, currentTimeMillis() + DROP_INTENT_MILLIS)
    }

    fun confirm(changes: Map<String, Int>): List<SkyBlockDroppedItem> {
        discardExpired()
        return buildList {
            changes.forEach { (itemId, change) ->
                val intent = pending[itemId] ?: return@forEach
                if (change >= 0) return@forEach
                val amount = min(-change, intent.amount)
                val remaining = intent.amount - amount
                if (remaining == 0) pending.remove(itemId) else pending[itemId] = intent.copy(amount = remaining)
                add(SkyBlockDroppedItem(itemId, amount))
            }
        }
    }

    fun clear() = pending.clear()

    private fun discardExpired() {
        val now = currentTimeMillis()
        pending.entries.removeIf { (_, intent) -> intent.expiresAtMillis <= now }
    }

    private data class Intent(
        val amount: Int,
        val expiresAtMillis: Long,
    )
}

data class SkyBlockDroppedItem(
    val itemId: String,
    val amount: Int,
)

private const val DROP_INTENT_MILLIS = 3_000L
