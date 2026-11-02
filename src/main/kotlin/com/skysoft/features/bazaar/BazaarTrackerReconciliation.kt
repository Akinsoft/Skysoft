package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import kotlin.math.abs
import kotlin.math.max

internal data class BazaarOrderRowMatch(
    val order: ProfileStorageView.BazaarOrderData,
    val parsed: PendingOrder,
)

internal data class BazaarSnapshotReconciliation(
    val matches: List<BazaarOrderRowMatch>,
    val unmatchedRows: List<PendingOrder>,
)

internal data class BazaarGuiIdentityUpdate(
    val changed: Boolean,
    val meaningful: Boolean,
)

internal fun reconcileBazaarSnapshot(
    orders: List<ProfileStorageView.BazaarOrderData>,
    rows: List<PendingOrder>,
): BazaarSnapshotReconciliation {
    if (orders.isEmpty()) return BazaarSnapshotReconciliation(emptyList(), rows)
    if (rows.isEmpty()) return BazaarSnapshotReconciliation(emptyList(), emptyList())

    val matchCosts = Array(orders.size) { orderIndex ->
        LongArray(rows.size) { rowIndex -> bazaarIdentityCost(orders[orderIndex], rows[rowIndex]) }
    }
    val matrixSize = orders.size + rows.size
    val costs = Array(matrixSize) { rowIndex ->
        LongArray(matrixSize) { columnIndex ->
            when {
                rowIndex < orders.size && columnIndex < rows.size -> matchCosts[rowIndex][columnIndex]
                rowIndex < orders.size || columnIndex < rows.size -> UNMATCHED_ASSIGNMENT_COST
                else -> 0L
            }
        }
    }
    val assignment = minimumCostAssignment(costs)
    val matchedRowIndexes = mutableSetOf<Int>()
    val matches = buildList {
        orders.indices.forEach { orderIndex ->
            val rowIndex = assignment[orderIndex]
            if (rowIndex !in rows.indices) return@forEach
            val cost = matchCosts[orderIndex][rowIndex]
            if (cost >= UNMATCHED_ASSIGNMENT_COST) return@forEach
            matchedRowIndexes += rowIndex
            add(
                BazaarOrderRowMatch(
                    order = orders[orderIndex],
                    parsed = rows[rowIndex],
                ),
            )
        }
    }
    return BazaarSnapshotReconciliation(
        matches = matches,
        unmatchedRows = rows.filterIndexed { index, _ -> index !in matchedRowIndexes },
    )
}

internal fun findMatchingOrder(parsed: PendingOrder): ProfileStorageView.BazaarOrderData? =
    reconcileBazaarSnapshot(storage.activeOrders, listOf(parsed)).matches.singleOrNull()?.order

