package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.mixin.AbstractContainerScreenAccessor
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
import org.lwjgl.glfw.GLFW

private const val FIRST_PLAYER_SLOT = 0
private const val LAST_PLAYER_SLOT = 40
private const val FIRST_HOTBAR_SLOT = 0
private const val LAST_HOTBAR_SLOT = 8
private const val OFFHAND_SLOT = 40
private const val BLOCKED_PULSE_MILLIS = 320L
private const val SLOT_SIZE = 16
private const val SLOT_OUTLINE_INSET = 1
private const val SLOT_OUTLINE_SIZE = 18

object SlotLockManager {
    private val config get() = SkysoftConfigGui.config().inventory.slotLocking
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
    fun renderSlotOverlay(context: GuiGraphicsExtractor, slot: Slot) {
        if (!isFeatureAvailable() || !isSlotLocked(slot)) return
        val isBlocked = blockedSlot == slot.containerSlot && System.currentTimeMillis() < blockedUntilMillis
        SlotLockRenderer.render(context, slot, isBlocked)
    }

    @JvmStatic
    fun handleSlotClick(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
    ): InputHandlingResult {
        val decision = slotInteractionDecision(screen, slot, button, action)
        if (decision == SlotLockInteractionDecision.ALLOW) return InputHandlingResult.IGNORED
        markBlocked(SlotLockInteractionResolver.blockedSlotFor(screen, slot, button, action, decision, lockedSlots))
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
    ): SlotLockInteractionDecision {
        if (!isFeatureAvailable() || lockedSlots.isEmpty()) return SlotLockInteractionDecision.ALLOW
        return SlotLockInteractionResolver.decision(screen, slot, button, action, lockedSlots)
    }

    @JvmStatic
    fun isSlotLocked(slot: Slot?): Boolean =
        slot?.let { isLockablePlayerSlot(it) && it.containerSlot in lockedSlots } == true

    @JvmStatic
    fun isSlotIndexLocked(slotIndex: Int): Boolean = slotIndex in lockedSlots

    @JvmStatic
    fun isLockKey(key: Int): Boolean = config.lockKey != GLFW.GLFW_KEY_UNKNOWN && key == config.lockKey

    @JvmStatic
    fun isFeatureAvailable(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    @JvmStatic
    fun hasLocks(): Boolean = lockedSlots.isNotEmpty()

    @JvmStatic
    fun resetAllLocks() {
        if (lockedSlots.isEmpty()) return
        lockedSlots.clear()
        ProfileStorageApi.markDirty()
        clearInputState()
    }

    private fun toggleLock(slotIndex: Int) {
        if (!lockedSlots.remove(slotIndex)) {
            lockedSlots.add(slotIndex)
            lockedSlots.sort()
        }
        ProfileStorageApi.markDirty()
        SoundUtilities.playClickSound()
    }

    private fun markBlocked(slotIndex: Int?) {
        blockedSlot = slotIndex
        blockedUntilMillis = System.currentTimeMillis() + BLOCKED_PULSE_MILLIS
    }
}

private object SlotLockInteractionResolver {
    fun decision(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
        lockedSlots: Collection<Int>,
    ): SlotLockInteractionDecision = slotLockInteractionDecision(
        lockedSlots = lockedSlots.toSet(),
        action = action,
        clickedPlayerSlot = playerSlotIndex(slot),
        swapPlayerSlot = swapTargetSlot(action, button),
        hasQuickMoveLockedTarget = action == ContainerInput.QUICK_MOVE &&
            firstQuickMoveTarget(screen, slot, lockedSlots) != null,
        hasPickupAllLockedSource = action == ContainerInput.PICKUP_ALL &&
            firstPickupAllSource(screen, lockedSlots) != null,
    )

    fun blockedSlotFor(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
        decision: SlotLockInteractionDecision,
        lockedSlots: Collection<Int>,
    ): Int? = when (decision) {
        SlotLockInteractionDecision.ALLOW -> null
        SlotLockInteractionDecision.LOCKED_SLOT -> playerSlotIndex(slot)
        SlotLockInteractionDecision.LOCKED_SWAP_TARGET -> swapTargetSlot(action, button)
        SlotLockInteractionDecision.LOCKED_QUICK_MOVE_TARGET -> firstQuickMoveTarget(screen, slot, lockedSlots)
        SlotLockInteractionDecision.LOCKED_PICKUP_ALL_SOURCE -> firstPickupAllSource(screen, lockedSlots)
    }

