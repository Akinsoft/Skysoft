package com.skysoft.data.skyblock

import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.world.item.ItemStack

object AttributeShardTransfers {
    private var listeners: List<Listener> = emptyList()
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
        listeners += Listener(boundary, isActive, listener)
    }

    fun recordDeposit(itemId: String, amount: Int) {
        dispatch(SkyBlockAttributeShardTransfer(itemId, amount, AttributeShardTransferDirection.TO_BOX))
    }

    fun recordRemoval(item: ItemStack) {
        if (!hasActiveListeners()) return
        val itemId = AttributeShardItemResolver.internalNameOrNull(item, "Hunting Box") ?: return
        removalIntents[itemId] = System.currentTimeMillis() + HUNTING_BOX_REMOVAL_MILLIS
    }

    fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    private fun dispatch(transfer: SkyBlockAttributeShardTransfer) {
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(transfer) }
            }
        }
    }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockAttributeShardTransfer) -> Unit,
    )
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
