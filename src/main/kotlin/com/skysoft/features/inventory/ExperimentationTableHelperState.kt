package com.skysoft.features.inventory

internal enum class ExperimentationGame {
    CHRONOMATRON,
    ULTRASEQUENCER,
    SUPERPAIRS,
}

internal enum class ExperimentationPhase {
    MEMORY,
    INPUT,
    INACTIVE,
}

internal enum class ExperimentationSlotEmphasis {
    NEXT,
    UPCOMING,
    REMAINING,
}

internal data class ExperimentationSlotVisual(
    val label: String?,
    val emphasis: ExperimentationSlotEmphasis,
)

internal class ExperimentationTableHelperState {
    private var game: ExperimentationGame? = null
    private var phase = ExperimentationPhase.INACTIVE
    private var round: Int? = null
    private val chronomatronSequence = mutableListOf<String>()
    private val activeChronomatronSlots = mutableMapOf<Int, String>()
    private var chronomatronPlaybackIndex = 0
    private var chronomatronPlaybackValid = false
    private var chronomatronSequenceReady = false
    private val ultrasequencerMemory = sortedMapOf<Int, Int>()
    private var ultrasequencerSequence = emptyList<Int>()
    private var inputIndex = 0

    fun start(game: ExperimentationGame) {
        if (this.game == game) return
        clear()
        this.game = game
    }

    fun clear() {
        game = null
        phase = ExperimentationPhase.INACTIVE
        round = null
        chronomatronSequence.clear()
        activeChronomatronSlots.clear()
        chronomatronPlaybackIndex = 0
        chronomatronPlaybackValid = false
        chronomatronSequenceReady = false
        ultrasequencerMemory.clear()
        ultrasequencerSequence = emptyList()
        inputIndex = 0
    }

    fun updateRound(round: Int?) {
        this.round = round?.takeIf { it > 0 }
    }

    fun updatePhase(phase: ExperimentationPhase) {
        if (this.phase == phase) return
        this.phase = phase
        when (phase) {
            ExperimentationPhase.MEMORY -> beginMemoryPhase()
            ExperimentationPhase.INPUT -> beginInputPhase()
            ExperimentationPhase.INACTIVE -> {
                chronomatronSequenceReady = false
                ultrasequencerSequence = emptyList()
                inputIndex = 0
            }
        }
    }

    fun observeChronomatronSlot(slotId: Int, color: String?, active: Boolean) {
        if (game != ExperimentationGame.CHRONOMATRON) return
        if (!active || color == null) {
            activeChronomatronSlots.remove(slotId)
            return
        }
        observeActiveChronomatronSlot(slotId, color)
    }

    fun rememberUltrasequencerNumber(number: Int, slotId: Int) {
        if (game != ExperimentationGame.ULTRASEQUENCER || phase != ExperimentationPhase.MEMORY) return
        if (number > 0) ultrasequencerMemory[number] = slotId
    }

    fun click(slotId: Int, chronomatronColor: String?) {
        if (phase != ExperimentationPhase.INPUT) return
        when (game) {
            ExperimentationGame.CHRONOMATRON -> {
                if (chronomatronSequence.getOrNull(inputIndex) == chronomatronColor) inputIndex++
            }

            ExperimentationGame.ULTRASEQUENCER -> {
                if (ultrasequencerSequence.getOrNull(inputIndex) == slotId) inputIndex++
            }

            ExperimentationGame.SUPERPAIRS -> Unit
            null -> Unit
        }
    }

    fun visual(slotId: Int, chronomatronColor: String?): ExperimentationSlotVisual? =
        when (game) {
            ExperimentationGame.CHRONOMATRON -> chronomatronVisual(chronomatronColor)
            ExperimentationGame.ULTRASEQUENCER -> ultrasequencerVisual(slotId)
            ExperimentationGame.SUPERPAIRS -> null
            null -> null
        }

    private fun beginMemoryPhase() {
        activeChronomatronSlots.clear()
        chronomatronPlaybackIndex = 0
        chronomatronPlaybackValid = true
        chronomatronSequenceReady = false
        ultrasequencerMemory.clear()
        ultrasequencerSequence = emptyList()
        inputIndex = 0
    }

    private fun beginInputPhase() {
        chronomatronSequenceReady = chronomatronPlaybackValid &&
            chronomatronSequence.size == round &&
            chronomatronPlaybackIndex == round
        ultrasequencerSequence = ultrasequencerMemory
            .takeIf { memory -> memory.keys.toList() == (1..memory.size).toList() }
            ?.values
            ?.toList()
            .orEmpty()
        inputIndex = 0
    }

    private fun observeActiveChronomatronSlot(slotId: Int, color: String) {
        if (game != ExperimentationGame.CHRONOMATRON || phase != ExperimentationPhase.MEMORY) return
        if (activeChronomatronSlots[slotId] == color) return
        activeChronomatronSlots.remove(slotId)
        val isNewNote = color !in activeChronomatronSlots.values
        activeChronomatronSlots[slotId] = color
        if (!isNewNote || !chronomatronPlaybackValid) return

        val expectedColor = chronomatronSequence.getOrNull(chronomatronPlaybackIndex)
        when {
            expectedColor == color -> chronomatronPlaybackIndex++
            expectedColor != null -> chronomatronPlaybackValid = false
            chronomatronSequence.size < (round ?: return) -> {
                chronomatronSequence += color
                chronomatronPlaybackIndex++
            }
        }
    }

    private fun chronomatronVisual(color: String?): ExperimentationSlotVisual? {
        if (phase != ExperimentationPhase.INPUT || !chronomatronSequenceReady || color == null) return null
        val next = chronomatronSequence.getOrNull(inputIndex) ?: return null
        if (color == next) return ExperimentationSlotVisual(null, ExperimentationSlotEmphasis.NEXT)
        val upcoming = chronomatronSequence.getOrNull(inputIndex + 1)
        return if (color == upcoming) {
            ExperimentationSlotVisual(null, ExperimentationSlotEmphasis.UPCOMING)
        } else {
            null
        }
    }

    private fun ultrasequencerVisual(slotId: Int): ExperimentationSlotVisual? {
        if (phase != ExperimentationPhase.INPUT) return null
        val sequenceIndex = ultrasequencerSequence.indexOf(slotId)
        if (sequenceIndex < inputIndex) return null
        val emphasis = when (sequenceIndex) {
            inputIndex -> ExperimentationSlotEmphasis.NEXT
            inputIndex + 1 -> ExperimentationSlotEmphasis.UPCOMING
            else -> ExperimentationSlotEmphasis.REMAINING
        }
        return ExperimentationSlotVisual((sequenceIndex + 1).toString(), emphasis)
    }
}
