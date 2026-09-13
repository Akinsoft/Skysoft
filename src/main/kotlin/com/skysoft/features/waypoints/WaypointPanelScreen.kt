package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

internal class WaypointPanelScreen : Screen(Component.literal("Skysoft Waypoints Display")) {
    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun isPauseScreen(): Boolean = false

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean = WaypointPanelInput.didClick(click)

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean = WaypointPanelInput.didDrag(click)

    override fun mouseReleased(click: MouseButtonEvent): Boolean = WaypointPanelInput.didRelease(click)

    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean =
        WaypointPanelInput.didScroll(x, y, vertical)

    override fun keyPressed(event: KeyEvent): Boolean {
        val options = Minecraft.getInstance().options
        when {
            event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_Z -> WaypointPanel.perform { Waypoints.undo() }
            event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_Y -> WaypointPanel.perform { Waypoints.redo() }
            options.keyInventory.matches(event) -> {
                Minecraft.getInstance().player?.let { MinecraftClient.setScreen(InventoryScreen(it)) }
            }
            options.keyChat.matches(event) -> MinecraftClient.setScreen(ChatScreen("", false))
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun onClose() = WaypointPanel.dismiss()
}