internal fun guiMatchIsPlausible(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Boolean =
    parsed.amount <= 0 ||
        order.filledAmount < parsed.amount + max(parsed.amountResolution, MIN_GUI_FILL_TOLERANCE.toDouble())

private fun guiFilledScore(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Long =
    amountDistance(order.filledAmount, parsed.filledAmount ?: 0L)

private fun guiSlotScore(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Int {
    val slot = parsed.guiSlot ?: return UNKNOWN_GUI_SLOT_SCORE
    return when (order.lastGuiSlot) {
        slot -> EXACT_GUI_SLOT_SCORE
        -1 -> UNKNOWN_GUI_SLOT_SCORE
        else -> MISMATCHED_GUI_SLOT_SCORE
    }
}

internal fun bazaarIdentityCost(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Long {
    if (!isSameOrderItem(order, parsed)) return IMPOSSIBLE_ASSIGNMENT_COST
    if (
        !haveOverlappingRanges(
            order.amountOrdered.toDouble(),
            order.amountResolution,
            parsed.amount.toDouble(),
            parsed.amountResolution,
            EXACT_AMOUNT_EPSILON,
        )
    ) {
        return IMPOSSIBLE_ASSIGNMENT_COST
    }
    if (
        order.pricePerUnit > 0.0 &&
        parsed.pricePerUnit > 0.0 &&
        !haveOverlappingRanges(
            order.pricePerUnit,
            order.pricePerUnitResolution,
            parsed.pricePerUnit,
            parsed.pricePerUnitResolution,
            PRICE_EPSILON,
        )
    ) {
        return IMPOSSIBLE_ASSIGNMENT_COST
    }
    if (!guiMatchIsPlausible(order, parsed)) return IMPOSSIBLE_ASSIGNMENT_COST

    val totalPenalty = parsed.totalCoins?.let { total ->
        if (
            haveOverlappingRanges(
                order.totalCoins,
                order.totalCoinsResolution,
                total,
                parsed.totalCoinsResolution,
                TOTAL_EPSILON,
            )
        ) {
            0L
        } else {
            TOTAL_MISMATCH_COST
        }
    } ?: 0L
    val fillCost = guiFilledScore(order, parsed).coerceAtMost(MAX_FILL_DISTANCE) * FILL_DISTANCE_COST
    val slotCost = guiSlotScore(order, parsed).toLong() * SLOT_DISTANCE_COST
    val ageTieBreak = (Long.MAX_VALUE - order.updatedAtMillis).coerceAtLeast(0L) % AGE_TIE_BREAK_RANGE
    return totalPenalty + fillCost + slotCost + ageTieBreak
}

internal fun isSameOrderItem(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Boolean =
    order.type == parsed.type &&
        (productMatches(order.productId, parsed.productId) || namesMatch(order.itemName, parsed.itemName))

internal fun updateOrderIdentityFromGui(
    order: ProfileStorage.BazaarOrderData,
    parsed: PendingOrder,
): BazaarGuiIdentityUpdate {
    var changed = false
    var meaningful = false
    fun update(isMeaningful: Boolean = true, block: () -> Unit) {
        block()
        changed = true
        meaningful = meaningful || isMeaningful
    }
    parsed.guiSlot?.let { slot ->
        if (order.lastGuiSlot != slot) update(isMeaningful = false) { order.lastGuiSlot = slot }
    }
    if ((order.productId == null || isGenericBazaarProductId(order.productId)) && parsed.productId != null) update {
        order.productId = parsed.productId
    }
    if (order.pricePerUnit <= 0.0 && parsed.pricePerUnit > 0.0) update {
        order.pricePerUnit = parsed.pricePerUnit
        order.pricePerUnitResolution = parsed.pricePerUnitResolution
    }
    if (order.totalCoins <= 0.0 && parsed.totalCoins != null) update {
        order.totalCoins = parsed.totalCoins
        order.totalCoinsResolution = parsed.totalCoinsResolution
    }
    if ((parsed.amount > 0 && order.amountOrdered <= 0) || shouldUpdateUnconfirmedAmount(order, parsed)) update {
        order.amountOrdered = parsed.amount
        order.amountResolution = parsed.amountResolution
    }
    if (shouldUpdateUnconfirmedTotal(order, parsed)) update {
        order.totalCoins = requireNotNull(parsed.totalCoins)
        order.totalCoinsResolution = parsed.totalCoinsResolution
    }
    return BazaarGuiIdentityUpdate(changed, meaningful)
}

private fun shouldUpdateUnconfirmedAmount(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Boolean =
    !order.setupConfirmed &&
        parsed.amount > 0 &&
        (order.amountOrdered != parsed.amount || order.amountResolution != parsed.amountResolution)

private fun shouldUpdateUnconfirmedTotal(order: ProfileStorageView.BazaarOrderData, parsed: PendingOrder): Boolean {
    val total = parsed.totalCoins ?: return false
    return !order.setupConfirmed &&
        total > 0.0 &&
        (
            abs(order.totalCoins - total) > TOTAL_RECALCULATION_EPSILON ||
                order.totalCoinsResolution != parsed.totalCoinsResolution
            )
}

internal fun haveOverlappingRanges(
    firstValue: Double,
    firstResolution: Double,
    secondValue: Double,
    secondResolution: Double,
    epsilon: Double,
): Boolean {
    if (!firstValue.isFinite() || !secondValue.isFinite()) return false
    val firstPadding = max(firstResolution, epsilon)
    val secondPadding = max(secondResolution, epsilon)
    return firstValue < secondValue + secondPadding && secondValue < firstValue + firstPadding
}

internal fun hasOverlappingFillEstimateIdentity(
    order: ProfileStorageView.BazaarOrderData,
    other: ProfileStorageView.BazaarOrderData,
): Boolean =
    order.id != other.id &&
        order.type == other.type &&
        order.productId != null &&
        order.productId == other.productId &&
        haveOverlappingRanges(
            order.pricePerUnit,
            order.pricePerUnitResolution,
            other.pricePerUnit,
            other.pricePerUnitResolution,
            BAZAAR_PRICE_EPSILON,
        )

private fun minimumCostAssignment(costs: Array<LongArray>): IntArray {
    val size = costs.size
    require(costs.all { it.size == size }) { "Assignment matrix must be square" }
    val rowPotential = LongArray(size + 1)
    val columnPotential = LongArray(size + 1)
    val matchedRow = IntArray(size + 1)
    val path = IntArray(size + 1)
    for (row in 1..size) {
        matchedRow[0] = row
        var column = 0
        val minimum = LongArray(size + 1) { IMPOSSIBLE_ASSIGNMENT_COST }
        val used = BooleanArray(size + 1)
        do {
            used[column] = true
            val currentRow = matchedRow[column]
            var delta = IMPOSSIBLE_ASSIGNMENT_COST
            var nextColumn = 0
            for (candidateColumn in 1..size) {
                if (used[candidateColumn]) continue
                val reducedCost = costs[currentRow - 1][candidateColumn - 1] -
                    rowPotential[currentRow] - columnPotential[candidateColumn]
                if (reducedCost < minimum[candidateColumn]) {
                    minimum[candidateColumn] = reducedCost
                    path[candidateColumn] = column
                }
                if (minimum[candidateColumn] < delta) {
                    delta = minimum[candidateColumn]
                    nextColumn = candidateColumn
                }
            }
            for (candidateColumn in 0..size) {
                if (used[candidateColumn]) {
                    rowPotential[matchedRow[candidateColumn]] += delta
                    columnPotential[candidateColumn] -= delta
                } else {
                    minimum[candidateColumn] -= delta
                }
            }
            column = nextColumn
        } while (matchedRow[column] != 0)
        do {
            val previousColumn = path[column]
            matchedRow[column] = matchedRow[previousColumn]
            column = previousColumn
        } while (column != 0)
    }
    val assignment = IntArray(size) { -1 }
    for (column in 1..size) assignment[matchedRow[column] - 1] = column - 1
    return assignment
}

private const val EXACT_GUI_SLOT_SCORE = 0
private const val UNKNOWN_GUI_SLOT_SCORE = 1
private const val MISMATCHED_GUI_SLOT_SCORE = 2
private const val MIN_GUI_FILL_TOLERANCE = 1L
private const val PRICE_EPSILON = 0.01
private const val TOTAL_EPSILON = 1.0
private const val TOTAL_MISMATCH_COST = 100_000L
private const val FILL_DISTANCE_COST = 100L
private const val SLOT_DISTANCE_COST = 10L
private const val MAX_FILL_DISTANCE = 10_000L
private const val AGE_TIE_BREAK_RANGE = 10L
private const val UNMATCHED_ASSIGNMENT_COST = 1_000_000_000_000L
private const val IMPOSSIBLE_ASSIGNMENT_COST = 1_000_000_000_000_000L
