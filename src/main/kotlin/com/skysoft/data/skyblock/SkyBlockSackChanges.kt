package com.skysoft.data.skyblock

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.hoverTextComponents

object SkyBlockSackChanges {
    private var listeners: List<Listener> = emptyList()

    fun register() {
        ChatEvents.onVisibleMessage(
            "SkyBlock Sacks changes",
            isActive = { hasActiveListeners() || config.hideSacksMessages },
        ) { message ->
            if (!message.cleanText.trim().startsWith(SACKS_MESSAGE_PREFIX)) {
                return@onVisibleMessage ChatMessageVisibility.SHOW
            }

            if (hasActiveListeners()) {
                val hoverTexts = message.component.hoverTextComponents().map { hover -> hover.string }.distinct()
                val changes = parseSackChanges(hoverTexts)
                val incomplete = hoverTexts.any { hover -> OTHER_ITEMS_TEXT in hover }
                if (changes.isNotEmpty() || incomplete) dispatch(SkyBlockSackChangeBatch(changes, incomplete))
            }
            if (config.hideSacksMessages) ChatMessageVisibility.HIDE else ChatMessageVisibility.SHOW
        }
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockSackChangeBatch) -> Unit,
    ) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun dispatch(batch: SkyBlockSackChangeBatch) {
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(batch) }
            }
        }
    }

    private fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }
    private val config get() = SkysoftConfigGui.config().chat.messageFiltering

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockSackChangeBatch) -> Unit,
    )
}

data class SkyBlockSackChangeBatch(
    val changes: List<SkyBlockSackChange>,
    val incomplete: Boolean = false,
)

data class SkyBlockSackChange(
    val displayName: String,
    val amount: Int,
    val sacks: List<String>,
)

internal fun parseSackChanges(hoverTexts: Iterable<String>): List<SkyBlockSackChange> =
    hoverTexts.distinct().flatMap { hoverText ->
        if (!hoverText.startsWith(ADDED_ITEMS_HEADER) && !hoverText.startsWith(REMOVED_ITEMS_HEADER)) {
            return@flatMap emptyList()
        }
        hoverText.lineSequence().mapNotNull { line ->
            val match = SACK_CHANGE_PATTERN.matchEntire(line) ?: return@mapNotNull null
            val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: return@mapNotNull null
            val displayName = match.groups["item"]?.value?.trim().orEmpty()
            val sacks = match.groups["sacks"]?.value?.split(", ").orEmpty()
            if (displayName.isBlank() || sacks.isEmpty()) return@mapNotNull null
            SkyBlockSackChange(displayName, amount, sacks)
        }.toList()
    }

private const val SACKS_MESSAGE_PREFIX = "[Sacks]"
private const val ADDED_ITEMS_HEADER = "Added items:"
private const val REMOVED_ITEMS_HEADER = "Removed items:"
private const val OTHER_ITEMS_TEXT = "other items"
private val SACK_CHANGE_PATTERN = Regex("^\\s*(?<amount>[+-][\\d,]+) (?<item>.+) \\((?<sacks>.+)\\)$")
