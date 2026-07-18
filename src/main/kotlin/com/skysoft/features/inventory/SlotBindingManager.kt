package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SlotBindingHighlightStyle
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockMenuItem.isSkyBlockMenu
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.ColorUtilities.hasVisibleAlpha
import com.skysoft.utils.ColorUtilities.toPackedArgb
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.Point
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import kotlin.math.abs
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot

internal fun registerSlotBindingStorage() {
    ProfileStorageApi.registerConsumer("Slot Bindings") {
        SkysoftConfigGui.config().inventory.slotBindings.enabled
    }
}

object SlotBindingManager {
    private const val SLOT_CENTER_OFFSET = 8
    private const val SLOT_HIT_PADDING = 1
    private const val SLOT_HIT_SIZE = 18
    private const val SLOT_OUTLINE_SIZE = 18
    private const val HOTBAR_FIRST_SLOT = 0
    private const val HOTBAR_LAST_SLOT = 8
    private const val ARMOR_LAST_SLOT = 39

    private const val PIXEL_SIZE = 1
    private const val SUBPIXEL_CENTER = 0.5
    private const val FILL_ALPHA_SCALE = 0.25
    private const val LINE_ALPHA_SCALE = 0.80
    private const val WHITE_FILL = 0x50FFFFFF
    private const val WHITE_OUTLINE = 0xFFFFFFFF.toInt()
    private const val WHITE_LINE = 0xDDFFFFFF.toInt()
    private val config get() = SkysoftConfigGui.config().inventory.slotBindings
    private val bindings get() = ProfileStorageApi.storage.slotBindings

    private var dragState: DragState? = null
    private var bindingKeyWasDown = false
    private var activeContainerId: Int? = null
    private var pendingTooltip: PendingTooltip? = null

