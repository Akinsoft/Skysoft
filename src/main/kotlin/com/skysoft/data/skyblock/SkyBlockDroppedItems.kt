package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.world.item.ItemStack
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

object SkyBlockDroppedItems {
    private val listeners = ActiveListenerRegistry<(SkyBlockDroppedItem) -> Unit>()
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
        listeners.register(boundary, isActive, listener)
    }

    fun recordIntent(stack: ItemStack, amount: Int) {
        if (!HypixelLocationState.inSkyBlock || !hasActiveListeners()) return
        val itemId = stack.skyBlockId() ?: return
        intents.add(itemId, min(amount, stack.count))
    }

    private fun dispatch(drop: SkyBlockDroppedItem) {
        listeners.forEachActive { listener -> listener(drop) }
    }

    private fun hasActiveListeners(): Boolean = listeners.hasActiveListeners
}

private class SkyBlockDropIntents {
    private val pending = mutableMapOf<String, Intent>()

    fun add(itemId: String, amount: Int) {
        if (amount <= 0) return
        discardExpired()
        val current = pending[itemId]
        pending[itemId] = Intent((current?.amount ?: 0) + amount, ElapsedTimeMark.now())
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
        pending.values.removeIf { intent -> intent.started.passedSince() >= DROP_INTENT_WINDOW }
    }

    private data class Intent(
        val amount: Int,
        val started: ElapsedTimeMark,
    )
}

data class SkyBlockDroppedItem(
    val itemId: String,
    val amount: Int,
)

private val DROP_INTENT_WINDOW = 3.seconds
