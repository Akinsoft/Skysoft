package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.MinecraftInput
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

private const val FIRST_PLAYER_SLOT = 0
private const val LAST_PLAYER_SLOT = 40
private const val OFFHAND_SLOT = 40
private const val BLOCKED_PULSE_MILLIS = 320L
private const val SLOT_SIZE = 16
private const val SLOT_OUTLINE_INSET = 1
private const val SLOT_OUTLINE_SIZE = 18
private const val PERCENT_MAX = 100f

object SlotLockManager {
    private val config get() = SkysoftConfigGui.config().inventory.protection.slotLocking
    private val lockedSlots get() = ProfileStorageApi.storage.slotLocks
    private var activeLockKey: Int? = null
    private var pendingLockSlot: Int? = null
    private var blockedSlot: Int? = null
    private var blockedUntilMillis = 0L

    fun register() {
        ProfileStorageApi.registerConsumer("Slot Locking") { config.enabled }
    }

    @JvmStatic
    fun beginFrame() {
        activeLockKey?.takeUnless(InputUtilities::isBindingDown)?.let {
            activeLockKey = null
            pendingLockSlot?.takeIf { isFeatureAvailable() }?.let(::toggleLock)
            pendingLockSlot = null
        }
        if (System.currentTimeMillis() >= blockedUntilMillis) blockedSlot = null
    }