    @JvmStatic
    fun handleSlotClick(screen: AbstractContainerScreen<*>, slot: Slot?, action: ContainerInput): InputHandlingResult {
        if (!isAvailable() || action != ContainerInput.QUICK_MOVE || slot == null || !Geometry.isPlayerInventorySlot(slot)) {
            return InputHandlingResult.IGNORED
        }
        repairBindings(screen)
        val binding = bindingFor(slot.containerSlot) ?: return InputHandlingResult.IGNORED
        val firstSlot = Geometry.findPlayerSlot(screen, binding.firstSlot) ?: return InputHandlingResult.IGNORED
        val secondSlot = Geometry.findPlayerSlot(screen, binding.secondSlot) ?: return InputHandlingResult.IGNORED
        return if (trySwapSlots(screen, firstSlot, secondSlot)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
    }

    @JvmStatic
    fun render(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        pendingTooltip = null
        val hoveredSlot = Geometry.playerSlotAt(screen, mouseX, mouseY)
        updateDragState(screen, hoveredSlot)
        if (!isAvailable()) return
        repairBindings(screen)

        if (config.details.showHighlights) {
            val slotPairs = bindings.mapNotNull { binding ->
                val firstSlot = Geometry.findPlayerSlot(screen, binding.firstSlot) ?: return@mapNotNull null
                val secondSlot = Geometry.findPlayerSlot(screen, binding.secondSlot) ?: return@mapNotNull null
                firstSlot to secondSlot
            }

            val hoveredBinding = if (config.details.showShiftHoverHighlight && isShiftDown() && hoveredSlot != null) {
                bindingFor(hoveredSlot.containerSlot)
            } else {
                null
            }

            for ((firstSlot, secondSlot) in slotPairs) {
                val highlighted = hoveredBinding != null &&
                    Geometry.bindingContains(hoveredBinding, firstSlot.containerSlot) &&
                    Geometry.bindingContains(hoveredBinding, secondSlot.containerSlot)
                Renderer.drawBinding(context, screen, firstSlot, secondSlot, if (highlighted) WHITE_LINE else Renderer.bindingLineColor())
                Renderer.drawSlotHighlight(context, screen, firstSlot, Renderer.bindingFillColor(), Renderer.bindingOutlineColor())
                Renderer.drawSlotHighlight(context, screen, secondSlot, Renderer.bindingFillColor(), Renderer.bindingOutlineColor())
            }

            if (hoveredBinding != null && hoveredSlot != null) {
                Geometry.otherSlot(hoveredBinding, hoveredSlot.containerSlot)?.let { otherSlotIndex ->
                    Geometry.findPlayerSlot(screen, otherSlotIndex)?.let { otherSlot ->
                        Renderer.drawSlotHighlight(context, screen, otherSlot, Renderer.whiteFillColor(), WHITE_OUTLINE)
                    }
                }
            }

            if (dragState == null && isBindingKeyDown()) {
                hoveredSlot?.takeUnless(Geometry::isSkyBlockMenuSlot)?.let { slot ->
                    val color = if (bindingFor(slot.containerSlot) != null) WHITE_OUTLINE else Renderer.bindingOutlineColor()
                    Renderer.drawSlotHighlight(context, screen, slot, Renderer.bindingFillColor(), color)
                }
            }
        }

        if (
            dragState == null &&
            isBindingKeyDown() &&
            hoveredSlot != null &&
            bindingFor(hoveredSlot.containerSlot) == null &&
            Geometry.isSkyBlockMenuSlot(hoveredSlot)
        ) {
            pendingTooltip = PendingTooltip(Tooltips.skyBlockMenuBindingTooltipLines(), mouseX, mouseY)
        }

        renderDraggingBinding(screen, context, mouseX, mouseY, hoveredSlot)
    }

    @JvmStatic
    fun renderTopLayer(context: GuiGraphicsExtractor) {
        val tooltip = pendingTooltip ?: return
        pendingTooltip = null
        context.nextStratum()
        val (mouseX, mouseY) = OverlayControlMouse.deferredTooltipPoint(tooltip.mouseX, tooltip.mouseY)
        SkysoftNativeTooltip.setForNextFrame(context, tooltip.lines, mouseX, mouseY)
    }

    @JvmStatic
    fun shouldSuppressRegularTooltips(screen: AbstractContainerScreen<*>): Boolean =
        isAvailable() && (isBindingKeyDown() || dragState?.containerId == screen.menu.containerId)

    @JvmStatic
    fun canHandleBindingKey(key: Int): Boolean = isAvailable() && key == config.settings.bindingKey

    @JvmStatic
    fun resetAllBindings() {
        val removed = bindings.isNotEmpty()
        bindings.clear()
        resetInputState()
        if (removed) ProfileStorageApi.markDirty()
    }

    private fun updateDragState(screen: AbstractContainerScreen<*>, hoveredSlot: Slot?) {
        if (!isAvailable()) {
            resetInputState()
            return
        }

        val containerId = screen.menu.containerId
        if (activeContainerId != containerId) {
            resetInputState()
            activeContainerId = containerId
        }

        val bindingKeyDown = isBindingKeyDown()
        if (bindingKeyDown && !bindingKeyWasDown) {
            startBindingKeyAction(screen, hoveredSlot)
        } else if (!bindingKeyDown && bindingKeyWasDown) {
            finishDrag(screen, hoveredSlot)
        }
        bindingKeyWasDown = bindingKeyDown
    }

    private fun startBindingKeyAction(screen: AbstractContainerScreen<*>, hoveredSlot: Slot?) {
        if (hoveredSlot == null) {
            dragState = null
            return
        }

        val hoveredSlotIndex = hoveredSlot.containerSlot
        if (bindingFor(hoveredSlotIndex) != null) {
            SlotLockManager.cancelPendingLock()
            removeBindingsInvolving(hoveredSlotIndex)
            dragState = null
            return
        }
        if (Geometry.isSkyBlockMenuSlot(hoveredSlot)) {
            dragState = null
            return
        }

        dragState = DragState(screen.menu.containerId, hoveredSlotIndex)
    }

    private fun finishDrag(screen: AbstractContainerScreen<*>, targetSlot: Slot?) {
        val drag = dragState ?: return
        dragState = null
        if (!shouldKeepPendingSlotLock(drag.containerId, screen.menu.containerId, drag.sourceSlot, targetSlot?.containerSlot)) {
            SlotLockManager.cancelPendingLock()
        }
        if (drag.containerId != screen.menu.containerId || !isAvailable()) return
        val sourceSlot = Geometry.findPlayerSlot(screen, drag.sourceSlot) ?: return
        if (targetSlot != null && targetSlot.containerSlot != sourceSlot.containerSlot) {
            bindSlots(sourceSlot, targetSlot)
        }
    }

    private fun resetInputState() {
        dragState = null
        bindingKeyWasDown = false
        activeContainerId = null
    }

    private fun bindSlots(firstSlot: Slot, secondSlot: Slot) {
        if (!Geometry.isValidBindingPair(firstSlot, secondSlot)) {
            return
        }
        val firstSlotIndex = firstSlot.containerSlot
        val secondSlotIndex = secondSlot.containerSlot
        var changed = false
        val iterator = bindings.iterator()
        while (iterator.hasNext()) {
            val binding = iterator.next()
            if (Geometry.bindingMatches(binding, firstSlotIndex, secondSlotIndex)) return
            if (Geometry.bindingContains(binding, firstSlotIndex) || Geometry.bindingContains(binding, secondSlotIndex)) {
                iterator.remove()
                changed = true
            }
        }
        bindings.add(ProfileStorage.SlotBindingData(firstSlotIndex, secondSlotIndex))
        if (changed || bindings.isNotEmpty()) ProfileStorageApi.markDirty()
    }

    private fun removeBindingsInvolving(slotIndex: Int) {
        val removed = bindings.removeIf { Geometry.bindingContains(it, slotIndex) }
        if (removed) {
            ProfileStorageApi.markDirty()
        }
    }

    private fun bindingFor(slotIndex: Int): ProfileStorage.SlotBindingData? =
        bindings.firstOrNull { Geometry.bindingContains(it, slotIndex) }

    private fun repairBindings(screen: AbstractContainerScreen<*>? = null) {
        val usedSlots = mutableSetOf<Int>()
        val iterator = bindings.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val binding = iterator.next()
            if (
                !binding.isValid() ||
                !Geometry.isValidBindingPair(binding.firstSlot, binding.secondSlot) ||
                Geometry.involvesSkyBlockMenuSlot(binding, screen) ||
                binding.firstSlot in usedSlots ||
                binding.secondSlot in usedSlots
            ) {
                iterator.remove()
                changed = true
                continue
            }
            usedSlots += binding.firstSlot
            usedSlots += binding.secondSlot
        }
        if (changed) ProfileStorageApi.markDirty()
    }

