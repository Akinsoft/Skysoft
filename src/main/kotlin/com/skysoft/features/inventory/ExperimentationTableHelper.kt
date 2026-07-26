package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.nonPlayerSlots
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object ExperimentationTableHelper {
    private val state = ExperimentationTableHelperState()
    private val superpairsMemory = SuperpairsMemory<ItemStack>()
    private var activeScreen: AbstractContainerScreen<*>? = null
    private var currentGame: ExperimentationGame? = null
    private var experimentSlots = emptySet<Slot>()

    @JvmStatic
    fun beginFrame(screen: AbstractContainerScreen<*>) {
        val game = activeGame(screen)
        if (game == null) {
            detach()
            return
        }
        val slots = screen.nonPlayerSlots()
        val attached = activeScreen !== screen
        if (attached) {
            detach()
            activeScreen = screen
            experimentSlots = slots.toSet()
        }

        currentGame = game
        state.start(game)
        state.updateRound(slots.firstOrNull { it.index == ROUND_STATUS_SLOT }?.item?.chronomatronRound())
        val phase = slots.firstOrNull { it.index == PHASE_STATUS_SLOT }
            ?.item
            ?.experimentPhaseOrNull()
        phase?.let(state::updatePhase)
        when (game) {
            ExperimentationGame.CHRONOMATRON -> Unit

            ExperimentationGame.ULTRASEQUENCER -> slots.forEach { slot ->
                slot.item.ultrasequencerNumber()?.let { state.rememberUltrasequencerNumber(it, slot.index) }
            }

            ExperimentationGame.SUPERPAIRS -> {
                superpairsMemory.observeHiddenCards(
                    slots.asSequence()
                        .filter { it.item.isSuperpairsHiddenCard() }
                        .map(Slot::index)
                        .asIterable(),
                )
                slots.asSequence()
                    .filterNot { it.item.isEmpty || it.item.isSuperpairsHiddenCard() }
                    .forEach { slot ->
                        superpairsMemory.rememberRevealedCard(slot.index, slot.item::copy)
                    }
            }
        }
    }

    @JvmStatic
    fun displayStack(screen: AbstractContainerScreen<*>, slot: Slot?, stack: ItemStack): ItemStack {
        if (screen !== activeScreen || currentGame != ExperimentationGame.SUPERPAIRS) {
            return stack
        }
        val experimentSlot = slot?.takeIf { it in experimentSlots } ?: return stack
        if (!stack.isSuperpairsHiddenCard()) return stack
        return superpairsMemory.rememberedCard(experimentSlot.index) ?: stack
    }

    @JvmStatic
    fun renderSlot(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) {
        if (screen !== activeScreen || slot !in experimentSlots) return
        val visual = state.visual(slot.index, slot.item.chronomatronTile()?.color) ?: return
        val colors = visualColors(visual.emphasis)
        context.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, colors.fill)
        if (visual.emphasis != ExperimentationSlotEmphasis.REMAINING) {
            context.outline(
                slot.x - OUTLINE_INSET,
                slot.y - OUTLINE_INSET,
                OUTLINE_SIZE,
                OUTLINE_SIZE,
                colors.outline,
            )
        }
        val label = visual.label ?: return
        LegacyTextRenderer.draw(
            context,
            label,
            slot.x + (SLOT_SIZE - LegacyTextRenderer.width(label)) / 2,
            slot.y + LABEL_Y_OFFSET,
            defaultColor = colors.text,
        )
    }

    @JvmStatic
    fun onSlotClick(screen: AbstractContainerScreen<*>, slot: Slot?, action: ContainerInput) {
        if (screen !== activeScreen) return
        val experimentSlot = slot?.takeIf { it in experimentSlots } ?: return
        if (action != ContainerInput.PICKUP && action != ContainerInput.THROW) return
        state.click(experimentSlot.index, experimentSlot.item.chronomatronTile()?.color)
    }

    @JvmStatic
    fun onMenuSlotChanged(menu: AbstractContainerMenu, menuSlotId: Int, stack: ItemStack) {
        val screen = activeScreen?.takeIf { it.menu === menu } ?: return
        if (currentGame != ExperimentationGame.CHRONOMATRON) return
        val slot = menu.slots.getOrNull(menuSlotId)?.takeIf { it in experimentSlots } ?: return
        when (slot.index) {
            ROUND_STATUS_SLOT -> state.updateRound(stack.chronomatronRound())
            PHASE_STATUS_SLOT -> stack.experimentPhaseOrNull()?.let(state::updatePhase)
            else -> {
                val tile = stack.chronomatronTile() ?: return
                state.observeChronomatronSlot(slot.index, tile.color, tile.active)
            }
        }
    }

    @JvmStatic
    fun onScreenRemoved(screen: AbstractContainerScreen<*>) {
        if (screen === activeScreen) detach()
    }

    private fun activeGame(screen: AbstractContainerScreen<*>): ExperimentationGame? {
        if (!SkysoftConfigGui.config().inventory.isExperimentationTableHelperEnabled) return null
        if (!HypixelLocationState.inSkyBlock) return null
        return experimentationGameFromTitle(screen.title.string.cleanSkyBlockText())
    }

    private fun detach() {
        activeScreen = null
        currentGame = null
        experimentSlots = emptySet()
        superpairsMemory.clear()
        state.clear()
    }
}