    @JvmStatic
    fun handleKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): InputHandlingResult {
        if (!isFeatureAvailable() || !isLockKey(event.key())) return InputHandlingResult.IGNORED
        if (activeLockKey == event.key()) return InputHandlingResult.CONSUMED
        activeLockKey = event.key()

        val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
            ?: return InputHandlingResult.CONSUMED
        if (!isLockablePlayerSlot(slot)) return InputHandlingResult.CONSUMED
        if (SlotBindingManager.canHandleBindingKey(event.key())) {
            pendingLockSlot = slot.containerSlot
        } else {
            toggleLock(slot.containerSlot)
        }
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun clearInputState() {
        activeLockKey = null
        pendingLockSlot = null
    }

    @JvmStatic
    fun cancelPendingLock() {
        pendingLockSlot = null
    }

    fun isSlotLockPending(slotIndex: Int): Boolean = pendingLockSlot == slotIndex

    @JvmStatic
    @JvmOverloads
    fun renderSlotOverlay(
        context: GuiGraphicsExtractor,
        slot: Slot,
        x: Int = slot.x,
        y: Int = slot.y,
    ) {
        if (!isFeatureAvailable() || !isSlotLocked(slot)) return
        val isBlocked = blockedSlot == slot.containerSlot && System.currentTimeMillis() < blockedUntilMillis
        SlotLockRenderer.render(context, x, y, isBlocked, config.details.lockIconOpacity / PERCENT_MAX)
    }

    @JvmStatic
    fun handleSlotClick(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
    ): InputHandlingResult {
        val interaction = slotInteraction(screen, slot, button, action)
        if (interaction.decision == SlotLockInteractionDecision.ALLOW) return InputHandlingResult.IGNORED
        markBlocked(interaction.blockedSlot)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun canQuickCraftInto(slot: Slot): Boolean = !isFeatureAvailable() || !isSlotLocked(slot)

    @JvmStatic
    fun handleSelectedItemDrop(player: LocalPlayer): InputHandlingResult {
        val selectedSlot = player.inventory.selectedSlot
        if (!isFeatureAvailable() || selectedSlot !in lockedSlots) return InputHandlingResult.IGNORED
        markBlocked(selectedSlot)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun handleOffhandSwap(player: LocalPlayer?): InputHandlingResult {
        val selectedSlot = player?.inventory?.selectedSlot ?: return InputHandlingResult.IGNORED
        if (!isFeatureAvailable() || selectedSlot !in lockedSlots && OFFHAND_SLOT !in lockedSlots) {
            return InputHandlingResult.IGNORED
        }
        markBlocked(if (selectedSlot in lockedSlots) selectedSlot else OFFHAND_SLOT)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun slotInteractionDecision(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
    ): SlotLockInteractionDecision = slotInteraction(screen, slot, button, action).decision

    private fun slotInteraction(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
    ): SlotLockInteraction {
        if (!isFeatureAvailable() || lockedSlots.isEmpty()) return SlotLockInteraction.ALLOWED
        return SlotLockInteractionResolver.resolve(screen, slot, button, action, lockedSlots)
    }

    @JvmStatic
    fun isSlotLocked(slot: Slot?): Boolean = playerSlotIndex(slot) in lockedSlots

    @JvmStatic
    fun isLockKey(key: Int): Boolean = config.settings.lockKey != MinecraftInput.NO_BINDING && key == config.settings.lockKey

    @JvmStatic
    fun isFeatureAvailable(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    @JvmStatic
    fun resetAllLocks() {
        if (lockedSlots.isEmpty()) return
        ProfileStorageApi.updateProfile { it.slotLocks.clear() }
        clearInputState()
    }

    private fun toggleLock(slotIndex: Int) {
        ProfileStorageApi.updateProfile { profile ->
            val lockedSlots = profile.slotLocks
            if (!lockedSlots.remove(slotIndex)) {
                lockedSlots.add(slotIndex)
                lockedSlots.sort()
            }
        }
        SoundUtilities.playClickSound()
    }

    private fun markBlocked(slotIndex: Int?) {
        blockedSlot = slotIndex
        blockedUntilMillis = System.currentTimeMillis() + BLOCKED_PULSE_MILLIS
    }
}

private object SlotLockInteractionResolver {
    fun resolve(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
        lockedSlots: Collection<Int>,
    ): SlotLockInteraction {
        playerSlotIndex(slot)?.takeIf { it in lockedSlots }?.let {
            return SlotLockInteraction(SlotLockInteractionDecision.LOCKED_SLOT, it)
        }
        return when (action) {
            ContainerInput.SWAP -> swapTargetSlot(button)?.takeIf { it in lockedSlots }
                ?.let { SlotLockInteraction(SlotLockInteractionDecision.LOCKED_SWAP_TARGET, it) }
            ContainerInput.QUICK_MOVE -> firstQuickMoveTarget(screen, slot, lockedSlots)
                ?.let { SlotLockInteraction(SlotLockInteractionDecision.LOCKED_QUICK_MOVE_TARGET, it) }
            ContainerInput.PICKUP_ALL -> firstPickupAllSource(screen, lockedSlots)
                ?.let { SlotLockInteraction(SlotLockInteractionDecision.LOCKED_PICKUP_ALL_SOURCE, it) }
            else -> null
        } ?: SlotLockInteraction.ALLOWED
    }

    private fun firstQuickMoveTarget(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        lockedSlots: Collection<Int>,
    ): Int? {
        if (slot == null || slot.container is Inventory || slot.item.isEmpty) return null
        return screen.menu.slots.firstNotNullOfOrNull { target ->
            playerSlotIndex(target)?.takeIf { it in lockedSlots && canReceive(target, slot.item) }
        }
    }

    private fun firstPickupAllSource(screen: AbstractContainerScreen<*>, lockedSlots: Collection<Int>): Int? {
        val carried = screen.menu.carried
        if (carried.isEmpty) return null
        return screen.menu.slots.firstNotNullOfOrNull { target ->
            playerSlotIndex(target)?.takeIf {
                it in lockedSlots &&
                    target.hasItem() && ItemStack.isSameItemSameComponents(target.item, carried)
            }
        }
    }

    private fun canReceive(slot: Slot, item: ItemStack): Boolean {
        if (!slot.mayPlace(item)) return false
        val current = slot.item
        return current.isEmpty ||
            ItemStack.isSameItemSameComponents(current, item) && current.count < slot.getMaxStackSize(item)
    }
}

private fun playerSlotIndex(slot: Slot?): Int? = slot?.containerSlot?.takeIf { isLockablePlayerSlot(slot) }

private fun swapTargetSlot(button: Int): Int? =
    button.takeIf { Inventory.isHotbarSlot(button) || button == OFFHAND_SLOT }

private fun isLockablePlayerSlot(slot: Slot?): Boolean =
    slot?.container is Inventory && slot.containerSlot in FIRST_PLAYER_SLOT..LAST_PLAYER_SLOT

private object SlotLockRenderer {
    private const val SLOT_SHADE = 0x20000000
    private val slotBorder = 0xCCE5AE43.toInt()
    private val blockedBorder = 0xFFFF5555.toInt()
    private val barrierStack = ItemStack(Items.BARRIER)

    fun render(context: GuiGraphicsExtractor, x: Int, y: Int, isBlocked: Boolean, iconAlpha: Float) {
        val borderColor = if (isBlocked) blockedBorder else slotBorder
        context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_SHADE)
        context.outline(
            x - SLOT_OUTLINE_INSET,
            y - SLOT_OUTLINE_INSET,
            SLOT_OUTLINE_SIZE,
            SLOT_OUTLINE_SIZE,
            borderColor,
        )
        ItemIconRenderable(barrierStack, alpha = iconAlpha).renderAt(context, x, y)
    }
}

enum class SlotLockInteractionDecision {
    ALLOW,
    LOCKED_SLOT,
    LOCKED_SWAP_TARGET,
    LOCKED_QUICK_MOVE_TARGET,
    LOCKED_PICKUP_ALL_SOURCE,
}

private data class SlotLockInteraction(val decision: SlotLockInteractionDecision, val blockedSlot: Int?) {
    companion object {
        val ALLOWED = SlotLockInteraction(SlotLockInteractionDecision.ALLOW, null)
    }
}