    private fun trySwapSlots(screen: AbstractContainerScreen<*>, firstSlot: Slot, secondSlot: Slot): Boolean {
        val firstHotbar = Geometry.isHotbarSlot(firstSlot.containerSlot)
        val secondHotbar = Geometry.isHotbarSlot(secondSlot.containerSlot)
        if (!Geometry.isValidBindingPair(firstSlot, secondSlot)) return false
        return when {
            firstHotbar -> trySwapWithHotbar(screen, secondSlot, firstSlot.containerSlot)
            secondHotbar -> trySwapWithHotbar(screen, firstSlot, secondSlot.containerSlot)
            else -> false
        }
    }

    private fun trySwapWithHotbar(screen: AbstractContainerScreen<*>, slot: Slot, hotbarSlot: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        val gameMode = minecraft.gameMode ?: return false
        gameMode.handleContainerInput(
            screen.menu.containerId,
            slot.index,
            hotbarSlot,
            ContainerInput.SWAP,
            player,
        )
        return true
    }

    private fun renderDraggingBinding(
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        hoveredSlot: Slot?,
    ) {
        val drag = dragState ?: return
        if (drag.containerId != screen.menu.containerId || !isBindingKeyDown()) return
        if (SlotLockManager.isSlotLockPending(drag.sourceSlot) && hoveredSlot?.containerSlot == drag.sourceSlot) return
        val source = Geometry.findPlayerSlot(screen, drag.sourceSlot) ?: return
        val target = hoveredSlot?.takeIf { it.containerSlot != drag.sourceSlot }
        val invalidReason = target?.let { Tooltips.invalidBindingReason(source, it) }

        if (config.details.showHighlights) {
            Renderer.drawSlotHighlight(context, screen, source, Renderer.bindingFillColor(), Renderer.bindingOutlineColor())
            if (target != null && invalidReason == null) {
                Renderer.drawSlotHighlight(context, screen, target, Renderer.bindingFillColor(), Renderer.bindingOutlineColor())
                Renderer.drawBinding(context, screen, source, target, Renderer.bindingLineColor())
            } else {
                val sourceCenter = Geometry.slotCenter(screen, source)
                Renderer.drawLine(context, sourceCenter.x, sourceCenter.y, mouseX, mouseY, Renderer.bindingLineColor())
            }
        }

        if (invalidReason != null) {
            pendingTooltip = PendingTooltip(Tooltips.invalidBindingTooltipLines(invalidReason), mouseX, mouseY)
        }
    }

