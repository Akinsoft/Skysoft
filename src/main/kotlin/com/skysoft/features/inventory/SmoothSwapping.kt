package com.skysoft.features.inventory

import com.skysoft.config.DEFAULT_SMOOTH_SWAPPING_DURATION
import com.skysoft.config.MAX_SMOOTH_SWAPPING_SPEED
import com.skysoft.config.MIN_SMOOTH_SWAPPING_SPEED
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SmoothSwappingCurve
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.gui.itemWithDecorations
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object SmoothSwapping {
    private const val MAX_ANIMATIONS = 128
    private const val MIN_DURATION_MS = 60
    private const val MAX_DURATION_MS = 720

    private val config get() = SkysoftConfigGui.config().inventory.smoothSwapping

    private var activeScreenKey: ScreenKey? = null
    private val animations = mutableListOf<SlotAnimation>()
    private val suppressedSlots = mutableSetOf<Int>()

    fun register() {
        SkysoftClientEvents.onDisconnect("Smooth Swapping disconnect reset", ::clearTransientState)
        SkysoftClientEvents.onEndTick("Smooth Swapping tick") {
            if (MinecraftClient.screen() !is AbstractContainerScreen<*>) {
                clearTransientState()
            }
        }
    }

    @JvmStatic
    fun beginFrame(screen: AbstractContainerScreen<*>) {
        if (!isAvailable()) {
            clearTransientState()
            return
        }

        val now = System.currentTimeMillis()
        val screenKey = screenKey(screen)
        if (screenKey != activeScreenKey) {
            activeScreenKey = screenKey
            animations.clear()
            suppressedSlots.clear()
            return
        }

        expireAnimations(now)
        if (animations.isEmpty()) return

        updateAnimationTargets(snapshotSlots(screen))
        refreshSuppressedSlots(now)
    }

    internal fun animateLocalContainerMutation(
        screen: AbstractContainerScreen<*>,
        mutation: () -> Unit,
    ) {
        if (!isAvailable() || activeScreenKey != screenKey(screen)) {
            mutation()
            return
        }

        val previousSlots = snapshotSlots(screen)
        mutation()
        if (activeScreenKey != screenKey(screen)) return

        val now = System.currentTimeMillis()
        val currentSlots = snapshotSlots(screen)
        expireAnimations(now)
        updateAnimationTargets(currentSlots)
        enqueueAnimations(previousSlots, currentSlots, now)
        refreshSuppressedSlots(now)
    }

    @JvmStatic
    fun render(
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
    ) {
        if (!isAvailable() || activeScreenKey != screenKey(screen) || animations.isEmpty()) return

        val now = System.currentTimeMillis()
        expireAnimations(now)
        if (animations.isEmpty()) return

        context.nextStratum()
        for (animation in animations) {
            val progress = easedProgress(animation, now)
            val x = lerp(animation.fromX, animation.toX, progress).roundToInt()
            val y = lerp(animation.fromY, animation.toY, progress).roundToInt()
            context.itemWithDecorations(animation.stack, x, y)
        }
    }

    @JvmStatic
    fun shouldSuppressSlot(screen: AbstractContainerScreen<*>, slot: Slot?): Boolean {
        if (slot == null || !isAvailable() || activeScreenKey != screenKey(screen)) return false
        return slot.index in suppressedSlots
    }

    private fun isAvailable(): Boolean = config.enabled

    private fun clearTransientState() {
        activeScreenKey = null
        animations.clear()
        suppressedSlots.clear()
    }

    private fun snapshotSlots(screen: AbstractContainerScreen<*>): Map<Int, SlotSnapshot> {
        val accessor = screen as AbstractContainerScreenAccessor
        val left = accessor.skysoftGetLeftPos()
        val top = accessor.skysoftGetTopPos()
        return screen.menu.slots.asSequence()
            .filter { it.isActive }
            .map { slot ->
                val stack = slot.item
                SlotSnapshot(
                    slotId = slot.index,
                    x = left + slot.x,
                    y = top + slot.y,
                    stack = if (stack.isEmpty) ItemStack.EMPTY else stack.copy(),
                )
            }
            .associateBy { it.slotId }
    }

    private fun enqueueAnimations(
        previous: Map<Int, SlotSnapshot>,
        current: Map<Int, SlotSnapshot>,
        now: Long,
    ) {
        if (previous.isEmpty() || current.isEmpty()) return

        val usedSources = mutableSetOf<Int>()
        val destinations = current.values
            .filter { !it.stack.isEmpty }
            .filter { destination -> !sameStack(previous[destination.slotId]?.stack ?: ItemStack.EMPTY, destination.stack) }

        for (destination in destinations) {
            val previousDestination = previous[destination.slotId]?.stack ?: ItemStack.EMPTY
            val change = destinationChange(previousDestination, destination.stack) ?: continue
            val source = findSource(previous, current, destination, usedSources) ?: continue
            if (!source.hasUsablePosition() || !destination.hasUsablePosition()) continue

            usedSources += source.slotId
            val stack = destination.stack.copyWithCount(change.count.coerceAtMost(source.stack.count).coerceAtLeast(1))
            addAnimation(source, destination, stack, change.suppressDestination, now)
        }
    }

    private fun destinationChange(previous: ItemStack, current: ItemStack): DestinationChange? {
        if (current.isEmpty) return null
        if (previous.isEmpty) return DestinationChange(current.count, suppressDestination = true)
        if (!ItemStack.isSameItemSameComponents(previous, current)) {
            return DestinationChange(current.count, suppressDestination = true)
        }

        val gained = current.count - previous.count
        return if (gained > 0) DestinationChange(gained, suppressDestination = false) else null
    }

    private fun findSource(
        previous: Map<Int, SlotSnapshot>,
        current: Map<Int, SlotSnapshot>,
        destination: SlotSnapshot,
        usedSources: Set<Int>,
    ): SlotSnapshot? = previous.values
        .asSequence()
        .filter { source -> source.slotId != destination.slotId }
        .filter { source -> source.slotId !in usedSources }
        .filter { source -> !source.stack.isEmpty }
        .filter { source -> ItemStack.isSameItemSameComponents(source.stack, destination.stack) }
        .filter { source -> sourceChangedOrReduced(source, current[source.slotId]?.stack ?: ItemStack.EMPTY) }
        .minWithOrNull(
            compareBy<SlotSnapshot> { source -> if (source.stack.count == destination.stack.count) 0 else 1 }
                .thenBy { source -> slotDistance(source, destination) },
        )

    private fun sourceChangedOrReduced(previous: SlotSnapshot, currentStack: ItemStack): Boolean {
        if (currentStack.isEmpty) return true
        if (!ItemStack.isSameItemSameComponents(previous.stack, currentStack)) return true
        return currentStack.count < previous.stack.count
    }

    private fun addAnimation(
        source: SlotSnapshot,
        destination: SlotSnapshot,
        stack: ItemStack,
        suppressDestination: Boolean,
        now: Long,
    ) {
        if (source.x == destination.x && source.y == destination.y) return
        animations.removeIf { it.destinationSlotId == destination.slotId }
        while (animations.size >= MAX_ANIMATIONS) {
            animations.removeAt(0)
        }
        animations += SlotAnimation(
            destinationSlotId = destination.slotId,
            stack = stack,
            fromX = source.x.toFloat(),
            fromY = source.y.toFloat(),
            toX = destination.x.toFloat(),
            toY = destination.y.toFloat(),
            startedMillis = now,
            durationMillis = animationDurationMillis(),
            suppressDestination = suppressDestination,
        )
    }

    private fun updateAnimationTargets(current: Map<Int, SlotSnapshot>) {
        animations.forEach { animation ->
            current[animation.destinationSlotId]?.let { destination ->
                animation.toX = destination.x.toFloat()
                animation.toY = destination.y.toFloat()
            }
        }
    }

    private fun refreshSuppressedSlots(now: Long) {
        suppressedSlots.clear()
        animations.asSequence()
            .filter { it.suppressDestination }
            .filter { animationProgress(it, now) < 1.0 }
            .mapTo(suppressedSlots) { it.destinationSlotId }
    }

    private fun expireAnimations(now: Long) {
        animations.removeIf { animationProgress(it, now) >= 1.0 }
        suppressedSlots.removeIf { slotId -> animations.none { it.destinationSlotId == slotId && it.suppressDestination } }
    }

    private fun easedProgress(animation: SlotAnimation, now: Long): Double =
        when (config.details.animationCurve) {
            SmoothSwappingCurve.LINEAR -> animationProgress(animation, now)
            SmoothSwappingCurve.EASE_OUT -> EasingUtilities.easeOutCubic(animationProgress(animation, now))

            SmoothSwappingCurve.EASE_IN_OUT -> EasingUtilities.smoothStep(animationProgress(animation, now))
        }

    private fun animationProgress(animation: SlotAnimation, now: Long): Double =
        ((now - animation.startedMillis).toDouble() / animation.durationMillis).coerceIn(0.0, 1.0)

    private fun animationDurationMillis(): Int {
        val speed = config.settings.animationSpeed.coerceIn(
            MIN_SMOOTH_SWAPPING_SPEED,
            MAX_SMOOTH_SWAPPING_SPEED,
        )
        return (DEFAULT_SMOOTH_SWAPPING_DURATION * SPEED_PERCENT_SCALE / speed)
            .roundToInt()
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

}

private const val OFFSCREEN_THRESHOLD = 10_000
private const val SPEED_PERCENT_SCALE = 100.0

private fun sameStack(first: ItemStack, second: ItemStack): Boolean =
    ItemStack.isSameItemSameComponents(first, second) && first.count == second.count

private fun slotDistance(first: SlotSnapshot, second: SlotSnapshot): Int =
    abs(first.x - second.x) + abs(first.y - second.y)

private fun SlotSnapshot.hasUsablePosition(): Boolean =
    abs(x) < OFFSCREEN_THRESHOLD && abs(y) < OFFSCREEN_THRESHOLD

private fun screenKey(screen: AbstractContainerScreen<*>): ScreenKey =
    ScreenKey(
        containerId = screen.menu.containerId,
        screenClass = screen.javaClass.name,
        title = screen.title.string,
        slotCount = screen.menu.slots.size,
    )

private fun lerp(start: Float, end: Float, progress: Double): Float =
    (start + (end - start) * progress).toFloat()

private data class ScreenKey(
    val containerId: Int,
    val screenClass: String,
    val title: String,
    val slotCount: Int,
)

private data class SlotSnapshot(
    val slotId: Int,
    val x: Int,
    val y: Int,
    val stack: ItemStack,
)

private data class DestinationChange(
    val count: Int,
    val suppressDestination: Boolean,
)

private data class SlotAnimation(
    val destinationSlotId: Int,
    val stack: ItemStack,
    val fromX: Float,
    val fromY: Float,
    var toX: Float,
    var toY: Float,
    val startedMillis: Long,
    val durationMillis: Int,
    val suppressDestination: Boolean,
)
