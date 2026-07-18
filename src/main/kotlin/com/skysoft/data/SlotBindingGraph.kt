package com.skysoft.data

internal object SlotBindingGraph {
    fun isValidPair(firstSlot: Int, secondSlot: Int): Boolean =
        firstSlot != secondSlot &&
            firstSlot in PLAYER_SLOT_RANGE &&
            secondSlot in PLAYER_SLOT_RANGE &&
            (isHotbarSlot(firstSlot) || isHotbarSlot(secondSlot))

    fun repair(bindings: MutableList<ProfileStorage.SlotBindingData>): SlotBindingRepairResult {
        val repaired = mutableListOf<ProfileStorage.SlotBindingData>()
        val anchorSlots = mutableSetOf<Int>()
        val secondarySlots = mutableSetOf<Int>()
        val pairs = mutableSetOf<Pair<Int, Int>>()
        var normalizedCount = 0

        for (binding in bindings) {
            val normalized = normalizeStoredBinding(binding) ?: continue
            if (normalized != binding) normalizedCount++
            val pair = normalized.firstSlot to normalized.secondSlot
            if (
                pair in pairs ||
                normalized.firstSlot in secondarySlots ||
                normalized.secondSlot in anchorSlots ||
                normalized.secondSlot in secondarySlots
            ) {
                continue
            }
            repaired += normalized
            pairs += pair
            anchorSlots += normalized.firstSlot
            secondarySlots += normalized.secondSlot
        }

        val removedCount = bindings.size - repaired.size
        val changed = bindings != repaired
        if (changed) {
            bindings.clear()
            bindings.addAll(repaired)
        }
        return SlotBindingRepairResult(changed, removedCount, normalizedCount)
    }

    fun additionDecision(
        bindings: List<ProfileStorage.SlotBindingData>,
        sourceSlot: Int,
        targetSlot: Int,
    ): SlotBindingAdditionDecision {
        if (!isValidPair(sourceSlot, targetSlot)) return SlotBindingAdditionDecision.INVALID
        if (bindings.any { it.containsBoth(sourceSlot, targetSlot) }) {
            return SlotBindingAdditionDecision.ALREADY_BOUND
        }

        val anchorSlots = bindings.mapTo(mutableSetOf()) { it.firstSlot }
        val secondarySlots = bindings.mapTo(mutableSetOf()) { it.secondSlot }
        val candidate = candidateBinding(anchorSlots, sourceSlot, targetSlot)
        if (
            candidate.firstSlot in secondarySlots ||
            candidate.secondSlot in anchorSlots ||
            candidate.secondSlot in secondarySlots
        ) {
            return SlotBindingAdditionDecision.SLOT_CONFLICT
        }
        return SlotBindingAdditionDecision.ADD
    }

    fun add(
        bindings: MutableList<ProfileStorage.SlotBindingData>,
        sourceSlot: Int,
        targetSlot: Int,
    ): SlotBindingAdditionDecision {
        val decision = additionDecision(bindings, sourceSlot, targetSlot)
        if (decision == SlotBindingAdditionDecision.ADD) {
            val anchorSlots = bindings.mapTo(mutableSetOf()) { it.firstSlot }
            bindings += candidateBinding(anchorSlots, sourceSlot, targetSlot)
        }
        return decision
    }

    fun bindingsForSlot(
        bindings: List<ProfileStorage.SlotBindingData>,
        slotIndex: Int,
    ): List<ProfileStorage.SlotBindingData> = bindings.filter { it.contains(slotIndex) }

    fun shiftClickDecision(
        bindings: List<ProfileStorage.SlotBindingData>,
        slotIndex: Int,
    ): SlotBindingShiftClickDecision {
        val matchingBindings = bindingsForSlot(bindings, slotIndex)
        return when (matchingBindings.size) {
            0 -> SlotBindingShiftClickDecision.Unbound
            1 -> SlotBindingShiftClickDecision.Swap(matchingBindings.single())
            else -> SlotBindingShiftClickDecision.AmbiguousAnchor
        }
    }

    private fun normalizeStoredBinding(
        binding: ProfileStorage.SlotBindingData,
    ): ProfileStorage.SlotBindingData? {
        if (!isValidPair(binding.firstSlot, binding.secondSlot)) return null
        return if (isHotbarSlot(binding.firstSlot)) {
            binding.copy()
        } else {
            ProfileStorage.SlotBindingData(binding.secondSlot, binding.firstSlot)
        }
    }

    private fun candidateBinding(
        anchorSlots: Set<Int>,
        sourceSlot: Int,
        targetSlot: Int,
    ): ProfileStorage.SlotBindingData {
        val anchorSlot = when {
            sourceSlot in anchorSlots -> sourceSlot
            targetSlot in anchorSlots -> targetSlot
            isHotbarSlot(sourceSlot) -> sourceSlot
            else -> targetSlot
        }
        val secondarySlot = if (anchorSlot == sourceSlot) targetSlot else sourceSlot
        return ProfileStorage.SlotBindingData(anchorSlot, secondarySlot)
    }

    private fun ProfileStorage.SlotBindingData.contains(slotIndex: Int): Boolean =
        firstSlot == slotIndex || secondSlot == slotIndex

    private fun ProfileStorage.SlotBindingData.containsBoth(first: Int, second: Int): Boolean =
        contains(first) && contains(second)

    private fun isHotbarSlot(slotIndex: Int): Boolean = slotIndex in HOTBAR_SLOT_RANGE

    private val PLAYER_SLOT_RANGE = 0..39
    private val HOTBAR_SLOT_RANGE = 0..8
}

internal data class SlotBindingRepairResult(
    val changed: Boolean,
    val removedCount: Int,
    val normalizedCount: Int,
)

internal enum class SlotBindingAdditionDecision {
    ADD,
    ALREADY_BOUND,
    SLOT_CONFLICT,
    INVALID,
}

internal sealed interface SlotBindingShiftClickDecision {
    data object Unbound : SlotBindingShiftClickDecision
    data object AmbiguousAnchor : SlotBindingShiftClickDecision
    data class Swap(val binding: ProfileStorage.SlotBindingData) : SlotBindingShiftClickDecision
}
