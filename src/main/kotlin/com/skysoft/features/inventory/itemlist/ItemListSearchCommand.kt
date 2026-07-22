package com.skysoft.features.inventory.itemlist

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import java.util.Locale
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object ItemListSearchCommand {
    private var pendingKey: ItemListEntryKey? = null

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            LiteralArgumentBuilder.literal<FabricClientCommandSource>("ssearch")
                .executes { context ->
                    SkysoftChat.error(context.source, "Usage: /ssearch <item or mob name or ID>")
                    0
                }
                .then(
                    RequiredArgumentBuilder.argument<FabricClientCommandSource, String>(
                        "item",
                        StringArgumentType.greedyString(),
                    ).suggests { _, builder -> suggestItems(builder) }
                        .executes { context ->
                            queueItem(context.source, StringArgumentType.getString(context, "item"))
                        },
                ),
        )
    }

    fun openPending() {
        val key = pendingKey ?: return
        pendingKey = null
        MinecraftClient.setScreen(ItemListViewerScreen(null, key))
    }

    fun hasPendingScreen(): Boolean = pendingKey != null

    private fun queueItem(source: FabricClientCommandSource, rawQuery: String): Int {
        SkyBlockDataRepository.ensureLoaded()
        if (SkyBlockDataRepository.status.state != SkyBlockDataLoadState.READY) {
            SkysoftChat.error(source, "Item List data is not ready yet.")
            return 0
        }
        val query = rawQuery.trim()
        val entry = resolveItemListCommandQuery(query, SkyBlockDataRepository.ItemListData.search(query))
        if (entry == null) {
            SkysoftChat.error(source, "No Item List entry found for '$query'.")
            return 0
        }
        pendingKey = entry.key
        return Command.SINGLE_SUCCESS
    }

    private fun suggestItems(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        SkyBlockDataRepository.ensureLoaded()
        if (SkyBlockDataRepository.status.state != SkyBlockDataLoadState.READY) return builder.buildFuture()
        val remaining = builder.remaining.lowercase(Locale.US)
        itemCommandSuggestions(SkyBlockDataRepository.ItemListData.search(builder.remaining), remaining)
            .take(MAX_SUGGESTIONS)
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    private const val MAX_SUGGESTIONS = 80
}

internal fun resolveItemListCommandQuery(query: String, results: List<ItemListEntry>): ItemListEntry? {
    if (query.isBlank()) return null
    return results.firstOrNull { it.displayName.equals(query, ignoreCase = true) }
        ?: results.firstOrNull { it.key.id.equals(query, ignoreCase = true) }
        ?: results.firstOrNull()
}

internal fun itemCommandSuggestions(results: List<ItemListEntry>, remaining: String): List<String> =
    results.asSequence()
        .flatMap { sequenceOf(it.displayName, it.key.id) }
        .filter { it.contains(remaining, ignoreCase = true) }
        .distinctBy { it.lowercase(Locale.US) }
        .toList()
