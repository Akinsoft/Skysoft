package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryDropSelectionGuard
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SkyBlockMenuInventoryDropFix
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.pets.PetStorageService
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(AbstractContainerScreen::class)
abstract class AbstractContainerScreenMixin {
    @Inject(method = ["init()V"], at = [At("TAIL")])
    protected fun skysoftLayoutStorageOverlay(ci: CallbackInfo) {
        StorageOverlayController.layoutScreen(this as AbstractContainerScreen<*>)
        InventoryEquipment.layoutScreen(this as AbstractContainerScreen<*>)
    }

    @Inject(method = ["removed"], at = [At("TAIL")])
    protected fun skysoftRestoreInventoryEquipmentLayout(ci: CallbackInfo) {
        BazaarTracker.restoreOrderMenu(this as AbstractContainerScreen<*>)
        InventoryEquipment.restoreScreen(this as AbstractContainerScreen<*>)
        SlotLockManager.clearInputState()
        ItemProtectionManager.clearInputState()
    }

    @Inject(method = ["extractTooltip"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSuppressTooltipDuringSlotBinding(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        if (SlotBindingManager.shouldSuppressRegularTooltips(this as AbstractContainerScreen<*>)) {
            ci.cancel()
        }
    }

    @Inject(method = ["extractLabels"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSuppressStorageOverlayLabels(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        if (StorageOverlayController.shouldSuppressContainerLabels(this as AbstractContainerScreen<*>)) {
            ci.cancel()
        }
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftClickStorageOverlay(
        click: MouseButtonEvent,
        doubled: Boolean,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (
            ItemListController.handleMouseClick(this as AbstractContainerScreen<*>, click, doubled) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        if (
            StorageOverlayController.handleMouseClick(this as AbstractContainerScreen<*>, click) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        if (
            BazaarTracker.handleMouseClick(this as AbstractContainerScreen<*>, click) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        if (
            InventoryEquipment.handleMouseClick(this as AbstractContainerScreen<*>, click) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        if (
            InventoryButtonManager.handleMouseClick(this as AbstractContainerScreen<*>, click) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["mouseReleased"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftReleaseOverlayInput(
        click: MouseButtonEvent,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (StorageOverlayController.handleMouseRelease(click) == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        if (
            InventoryButtonManager.handleMouseRelease(this as AbstractContainerScreen<*>, click) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["mouseDragged"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftDragStorageOverlay(
        click: MouseButtonEvent,
        deltaX: Double,
        deltaY: Double,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (
            StorageOverlayController.handleMouseDrag(this as AbstractContainerScreen<*>, click) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
        }
    }

    @WrapOperation(
        method = ["mouseClicked"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;" +
                    "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            ),
        ],
    )
    protected fun isSkysoftStorageOverlayWidgetClickHandled(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
        doubled: Boolean,
        original: Operation<Boolean>,
    ): Boolean {
        if (StorageOverlayController.isActive(screen)) {
            return false
        }
        return original.call(screen, click, doubled)
    }

    @WrapOperation(
        method = ["mouseDragged"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;" +
                    "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
            ),
        ],
    )
    protected fun isSkysoftStorageOverlayWidgetDragHandled(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
        deltaX: Double,
        deltaY: Double,
        original: Operation<Boolean>,
    ): Boolean {
        if (StorageOverlayController.isActive(screen)) {
            return false
        }
        return original.call(screen, click, deltaX, deltaY)
    }

    @WrapOperation(
        method = ["mouseDragged"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;" +
                    "shouldAddSlotToQuickCraft(Lnet/minecraft/world/inventory/Slot;" +
                    "Lnet/minecraft/world/item/ItemStack;)Z",
            ),
        ],
    )
    protected fun canSkysoftAddSlotToQuickCraft(
        screen: AbstractContainerScreen<*>,
        slot: Slot,
        stack: ItemStack,
        original: Operation<Boolean>,
    ): Boolean = SlotLockManager.canQuickCraftInto(slot) && original.call(screen, slot, stack)

    @WrapOperation(
        method = ["mouseReleased"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;" +
                    "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            ),
        ],
    )
    protected fun isSkysoftStorageOverlayWidgetReleaseHandled(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
        original: Operation<Boolean>,
    ): Boolean {
        if (StorageOverlayController.isActive(screen)) {
            return false
        }
        return original.call(screen, click)
    }

    @Inject(method = ["mouseScrolled"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftScrollStorageOverlay(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (
            ItemListController.handleMouseScroll(
                this as AbstractContainerScreen<*>,
                mouseX,
                mouseY,
                verticalAmount,
            ) == InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        if (
            StorageOverlayController.handleMouseScroll(
                this as AbstractContainerScreen<*>,
                mouseX,
                mouseY,
                verticalAmount,
            ) == InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftKeyStorageOverlay(
        event: KeyEvent,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (
            StorageOverlayController.handleKeyPress(this as AbstractContainerScreen<*>, event) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        if (
            ItemListController.handleKeyPress(this as AbstractContainerScreen<*>, event) ==
            InputHandlingResult.CONSUMED
        ) {
            cir.returnValue = true
            return
        }
        val screen = this as AbstractContainerScreen<*>
        val slotLockResult = SlotLockManager.handleKeyPress(screen, event)
        val itemProtectionResult = ItemProtectionManager.handleKeyPress(screen, event)
        if (slotLockResult == InputHandlingResult.CONSUMED || itemProtectionResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
        }
    }

    @WrapOperation(
        method = ["slotClicked"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;" +
                    "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;" +
                    "Lnet/minecraft/world/entity/player/Player;)V",
            ),
        ],
    )
    protected fun skysoftPreventSkyBlockMenuOpeningOnInventoryDrop(
        gameMode: MultiPlayerGameMode,
        containerId: Int,
        slotId: Int,
        button: Int,
        action: ContainerInput,
        player: Player,
        original: Operation<Void>,
    ) {
        if (BazaarTracker.shouldBlockOrderInteraction(this as AbstractContainerScreen<*>, slotId)) {
            return
        }
        val guard: InventoryDropSelectionGuard? = SkyBlockMenuInventoryDropFix.beginContainerThrow(player, slotId, action)
        try {
            original.call(gameMode, containerId, slotId, button, action, player)
        } finally {
            SkyBlockMenuInventoryDropFix.finishContainerThrow(guard)
        }
    }

    @Inject(method = ["hasClickedOutside"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftKeepStorageOverlayClicksInside(
        mouseX: Double,
        mouseY: Double,
        left: Int,
        top: Int,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (ItemListController.isClickInside(this as AbstractContainerScreen<*>, mouseX, mouseY)) {
            cir.returnValue = false
            return
        }
        if (StorageOverlayController.isClickInsideOverlay(this as AbstractContainerScreen<*>, mouseX, mouseY)) {
            cir.returnValue = false
        }
    }

    @Inject(method = ["slotClicked"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSlotClicked(
        slot: Slot?,
        slotId: Int,
        button: Int,
        action: ContainerInput,
        ci: CallbackInfo,
    ) {
        val screen = this as AbstractContainerScreen<*>
        val slotLockResult = SlotLockManager.handleSlotClick(screen, slot, button, action)
        val itemProtectionResult = ItemProtectionManager.handleContainerDrop(screen, slot, slotId, action)
        if (slotLockResult == InputHandlingResult.CONSUMED || itemProtectionResult == InputHandlingResult.CONSUMED) {
            ci.cancel()
            return
        }
        PetStorageService.onSlotClick(slot, slotId, button)
        if (
            SlotBindingManager.handleSlotClick(this as AbstractContainerScreen<*>, slot, action) ==
            InputHandlingResult.CONSUMED
        ) {
            ci.cancel()
        }
    }
}
