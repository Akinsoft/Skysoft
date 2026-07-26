package com.skysoft.features.inventory

internal class SuperpairsMemory<T> {
    private val boardSlots = mutableSetOf<Int>()
    private val revealedCards = mutableMapOf<Int, T>()

    fun observeHiddenCards(slotIds: Iterable<Int>) {
        boardSlots.addAll(slotIds)
    }

    fun rememberRevealedCard(slotId: Int, card: () -> T) {
        if (slotId in boardSlots) revealedCards[slotId] = card()
    }

    fun rememberedCard(slotId: Int): T? = revealedCards[slotId]

    fun clear() {
        boardSlots.clear()
        revealedCards.clear()
    }
}
