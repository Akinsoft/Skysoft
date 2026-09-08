package com.skysoft

import com.mojang.logging.LogUtils
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.gui.DeferredScreenRequests
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputUtilities
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

class SkysoftMod : ClientModInitializer {
    override fun onInitializeClient() {
        SkysoftErrorBoundary.register()
        SkysoftFeatureRegistrations.registerAll()
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SkysoftErrorBoundary.run("Config save") { SkysoftConfigGui.config().saveNow() }
            SkysoftErrorBoundary.run("Profile storage save") { ProfileStorageApi.flush() }
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            SkysoftErrorBoundary.run("Command registration") { SkysoftCommands.register(dispatcher) }
        }
        SkysoftClientEvents.onEndTick("Position Editor keybind", ::hasPositionEditorKeybind) {
            handlePositionEditorKeybind()
        }
        SkysoftClientEvents.onEndTick("Tooltip keyboard navigation", TooltipViewport::needsKeyboardUpdate) {
            TooltipViewport.updateKeyboardPan()
        }
    }

    companion object {
        const val MOD_ID: String = "skysoft"

        val VERSION: String
            get() = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
        val LOGGER = LogUtils.getLogger()

        fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

        private var positionEditorKeyWasDown = false

        private fun hasPositionEditorKeybind(): Boolean =
            SkysoftConfigGui.config().gui.positionEditor.keybind != GLFW.GLFW_KEY_UNKNOWN || positionEditorKeyWasDown

        private fun handlePositionEditorKeybind() {
            val key = SkysoftConfigGui.config().gui.positionEditor.keybind
            val minecraft = Minecraft.getInstance()
            val keyDown = key != GLFW.GLFW_KEY_UNKNOWN &&
                key != GLFW.GLFW_KEY_ENTER &&
                InputUtilities.isActionBindingDown(key)
            if (!keyDown) {
                positionEditorKeyWasDown = false
                return
            }
            if (positionEditorKeyWasDown) return
            positionEditorKeyWasDown = true

            val screen = MinecraftClient.screen(minecraft)
            if (screen is SkysoftHudEditor.EditorScreen) return
            if (screen != null && screen !is AbstractContainerScreen<*>) return
            DeferredScreenRequests.request("HUD editor", SkysoftHudEditor::open)
        }
    }
}
