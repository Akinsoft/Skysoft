package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryDropSelectionGuard
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.MinisterCalendarTooltip
import com.skysoft.features.inventory.SkyBlockMenuInventoryDropFix
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.pets.PetStorageService
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer
import com.skysoft.utils.SkysoftErrorBoundary
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
        val screen = this as AbstractContainerScreen<*>
        SkysoftErrorBoundary.run("Storage Overlay screen layout") { StorageOverlayController.layoutScreen(screen) }
        SkysoftErrorBoundary.run("Inventory Equipment screen layout") { InventoryEquipment.layoutScreen(screen) }
    }

    @Inject(method = ["removed"], at = [At("TAIL")])
    protected fun skysoftRestoreInventoryEquipmentLayout(ci: CallbackInfo) {
        val screen = this as AbstractContainerScreen<*>
        SkysoftErrorBoundary.run("Bazaar Tracker screen cleanup") { BazaarTracker.restoreOrderMenu(screen) }
        SkysoftErrorBoundary.run("Inventory Equipment screen cleanup") { InventoryEquipment.restoreScreen(screen) }
        SkysoftErrorBoundary.run("Slot Lock screen cleanup", SlotLockManager::clearInputState)
        SkysoftErrorBoundary.run("Item Protection screen cleanup", ItemProtectionManager::clearInputState)
    }

    @Inject(method = ["extractTooltip"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSuppressTooltipDuringSlotBinding(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        AdjacentTooltipRenderer.clear()
        val screen = this as AbstractContainerScreen<*>
        val shouldSuppress = SkysoftErrorBoundary.value("Slot Binding tooltip suppression", false) {
            SlotBindingManager.shouldSuppressRegularTooltips(screen)
        }
        if (shouldSuppress) {
            ci.cancel()
            return
        }
        SkysoftErrorBoundary.run("Minister in Calendar tooltip preparation") {
            MinisterCalendarTooltip.prepare(screen, context)
        }
    }

    @Inject(method = ["extractLabels"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSuppressStorageOverlayLabels(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        val shouldSuppress = SkysoftErrorBoundary.value("Storage Overlay label suppression", false) {
            StorageOverlayController.shouldSuppressContainerLabels(this as AbstractContainerScreen<*>)
        }
        if (shouldSuppress) {
            ci.cancel()
        }
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftClickStorageOverlay(
        click: MouseButtonEvent,
        doubled: Boolean,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        val screen = this as AbstractContainerScreen<*>
        val itemListResult = SkysoftErrorBoundary.value("Item List mouse click", InputHandlingResult.IGNORED) {
            ItemListController.handleMouseClick(screen, click, doubled)
        }
        if (itemListResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val storageResult = SkysoftErrorBoundary.value("Storage Overlay mouse click", InputHandlingResult.IGNORED) {
            StorageOverlayController.handleMouseClick(screen, click)
        }
        if (storageResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val bazaarResult = SkysoftErrorBoundary.value("Bazaar Tracker screen click", InputHandlingResult.IGNORED) {
            BazaarTracker.handleMouseClick(screen, click)
        }
        if (bazaarResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val equipmentResult = SkysoftErrorBoundary.value("Inventory Equipment mouse click", InputHandlingResult.IGNORED) {
            InventoryEquipment.handleMouseClick(screen, click)
        }
        if (equipmentResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val buttonResult = SkysoftErrorBoundary.value("Inventory Button mouse click", InputHandlingResult.IGNORED) {
            InventoryButtonManager.handleMouseClick(screen, click)
        }
        if (buttonResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["mouseReleased"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftReleaseOverlayInput(
        click: MouseButtonEvent,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        val storageResult = SkysoftErrorBoundary.value("Storage Overlay mouse release", InputHandlingResult.IGNORED) {
            StorageOverlayController.handleMouseRelease(click)
        }
        if (storageResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val buttonResult = SkysoftErrorBoundary.value("Inventory Button mouse release", InputHandlingResult.IGNORED) {
            InventoryButtonManager.handleMouseRelease(this as AbstractContainerScreen<*>, click)
        }
        if (buttonResult == InputHandlingResult.CONSUMED) {
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
        val result = SkysoftErrorBoundary.value("Storage Overlay mouse drag", InputHandlingResult.IGNORED) {
            StorageOverlayController.handleMouseDrag(this as AbstractContainerScreen<*>, click)
        }
        if (result == InputHandlingResult.CONSUMED) {
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
        val isActive = SkysoftErrorBoundary.value("Storage Overlay widget click", false) {
            StorageOverlayController.isActive(screen)
        }
        if (isActive) return false
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
        val isActive = SkysoftErrorBoundary.value("Storage Overlay widget drag", false) {
            StorageOverlayController.isActive(screen)
        }
        if (isActive) return false
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
    ): Boolean {
        val isAllowed = SkysoftErrorBoundary.value("Slot Lock quick craft", true) {
            SlotLockManager.canQuickCraftInto(slot)
        }
        return isAllowed && original.call(screen, slot, stack)
    }

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
        val isActive = SkysoftErrorBoundary.value("Storage Overlay widget release", false) {
            StorageOverlayController.isActive(screen)
        }
        if (isActive) return false
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
        val screen = this as AbstractContainerScreen<*>
        val itemListResult = SkysoftErrorBoundary.value("Item List mouse scroll", InputHandlingResult.IGNORED) {
            ItemListController.handleMouseScroll(
                screen,
                mouseX,
                mouseY,
                verticalAmount,
            )
        }
        if (itemListResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val storageResult = SkysoftErrorBoundary.value("Storage Overlay mouse scroll", InputHandlingResult.IGNORED) {
            StorageOverlayController.handleMouseScroll(
                screen,
                mouseX,
                mouseY,
                verticalAmount,
            )
        }
        if (storageResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftKeyStorageOverlay(
        event: KeyEvent,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        val screen = this as AbstractContainerScreen<*>
        val storageResult = SkysoftErrorBoundary.value("Storage Overlay key input", InputHandlingResult.IGNORED) {
            StorageOverlayController.handleKeyPress(screen, event)
        }
        if (storageResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val itemListResult = SkysoftErrorBoundary.value("Item List key input", InputHandlingResult.IGNORED) {
            ItemListController.handleKeyPress(screen, event)
        }
        if (itemListResult == InputHandlingResult.CONSUMED) {
            cir.returnValue = true
            return
        }
        val slotLockResult = SkysoftErrorBoundary.value("Slot Lock key input", InputHandlingResult.IGNORED) {
            SlotLockManager.handleKeyPress(screen, event)
        }
        val itemProtectionResult = SkysoftErrorBoundary.value("Item Protection key input", InputHandlingResult.IGNORED) {
            ItemProtectionManager.handleKeyPress(screen, event)
        }
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
        val shouldBlock = SkysoftErrorBoundary.value("Bazaar Tracker order interaction", false) {
            BazaarTracker.shouldBlockOrderInteraction(this as AbstractContainerScreen<*>, slotId)
        }
        if (shouldBlock) return
        val guard = SkysoftErrorBoundary.value<InventoryDropSelectionGuard?>("Inventory drop selection guard", null) {
            SkyBlockMenuInventoryDropFix.beginContainerThrow(player, slotId, action)
        }
        try {
            original.call(gameMode, containerId, slotId, button, action, player)
        } finally {
            SkysoftErrorBoundary.run("Inventory drop selection guard") {
                SkyBlockMenuInventoryDropFix.finishContainerThrow(guard)
            }
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
        val screen = this as AbstractContainerScreen<*>
        val isInsideItemList = SkysoftErrorBoundary.value("Item List outside click", false) {
            ItemListController.isClickInside(screen, mouseX, mouseY)
        }
        if (isInsideItemList) {
            cir.returnValue = false
            return
        }
        val isInsideStorage = SkysoftErrorBoundary.value("Storage Overlay outside click", false) {
            StorageOverlayController.isClickInsideOverlay(screen, mouseX, mouseY)
        }
        if (isInsideStorage) cir.returnValue = false
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
        val itemProtectionResult = SkysoftErrorBoundary.value("Item Protection slot click", InputHandlingResult.IGNORED) {
            ItemProtectionManager.handleContainerDrop(screen, slot, slotId, action)
        }
        if (itemProtectionResult == InputHandlingResult.CONSUMED) {
            ci.cancel()
            return
        }
        val slotBindingResult = SkysoftErrorBoundary.value("Slot Binding slot click", InputHandlingResult.IGNORED) {
            SlotBindingManager.handleSlotClick(screen, slot, action)
        }
        if (slotBindingResult == InputHandlingResult.CONSUMED) {
            SkysoftErrorBoundary.run("Pet Storage slot click") { PetStorageService.onSlotClick(slot, slotId, button) }
            ci.cancel()
            return
        }
        val slotLockResult = SkysoftErrorBoundary.value("Slot Lock slot click", InputHandlingResult.IGNORED) {
            SlotLockManager.handleSlotClick(screen, slot, button, action)
        }
        if (slotLockResult == InputHandlingResult.CONSUMED) {
            ci.cancel()
            return
        }
        SkysoftErrorBoundary.run("Pet Storage slot click") { PetStorageService.onSlotClick(slot, slotId, button) }
    }
}
