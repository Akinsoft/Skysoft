package com.skysoft.data.skyblock

import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.world.item.ItemStack

object AttributeShardTransfers {
    private val listeners = ActiveListenerRegistry<(SkyBlockAttributeShardTransfer) -> Unit>()
    private val removalIntents = mutableMapOf<String, Long>()

    fun register() {
        SkyBlockInventoryChanges.onChange(
            "Attribute Shard Hunting Box removals",
            isActive = ::hasActiveListeners,
        ) { change ->
            val now = System.currentTimeMillis()
            removalIntents.entries.removeIf { (_, expiresAt) -> expiresAt <= now }
            change.changes.forEach { (itemId, amount) ->
                if (amount <= 0 || removalIntents.remove(itemId) == null) return@forEach
                dispatch(SkyBlockAttributeShardTransfer(itemId, amount, AttributeShardTransferDirection.FROM_BOX))
            }
        }
        SkysoftClientEvents.onDisconnect("Attribute Shard removal reset", removalIntents::clear)
    }

    fun onTransfer(boundary: String, isActive: () -> Boolean, listener: (SkyBlockAttributeShardTransfer) -> Unit) {
        listeners.register(boundary, isActive, listener)
    }

    fun recordDeposit(itemId: String, amount: Int) {
        dispatch(SkyBlockAttributeShardTransfer(itemId, amount, AttributeShardTransferDirection.TO_BOX))
    }

    fun recordRemoval(item: ItemStack) {
        if (!hasActiveListeners()) return
        val itemId = AttributeShardItemResolver.internalNameOrNull(item, "Hunting Box") ?: return
        removalIntents[itemId] = System.currentTimeMillis() + HUNTING_BOX_REMOVAL_MILLIS
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    private fun dispatch(transfer: SkyBlockAttributeShardTransfer) {
        listeners.forEachActive { listener -> listener(transfer) }
    }
}

data class SkyBlockAttributeShardTransfer(
    val itemId: String,
    val amount: Int,
    val direction: AttributeShardTransferDirection,
)

enum class AttributeShardTransferDirection {
    TO_BOX,
    FROM_BOX,
}

private const val HUNTING_BOX_REMOVAL_MILLIS = 3_000L
