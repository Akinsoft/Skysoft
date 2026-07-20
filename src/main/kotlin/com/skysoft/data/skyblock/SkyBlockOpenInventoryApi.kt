package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.nonPlayerInventoryItems
import com.skysoft.utils.gui.nonPlayerInventoryKey
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

object SkyBlockOpenInventoryApi {
    private var listeners: List<Listener> = emptyList()

    fun register() {
        SkysoftClientEvents.onEndTick(
            "SkyBlock open inventory",
            isActive = ::hasActiveListeners,
            action = ::update,
        )
    }

    fun onUpdate(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockOpenInventorySnapshot?) -> Unit,
    ) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun update(minecraft: Minecraft) {
        val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
        val snapshot = if (HypixelLocationState.inSkyBlock && screen != null) {
            val title = screen.title.cleanSkyBlockText()
            SkyBlockOpenInventorySnapshot(
                title = title,
                items = screen.nonPlayerInventoryItems(),
                key = screen.nonPlayerInventoryKey(title),
                containerId = screen.menu.containerId,
            )
        } else {
            null
        }
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(snapshot) }
            }
        }
    }

    private fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockOpenInventorySnapshot?) -> Unit,
    )
}

data class SkyBlockOpenInventorySnapshot(
    val title: String,
    val items: Map<Int, ItemStack>,
    val key: String,
    val containerId: Int,
)
