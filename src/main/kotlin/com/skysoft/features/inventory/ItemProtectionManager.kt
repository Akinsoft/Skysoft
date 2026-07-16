package com.skysoft.features.inventory

import com.mojang.brigadier.Command
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockUuid
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.toPackedArgb
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.renderables.withIsolatedPose
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object ItemProtectionManager {
    private val config get() = SkysoftConfigGui.config().inventory.protectItem
    private val protectedItemUuids get() = ProfileStorageApi.storage.protectedItemUuids
    private var activeProtectKey: Int? = null

    @JvmStatic
    fun beginFrame() {
        activeProtectKey?.takeUnless(InputUtilities::isKeyDown)?.let { activeProtectKey = null }
    }

    @JvmStatic
    fun clearInputState() {
        activeProtectKey = null
    }

    @JvmStatic
    fun handleKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): InputHandlingResult {
        if (!isFeatureAvailable() || !isProtectKey(event.key())) return InputHandlingResult.IGNORED
        if (activeProtectKey == event.key()) return InputHandlingResult.CONSUMED
        activeProtectKey = event.key()

        val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
        if (!isPlayerInventorySlot(slot)) {
            SkysoftChat.error("Hover an item in your inventory to protect it.")
            return InputHandlingResult.CONSUMED
        }
        reportChange(changeProtection(requireNotNull(slot).item))
        return InputHandlingResult.CONSUMED
    }

    fun toggleHeldItem(source: FabricClientCommandSource): Int {
        if (!isFeatureAvailable()) {
            SkysoftChat.error(source, "Protect Item is disabled or unavailable outside SkyBlock.")
            return Command.SINGLE_SUCCESS
        }
        reportChange(changeProtection(Minecraft.getInstance().player?.mainHandItem ?: ItemStack.EMPTY), source)
        return Command.SINGLE_SUCCESS
    }

    @JvmStatic
    fun handleContainerDrop(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        slotId: Int,
        action: ContainerInput,
    ): InputHandlingResult {
        if (!isFeatureAvailable() || !isContainerItemDrop(action, slotId)) return InputHandlingResult.IGNORED
        val stack = slot?.item ?: screen.menu.carried
        if (!isProtected(stack)) return InputHandlingResult.IGNORED
        SkysoftChat.error("${stack.hoverName.string} is protected.")
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun handleWorldDrop(player: LocalPlayer): InputHandlingResult {
        val stack = player.mainHandItem
        if (worldDropDecision(stack) != ItemDropProtectionDecision.BLOCK) return InputHandlingResult.IGNORED
        SkysoftChat.error("${stack.hoverName.string} is protected.")
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun shouldAllowDungeonUltimate(player: LocalPlayer): Boolean =
        worldDropDecision(player.mainHandItem) == ItemDropProtectionDecision.ALLOW_DUNGEON_ULTIMATE

    fun isProtected(stack: ItemStack): Boolean =
        stack.skyBlockUuid()?.let { it in protectedItemUuids } == true

    @JvmStatic
    fun renderProtectedMarker(context: GuiGraphicsExtractor, slot: Slot) {
        if (!isFeatureAvailable() || !config.settings.showProtectedItemStar || !isProtected(slot.item)) return
        val font = Minecraft.getInstance().font
        val scale = config.settings.protectedItemStarScale
        context.withIsolatedPose {
            pose().translate((slot.x + SLOT_SIZE).toFloat(), (slot.y + SLOT_SIZE).toFloat())
            pose().scale(scale, scale)
            text(
                font,
                PROTECTED_MARKER,
                -font.width(PROTECTED_MARKER),
                -font.lineHeight,
                config.settings.protectedItemStarColor.get().toColor()
                    .toPackedArgb(config.settings.protectedItemStarOpacity / PERCENT_MAX),
                true,
            )
        }
    }

    fun isProtectKey(key: Int): Boolean =
        config.settings.protectKey != GLFW.GLFW_KEY_UNKNOWN && key == config.settings.protectKey

    fun isFeatureAvailable(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    fun hasProtectedItems(): Boolean = protectedItemUuids.isNotEmpty()

    fun resetProtectedItems() {
        if (protectedItemUuids.isEmpty()) return
        protectedItemUuids.clear()
        ProfileStorageApi.markDirty()
        clearInputState()
    }

    internal fun worldDropDecision(stack: ItemStack): ItemDropProtectionDecision =
        itemDropProtectionDecision(
            isFeatureAvailable = isFeatureAvailable(),
            isProtected = isProtected(stack),
            isDungeon = HypixelLocationState.currentIsland == SkyBlockIsland.DUNGEONS,
            allowDungeonUltimates = config.settings.allowDungeonUltimates,
        )

    private fun changeProtection(stack: ItemStack): ItemProtectionChangeResult {
        if (stack.isEmpty) return ItemProtectionChangeResult.NO_ITEM
        val uuid = stack.skyBlockUuid() ?: return ItemProtectionChangeResult.NO_SKYBLOCK_UUID
        return if (protectedItemUuids.remove(uuid)) {
            ProfileStorageApi.markDirty()
            ItemProtectionChangeResult.UNPROTECTED
        } else {
            protectedItemUuids.add(uuid)
            ProfileStorageApi.markDirty()
            ItemProtectionChangeResult.PROTECTED
        }
    }

    private fun reportChange(result: ItemProtectionChangeResult, source: FabricClientCommandSource? = null) {
        when (result) {
            ItemProtectionChangeResult.PROTECTED -> SoundUtilities.playItemProtectedSound()
            ItemProtectionChangeResult.UNPROTECTED -> SoundUtilities.playItemUnprotectedSound()
            else -> Unit
        }
        val message = when (result) {
            ItemProtectionChangeResult.PROTECTED -> "Item protected."
            ItemProtectionChangeResult.UNPROTECTED -> "Item unprotected."
            ItemProtectionChangeResult.NO_ITEM -> "Hold or hover a SkyBlock item first."
            ItemProtectionChangeResult.NO_SKYBLOCK_UUID -> "That item does not have a SkyBlock UUID."
        }
        if (source == null) {
            if (result.isSuccess) SkysoftChat.success(message) else SkysoftChat.error(message)
        } else if (result.isSuccess) {
            SkysoftChat.feedback(source, message)
        } else {
            SkysoftChat.error(source, message)
        }
    }
}

enum class ItemDropProtectionDecision {
    ALLOW,
    ALLOW_DUNGEON_ULTIMATE,
    BLOCK,
}

internal fun itemDropProtectionDecision(
    isFeatureAvailable: Boolean,
    isProtected: Boolean,
    isDungeon: Boolean,
    allowDungeonUltimates: Boolean,
): ItemDropProtectionDecision = when {
    !isFeatureAvailable || !isProtected -> ItemDropProtectionDecision.ALLOW
    isDungeon && allowDungeonUltimates -> ItemDropProtectionDecision.ALLOW_DUNGEON_ULTIMATE
    else -> ItemDropProtectionDecision.BLOCK
}

internal fun isContainerItemDrop(action: ContainerInput, slotId: Int): Boolean =
    action == ContainerInput.THROW || action == ContainerInput.PICKUP && slotId == OUTSIDE_SLOT

private enum class ItemProtectionChangeResult(val isSuccess: Boolean) {
    PROTECTED(true),
    UNPROTECTED(true),
    NO_ITEM(false),
    NO_SKYBLOCK_UUID(false),
}

private fun isPlayerInventorySlot(slot: Slot?): Boolean = slot?.container is Inventory

private const val OUTSIDE_SLOT = -999
private const val SLOT_SIZE = 16
private const val PROTECTED_MARKER = "✦"
private const val PERCENT_MAX = 100.0