    private fun firstQuickMoveTarget(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        lockedSlots: Collection<Int>,
    ): Int? {
        if (slot == null || slot.container is Inventory || slot.item.isEmpty) return null
        return screen.menu.slots.firstNotNullOfOrNull { target ->
            target.containerSlot.takeIf { isSlotLocked(target, lockedSlots) && canReceive(target, slot.item) }
        }
    }

    private fun firstPickupAllSource(screen: AbstractContainerScreen<*>, lockedSlots: Collection<Int>): Int? {
        val carried = screen.menu.carried
        if (carried.isEmpty) return null
        return screen.menu.slots.firstNotNullOfOrNull { target ->
            target.containerSlot.takeIf {
                isSlotLocked(target, lockedSlots) &&
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

    private fun isSlotLocked(slot: Slot, lockedSlots: Collection<Int>): Boolean =
        isLockablePlayerSlot(slot) && slot.containerSlot in lockedSlots
}

private fun playerSlotIndex(slot: Slot?): Int? = slot?.containerSlot?.takeIf { isLockablePlayerSlot(slot) }

private fun swapTargetSlot(action: ContainerInput, button: Int): Int? =
    button.takeIf {
        action == ContainerInput.SWAP && (button in FIRST_HOTBAR_SLOT..LAST_HOTBAR_SLOT || button == OFFHAND_SLOT)
    }

private fun isLockablePlayerSlot(slot: Slot?): Boolean =
    slot?.container is Inventory && slot.containerSlot in FIRST_PLAYER_SLOT..LAST_PLAYER_SLOT

private object SlotLockRenderer {
    private const val SLOT_SHADE = 0x20000000
    private const val BARRIER_OPACITY = 0.5f
    private val slotBorder = 0xCCE5AE43.toInt()
    private val blockedBorder = 0xFFFF5555.toInt()
    private val barrierIcon = ItemIconRenderable(ItemStack(Items.BARRIER), alpha = BARRIER_OPACITY)

    fun render(context: GuiGraphicsExtractor, slot: Slot, isBlocked: Boolean) {
        val borderColor = if (isBlocked) blockedBorder else slotBorder
        context.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, SLOT_SHADE)
        context.outline(
            slot.x - SLOT_OUTLINE_INSET,
            slot.y - SLOT_OUTLINE_INSET,
            SLOT_OUTLINE_SIZE,
            SLOT_OUTLINE_SIZE,
            borderColor,
        )
        barrierIcon.renderAt(context, slot.x, slot.y)
    }
}

enum class SlotLockInteractionDecision {
    ALLOW,
    LOCKED_SLOT,
    LOCKED_SWAP_TARGET,
    LOCKED_QUICK_MOVE_TARGET,
    LOCKED_PICKUP_ALL_SOURCE,
}

internal fun slotLockInteractionDecision(
    lockedSlots: Set<Int>,
    action: ContainerInput,
    clickedPlayerSlot: Int?,
    swapPlayerSlot: Int?,
    hasQuickMoveLockedTarget: Boolean,
    hasPickupAllLockedSource: Boolean,
): SlotLockInteractionDecision = when {
    clickedPlayerSlot in lockedSlots -> SlotLockInteractionDecision.LOCKED_SLOT
    action == ContainerInput.SWAP && swapPlayerSlot in lockedSlots -> SlotLockInteractionDecision.LOCKED_SWAP_TARGET
    action == ContainerInput.QUICK_MOVE && hasQuickMoveLockedTarget ->
        SlotLockInteractionDecision.LOCKED_QUICK_MOVE_TARGET
    action == ContainerInput.PICKUP_ALL && hasPickupAllLockedSource ->
        SlotLockInteractionDecision.LOCKED_PICKUP_ALL_SOURCE
    else -> SlotLockInteractionDecision.ALLOW
}
