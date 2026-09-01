package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ActiveStatePublisher
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SkysoftScreenEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.nonPlayerInventoryKey
import com.skysoft.utils.gui.nonPlayerSlots
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack

object SkyBlockOpenInventoryApi {
    private val publisher = ActiveStatePublisher<SkyBlockOpenInventorySnapshot?>("SkyBlock Open Inventory API", null)

    fun register() {
        publisher.register()
        SkysoftClientEvents.onEndTick(
            "SkyBlock open inventory",
            isActive = { publisher.hasActiveListeners || publisher.state != null },
            action = ::update,
        )
        SkysoftScreenEvents.onBeforeInit(
            "SkyBlock open inventory close setup",
            isActive = { publisher.hasActiveListeners },
        ) { _, screen ->
            if (screen !is AbstractContainerScreen<*>) return@onBeforeInit
            ScreenEvents.remove(screen).register {
                SkysoftErrorBoundary.run("SkyBlock open inventory close") {
                    publishScreen(screen)
                }
            }
        }
        SkysoftClientEvents.onDisconnect("SkyBlock open inventory reset") {
            publisher.update(null)
        }
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockOpenInventorySnapshot?) -> Unit,
    ) {
        publisher.onChange(boundary, isActive, listener)
    }

    private fun update(minecraft: Minecraft) {
        val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
        if (!HypixelLocationState.inSkyBlock || screen == null) {
            publisher.update(null)
            return
        }
        publishScreen(screen)
    }

    internal fun capture(screen: AbstractContainerScreen<*>) {
        if (publisher.hasActiveListeners) publishScreen(screen)
    }

    private fun publishScreen(screen: AbstractContainerScreen<*>) {
        if (!HypixelLocationState.inSkyBlock) return
        val title = screen.title.cleanSkyBlockText()
        val key = screen.nonPlayerInventoryKey(title)
        val previous = publisher.state
        if (previous?.key == key && previous.containerId == screen.menu.containerId) return
        val cells = screen.nonPlayerSlots().map { slot ->
            SkyBlockOpenInventoryCell(slot.containerSlot, slot.item.copy())
        }
        publisher.update(
            SkyBlockOpenInventorySnapshot(
                title = title,
                cells = cells,
                key = key,
                containerId = screen.menu.containerId,
                menuRows = (screen as? ContainerScreen)?.menu?.rowCount,
            ),
        )
    }
}

data class SkyBlockOpenInventorySnapshot(
    val title: String,
    val cells: List<SkyBlockOpenInventoryCell>,
    val key: String,
    val containerId: Int,
    val menuRows: Int?,
) {
    val items: Map<Int, ItemStack> = cells.mapNotNull { cell ->
        cell.item.takeUnless(ItemStack::isEmpty)?.let { cell.index to it }
    }.toMap()
}

data class SkyBlockOpenInventoryCell(
    val index: Int,
    val item: ItemStack,
)
