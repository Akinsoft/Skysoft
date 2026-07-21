package com.skysoft.gui.scale

import com.mojang.blaze3d.platform.Window
import com.skysoft.config.SkysoftConfigGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import org.lwjgl.glfw.GLFW

class InventoryCursorMemory private constructor() {
    @JvmRecord
    data class CursorPoint(val x: Double, val y: Double)

    private data class CursorSnapshot(
        val cursor: CursorPoint,
        val level: ClientLevel?,
        val capturedAt: Long,
    )

    companion object {
        private const val EXPIRY_NANOS = 1_000_000_000L

        private var inventoryScreenClosed = false
        private var pending: CursorSnapshot? = null
        private var waitForCursorEvents = false

        @JvmStatic
        fun prepareForMouseGrab() {
            inventoryScreenClosed = isEnabled()
        }

        @JvmStatic
        fun beginMouseGrab(window: Window) {
            val shouldCapture = inventoryScreenClosed
            inventoryScreenClosed = false
            if (!isEnabled() || !shouldCapture) {
                discard()
                return
            }
            val level = Minecraft.getInstance().level
            if (!isUsable(pending) || pending?.level !== level) {
                val x = doubleArrayOf(0.0)
                val y = doubleArrayOf(0.0)
                GLFW.glfwGetCursorPos(window.handle(), x, y)
                pending = CursorSnapshot(CursorPoint(x[0], y[0]), level, System.nanoTime())
            }
            waitForCursorEvents = true
        }

        @JvmStatic
        fun cursorAfterInput(screen: Screen?): CursorPoint? {
            val minecraft = Minecraft.getInstance()
            if (
                !isEnabled() ||
                !minecraft.isWindowActive ||
                !isUsable(pending) ||
                pending?.level !== minecraft.level ||
                screen != null && screen !is AbstractContainerScreen<*>
            ) {
                discard()
                return null
            }
            if (screen == null) return null
            if (waitForCursorEvents) {
                waitForCursorEvents = false
                return null
            }
            val cursor = checkNotNull(pending).cursor
            discard()
            return cursor
        }

        private fun isUsable(snapshot: CursorSnapshot?): Boolean =
            snapshot?.let { System.nanoTime() - it.capturedAt <= EXPIRY_NANOS } == true

        private fun isEnabled(): Boolean = SkysoftConfigGui.config().inventory.preserveCursorPosition

        private fun discard() {
            inventoryScreenClosed = false
            pending = null
            waitForCursorEvents = false
        }
    }
}