internal fun experimentationGameFromTitle(title: String): ExperimentationGame? =
    when (EXPERIMENT_TITLE_PATTERN.matchEntire(title)?.groupValues?.get(1)) {
        "Chronomatron" -> ExperimentationGame.CHRONOMATRON
        "Ultrasequencer" -> ExperimentationGame.ULTRASEQUENCER
        "Superpairs" -> ExperimentationGame.SUPERPAIRS
        else -> null
    }

private fun ItemStack.experimentPhaseOrNull(): ExperimentationPhase? {
    val name = hoverName.string.cleanSkyBlockText()
    return when {
        name == MEMORY_PHASE_NAME -> ExperimentationPhase.MEMORY
        name.startsWith(INPUT_PHASE_PREFIX) -> ExperimentationPhase.INPUT
        else -> null
    }
}

private fun ItemStack.chronomatronRound(): Int? =
    ROUND_PATTERN.matchEntire(hoverName.string.cleanSkyBlockText())
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

private fun ItemStack.ultrasequencerNumber(): Int? =
    hoverName.string.cleanSkyBlockText().toIntOrNull()?.takeIf { it > 0 }

private fun ItemStack.chronomatronTile(): ChronomatronTile? {
    if (isEmpty) return null
    val path = BuiltInRegistries.ITEM.getKey(item).path
    val active = path.endsWith(TERRACOTTA_SUFFIX)
    val suffix = if (active) TERRACOTTA_SUFFIX else STAINED_GLASS_SUFFIX
    val color = path.removeSuffix(suffix).takeIf {
        path.endsWith(suffix) && it in CHRONOMATRON_COLORS
    } ?: return null
    return ChronomatronTile(color, active)
}

private fun ItemStack.isSuperpairsHiddenCard(): Boolean =
    !isEmpty && BuiltInRegistries.ITEM.getKey(item).path == SUPERPAIRS_HIDDEN_ITEM

private fun visualColors(emphasis: ExperimentationSlotEmphasis): ExperimentationVisualColors =
    when (emphasis) {
        ExperimentationSlotEmphasis.NEXT -> ExperimentationVisualColors(
            fill = NEXT_FILL_COLOR,
            outline = NEXT_OUTLINE_COLOR,
            text = NEXT_TEXT_COLOR,
        )

        ExperimentationSlotEmphasis.UPCOMING -> ExperimentationVisualColors(
            fill = UPCOMING_FILL_COLOR,
            outline = UPCOMING_OUTLINE_COLOR,
            text = UPCOMING_TEXT_COLOR,
        )

        ExperimentationSlotEmphasis.REMAINING -> ExperimentationVisualColors(
            fill = REMAINING_FILL_COLOR,
            outline = REMAINING_OUTLINE_COLOR,
            text = REMAINING_TEXT_COLOR,
        )
    }

private data class ChronomatronTile(val color: String, val active: Boolean)

private data class ExperimentationVisualColors(val fill: Int, val outline: Int, val text: Int)

private val EXPERIMENT_TITLE_PATTERN = Regex("""^(Chronomatron|Ultrasequencer|Superpairs) \([^)]+\)$""")
private val ROUND_PATTERN = Regex("""^Round: (\d+)$""")
private val CHRONOMATRON_COLORS = setOf(
    "red",
    "orange",
    "yellow",
    "lime",
    "green",
    "cyan",
    "light_blue",
    "blue",
    "purple",
    "pink",
)

private const val ROUND_STATUS_SLOT = 4
private const val PHASE_STATUS_SLOT = 49
private const val MEMORY_PHASE_NAME = "Remember the pattern!"
private const val INPUT_PHASE_PREFIX = "Timer: "
private const val TERRACOTTA_SUFFIX = "_terracotta"
private const val STAINED_GLASS_SUFFIX = "_stained_glass"
private const val SUPERPAIRS_HIDDEN_ITEM = "cyan_stained_glass"
private const val SLOT_SIZE = 16
private const val OUTLINE_INSET = 1
private const val OUTLINE_SIZE = 18
private const val LABEL_Y_OFFSET = 4
private const val NEXT_FILL_COLOR = 0x9030FF30.toInt()
private const val NEXT_OUTLINE_COLOR = 0xFF30FF30.toInt()
private const val NEXT_TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val UPCOMING_FILL_COLOR = 0x80FFD740.toInt()
private const val UPCOMING_OUTLINE_COLOR = 0xFFFFD740.toInt()
private const val UPCOMING_TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val REMAINING_FILL_COLOR = 0xB0101010.toInt()
private const val REMAINING_OUTLINE_COLOR = 0x00000000
private const val REMAINING_TEXT_COLOR = 0xFFFFFFFF.toInt()