    private fun isAvailable(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    private fun isBindingKeyDown(): Boolean = InputUtilities.isKeyDown(config.settings.bindingKey)

    private fun isShiftDown(): Boolean = InputUtilities.isShiftDown()

    private object Tooltips {
        fun invalidBindingReason(source: Slot, target: Slot): InvalidBindingReason? = when {
            Geometry.isSkyBlockMenuSlot(source) || Geometry.isSkyBlockMenuSlot(target) -> InvalidBindingReason.SKYBLOCK_MENU
            !Geometry.isValidBindingPair(source.containerSlot, target.containerSlot) -> InvalidBindingReason.HOTBAR_REQUIRED
            else -> null
        }

        fun invalidBindingTooltipLines(reason: InvalidBindingReason): List<String> = when (reason) {
            InvalidBindingReason.HOTBAR_REQUIRED -> listOf(
                "§cInvalid Slot Binding",
                "§7Pick a §ehotbar slot§7.",
                "§7At least one side must be hotbar.",
            )

            InvalidBindingReason.SKYBLOCK_MENU -> skyBlockMenuBindingTooltipLines()
        }

        fun skyBlockMenuBindingTooltipLines(): List<String> = listOf(
            "§cInvalid Slot Binding",
            "§7The §eSkyBlock Menu §7slot can't be bound.",
            "§7Pick a different slot.",
        )
    }

    private object Renderer {
        fun bindingOutlineColor(): Int = bindingColor(alphaScale = 1.0)

        fun bindingLineColor(): Int = bindingColor(alphaScale = LINE_ALPHA_SCALE)

        fun bindingFillColor(): Int =
            if (config.details.highlightStyle == SlotBindingHighlightStyle.FILL) {
                bindingColor(alphaScale = FILL_ALPHA_SCALE)
            } else {
                0
            }

        fun whiteFillColor(): Int =
            if (config.details.highlightStyle == SlotBindingHighlightStyle.FILL) WHITE_FILL else 0

        fun drawBinding(
            context: GuiGraphicsExtractor,
            screen: AbstractContainerScreen<*>,
            firstSlot: Slot,
            secondSlot: Slot,
            color: Int,
        ) {
            val first = Geometry.slotCenter(screen, firstSlot)
            val second = Geometry.slotCenter(screen, secondSlot)
            drawLine(context, first.x, first.y, second.x, second.y, color)
        }

        fun drawSlotHighlight(
            context: GuiGraphicsExtractor,
            screen: AbstractContainerScreen<*>,
            slot: Slot,
            fillColor: Int,
            outlineColor: Int,
        ) {
            val position = Geometry.slotTopLeft(screen, slot)
            if (fillColor.hasVisibleAlpha()) {
                context.fill(
                    position.x - 1,
                    position.y - 1,
                    position.x + SLOT_OUTLINE_SIZE - 1,
                    position.y + SLOT_OUTLINE_SIZE - 1,
                    fillColor,
                )
            }
            context.outline(position.x - 1, position.y - 1, SLOT_OUTLINE_SIZE, SLOT_OUTLINE_SIZE, outlineColor)
        }

        fun drawLine(context: GuiGraphicsExtractor, startX: Int, startY: Int, endX: Int, endY: Int, color: Int) {
            if (startX == endX && startY == endY) {
                drawPixel(context, startX, startY, color, 1.0)
                return
            }

            var x0 = startX.toDouble()
            var y0 = startY.toDouble()
            var x1 = endX.toDouble()
            var y1 = endY.toDouble()
            val steep = abs(y1 - y0) > abs(x1 - x0)
            if (steep) {
                val oldX0 = x0
                x0 = y0
                y0 = oldX0
                val oldX1 = x1
                x1 = y1
                y1 = oldX1
            }
            if (x0 > x1) {
                val oldX0 = x0
                x0 = x1
                x1 = oldX0
                val oldY0 = y0
                y0 = y1
                y1 = oldY0
            }

            val dx = x1 - x0
            val gradient = if (dx == 0.0) 1.0 else (y1 - y0) / dx
            drawLineEndpoints(context, steep, color, LineValues(x0, y0, x1, y1, gradient))
        }

        private fun bindingColor(alphaScale: Double): Int {
            val color = config.details.highlightColor.get().toColor()
            return color.toPackedArgb(alphaScale)
        }

        private fun drawLineEndpoints(
            context: GuiGraphicsExtractor,
            steep: Boolean,
            color: Int,
            values: LineValues,
        ) {
            val xEnd1 = roundLineCoordinate(values.x0)
            val yEnd1 = values.y0 + values.gradient * (xEnd1 - values.x0)
            val xGap1 = reverseFractionalPart(values.x0 + SUBPIXEL_CENTER)
            val xPixel1 = xEnd1.toInt()
            val yPixel1 = integerPart(yEnd1)
            plotLinePixel(context, steep, xPixel1, yPixel1, color, reverseFractionalPart(yEnd1) * xGap1)
            plotLinePixel(context, steep, xPixel1, yPixel1 + 1, color, fractionalPart(yEnd1) * xGap1)

            val xEnd2 = roundLineCoordinate(values.x1)
            val yEnd2 = values.y1 + values.gradient * (xEnd2 - values.x1)
            val xGap2 = fractionalPart(values.x1 + SUBPIXEL_CENTER)
            val xPixel2 = xEnd2.toInt()
            val yPixel2 = integerPart(yEnd2)
            plotLinePixel(context, steep, xPixel2, yPixel2, color, reverseFractionalPart(yEnd2) * xGap2)
            plotLinePixel(context, steep, xPixel2, yPixel2 + 1, color, fractionalPart(yEnd2) * xGap2)

            var interY = yEnd1 + values.gradient
            for (x in (xPixel1 + 1) until xPixel2) {
                val y = integerPart(interY)
                plotLinePixel(context, steep, x, y, color, reverseFractionalPart(interY))
                plotLinePixel(context, steep, x, y + 1, color, fractionalPart(interY))
                interY += values.gradient
            }
        }

        private fun plotLinePixel(
            context: GuiGraphicsExtractor,
            steep: Boolean,
            x: Int,
            y: Int,
            color: Int,
            coverage: Double,
        ) {
            if (steep) drawPixel(context, y, x, color, coverage) else drawPixel(context, x, y, color, coverage)
        }

        private fun drawPixel(context: GuiGraphicsExtractor, x: Int, y: Int, color: Int, coverage: Double) {
            if (coverage <= 0.0) return
            context.fill(x, y, x + PIXEL_SIZE, y + PIXEL_SIZE, color.withScaledAlpha(coverage))
        }

        private fun integerPart(value: Double): Int = floor(value).toInt()

        private fun roundLineCoordinate(value: Double): Double = floor(value + SUBPIXEL_CENTER)

        private fun fractionalPart(value: Double): Double = value - floor(value)

        private fun reverseFractionalPart(value: Double): Double = 1.0 - fractionalPart(value)
    }

    private object Geometry {
        fun playerSlotAt(screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int): Slot? =
            screen.menu.slots.firstOrNull { slot ->
                isPlayerInventorySlot(slot) && containsPoint(screen, slot, mouseX, mouseY)
            }

        fun findPlayerSlot(screen: AbstractContainerScreen<*>, containerSlot: Int): Slot? =
            screen.menu.slots.firstOrNull { slot -> isPlayerInventorySlot(slot) && slot.containerSlot == containerSlot }

        fun isPlayerInventorySlot(slot: Slot): Boolean =
            slot.container is Inventory && slot.containerSlot in HOTBAR_FIRST_SLOT..ARMOR_LAST_SLOT

        fun slotTopLeft(screen: AbstractContainerScreen<*>, slot: Slot): Point {
            val accessor = screen as AbstractContainerScreenAccessor
            return Point(accessor.skysoftGetLeftPos() + slot.x, accessor.skysoftGetTopPos() + slot.y)
        }

        fun slotCenter(screen: AbstractContainerScreen<*>, slot: Slot): Point {
            val topLeft = slotTopLeft(screen, slot)
            return Point(topLeft.x + SLOT_CENTER_OFFSET, topLeft.y + SLOT_CENTER_OFFSET)
        }

        fun isValidBindingPair(firstSlot: Slot, secondSlot: Slot): Boolean =
            !isSkyBlockMenuSlot(firstSlot) &&
                !isSkyBlockMenuSlot(secondSlot) &&
                isValidBindingPair(firstSlot.containerSlot, secondSlot.containerSlot)

        fun isValidBindingPair(firstSlot: Int, secondSlot: Int): Boolean =
            firstSlot != secondSlot &&
                firstSlot in HOTBAR_FIRST_SLOT..ARMOR_LAST_SLOT &&
                secondSlot in HOTBAR_FIRST_SLOT..ARMOR_LAST_SLOT &&
                (isHotbarSlot(firstSlot) || isHotbarSlot(secondSlot))

        fun isHotbarSlot(slotIndex: Int): Boolean = slotIndex in HOTBAR_FIRST_SLOT..HOTBAR_LAST_SLOT

        fun isSkyBlockMenuSlot(slot: Slot): Boolean = slot.item.isSkyBlockMenu()

        fun bindingContains(binding: ProfileStorage.SlotBindingData, slotIndex: Int): Boolean =
            binding.firstSlot == slotIndex || binding.secondSlot == slotIndex

        fun bindingMatches(binding: ProfileStorage.SlotBindingData, firstSlot: Int, secondSlot: Int): Boolean =
            (binding.firstSlot == firstSlot && binding.secondSlot == secondSlot) ||
                (binding.firstSlot == secondSlot && binding.secondSlot == firstSlot)

        fun otherSlot(binding: ProfileStorage.SlotBindingData, slotIndex: Int): Int? = when (slotIndex) {
            binding.firstSlot -> binding.secondSlot
            binding.secondSlot -> binding.firstSlot
            else -> null
        }

        fun involvesSkyBlockMenuSlot(
            binding: ProfileStorage.SlotBindingData,
            screen: AbstractContainerScreen<*>?,
        ): Boolean {
            if (screen == null) return false
            return findPlayerSlot(screen, binding.firstSlot)?.let(::isSkyBlockMenuSlot) == true ||
                findPlayerSlot(screen, binding.secondSlot)?.let(::isSkyBlockMenuSlot) == true
        }

        private fun containsPoint(screen: AbstractContainerScreen<*>, slot: Slot, mouseX: Int, mouseY: Int): Boolean {
            val position = slotTopLeft(screen, slot)
            return mouseX in position.x - SLOT_HIT_PADDING until position.x - SLOT_HIT_PADDING + SLOT_HIT_SIZE &&
                mouseY in position.y - SLOT_HIT_PADDING until position.y - SLOT_HIT_PADDING + SLOT_HIT_SIZE
        }
    }

    private enum class InvalidBindingReason {
        HOTBAR_REQUIRED,
        SKYBLOCK_MENU,
    }

    private data class DragState(val containerId: Int, val sourceSlot: Int)
    private data class PendingTooltip(val lines: List<String>, val mouseX: Int, val mouseY: Int)
    private data class LineValues(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val gradient: Double)
}

internal fun shouldKeepPendingSlotLock(
    sourceContainerId: Int,
    currentContainerId: Int,
    sourceSlot: Int,
    targetSlot: Int?,
): Boolean = sourceContainerId == currentContainerId && sourceSlot == targetSlot
