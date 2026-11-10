package com.skysoft.features.inventory.buttons

import com.skysoft.utils.RegexUtilities.whitespacePattern
import com.mojang.authlib.GameProfile
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockStackFactory
import com.skysoft.data.skyblock.ItemListEntryKind
import java.util.Locale
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

internal object InventoryButtonIcons {
    data class IconCandidate(
        val id: String,
        val displayName: String,
        val stack: ItemStack,
    )

    private const val PLAYER_HEAD_CACHE_REFRESH_INTERVAL_MILLIS = 4 * 60 * 1000L
    private const val MAX_ICON_CANDIDATES = 1024

    private val iconStackCache = linkedMapOf<String, ItemStack>()
    private val playerNamePattern = Regex("^[A-Za-z0-9_]{3,16}$")
    private val playerIconPrefixes = listOf("player", "head", "skull")
    private var nextPlayerHeadCacheRefreshMillis = 0L

    fun registerPlayerHeadCacheRefresh(
        isActive: () -> Boolean,
        activeIcons: () -> Sequence<String>,
    ) {
        SkysoftClientEvents.onEndTick("Inventory Button player head cache", isActive) tick@{ minecraft ->
            if (minecraft.connection == null) {
                nextPlayerHeadCacheRefreshMillis = 0L
                return@tick
            }

            val now = System.currentTimeMillis()
            if (now < nextPlayerHeadCacheRefreshMillis) return@tick
            nextPlayerHeadCacheRefreshMillis = now + PLAYER_HEAD_CACHE_REFRESH_INTERVAL_MILLIS

            activeIcons()
                .mapNotNull(::iconStack)
                .filter { it.item == Items.PLAYER_HEAD }
                .mapNotNull { it.get(DataComponents.PROFILE) }
                .distinct()
                .forEach { minecraft.playerSkinRenderCache().lookup(it) }
        }
    }

    fun iconStack(icon: String): ItemStack? {
        val trimmed = icon.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("text:", ignoreCase = true)) return null
        inventoryButtonTextureHash(trimmed)?.let { textureHash ->
            return iconStackCache.getOrPut("skull:$textureHash") {
                SkyBlockStackFactory.texturedHead(textureHash, name = null)
            }.copy()
        }
        explicitPlayerNameQuery(trimmed)?.let { playerName ->
            return iconStackCache.getOrPut("player:${playerName.lowercase(Locale.ROOT)}") {
                playerHeadStack(playerName)
            }.copy()
        }
        skyBlockInternalName(trimmed)?.let { internalName ->
            return SkyBlockDataRepository.stack(SkyBlockDataRepository.itemKey(internalName))
        }
        return iconStackCache.getOrPut(trimmed.lowercase(Locale.ROOT)) {
            resolveItem(trimmed)?.let { ItemStack(it) } ?: ItemStack.EMPTY
        }.takeUnless { it.isEmpty }
    }

    fun searchIconCandidates(query: String): List<IconCandidate> {
        explicitPlayerNameQuery(query)?.let { playerName ->
            return listOf(playerHeadCandidate(playerName))
        }

        val results = linkedMapOf<String, IconCandidate>()
        vanillaIconCandidates(query).forEach { candidate ->
            results.putIfAbsent(candidate.id, candidate)
        }
        if (query.isNotBlank()) {
            SkyBlockDataRepository.ensureLoaded()
            SkyBlockDataRepository.search(query).asSequence()
                .filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
                .take(MAX_ICON_CANDIDATES)
                .forEach { entry ->
                    val stack = SkyBlockDataRepository.stack(entry.key) ?: return@forEach
                    val id = "skyblock:${entry.key.id}"
                    results.putIfAbsent(id, IconCandidate(id, entry.displayName, stack))
                }
        }
        playerIconCandidates(query).forEach { candidate ->
            results.putIfAbsent(candidate.id, candidate)
        }
        return results.values.take(MAX_ICON_CANDIDATES).toList()
    }

    fun clearIconCache() {
        iconStackCache.clear()
        nextPlayerHeadCacheRefreshMillis = 0L
    }

    private fun playerIconCandidates(query: String): List<IconCandidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val words = searchWords(trimmed)
        val results = linkedMapOf<String, IconCandidate>()

        fun add(candidate: IconCandidate) {
            results.putIfAbsent(candidate.id.lowercase(Locale.ROOT), candidate)
        }

        TabListApi.playerProfiles.asSequence()
            .filter { player -> player.profileName.isNotBlank() && matchesPlayerQuery(player.profileName, words) }
            .take(MAX_ICON_CANDIDATES)
            .forEach { player -> add(playerHeadCandidate(player.profileName, player.profile)) }

        typedPlayerNameQuery(trimmed)?.let { playerName ->
            add(playerHeadCandidate(playerName))
        }

        return results.values.take(MAX_ICON_CANDIDATES).toList()
    }

    private fun playerHeadCandidate(
        playerName: String,
        profile: GameProfile? = null,
    ): IconCandidate = IconCandidate(
        id = "player:$playerName",
        displayName = "$playerName's Head",
        stack = playerHeadStack(playerName, profile),
    )

    private fun playerHeadStack(playerName: String, profile: GameProfile? = null): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(
            DataComponents.PROFILE,
            if (profile != null) ResolvableProfile.createResolved(profile) else ResolvableProfile.createUnresolved(playerName),
        )
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("$playerName's Head"))
        return stack
    }

    private fun explicitPlayerNameQuery(query: String): String? {
        val trimmed = query.trim()
        val prefix = playerIconPrefixes.firstOrNull { trimmed.startsWith("$it:", ignoreCase = true) } ?: return null
        return typedPlayerNameQuery(trimmed.substring(prefix.length + 1))
    }

    private fun typedPlayerNameQuery(query: String): String? =
        query.trim().removePrefix("@").takeIf(playerNamePattern::matches)

    private fun matchesPlayerQuery(playerName: String, words: List<String>): Boolean {
        val searchable = playerName.lowercase(Locale.ROOT)
        return words.all { it in searchable }
    }

    private fun searchWords(query: String): List<String> =
        query.trim().lowercase(Locale.ROOT).split(whitespacePattern).filter { it.isNotBlank() }

    private fun vanillaIconCandidates(query: String): List<IconCandidate> {
        val words = searchWords(query)
        return BuiltInRegistries.ITEM.keySet().asSequence()
            .mapNotNull { id ->
                val item = BuiltInRegistries.ITEM.getValue(id)
                if (item == Items.AIR) return@mapNotNull null
                val stack = ItemStack(item)
                val display = stack.hoverName.string
                val searchable = buildString {
                    append(id.toString()).append(' ')
                    append(id.path.replace('_', ' ')).append(' ')
                    append(display)
                }.lowercase(Locale.ROOT)
                if (words.any { it !in searchable }) return@mapNotNull null
                IconCandidate(id.toString(), display, stack)
            }
            .sortedWith(
                compareByDescending<IconCandidate> {
                    it.id in priorityIcons
                }.thenBy { it.displayName },
            )
            .take(MAX_ICON_CANDIDATES)
            .toList()
    }

    private fun skyBlockInternalName(icon: String): String? {
        if (icon.startsWith("skyblock:", ignoreCase = true)) return icon.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        if (':' in icon) return null
        if (resolveItem(icon) != null) return null
        return icon.uppercase(Locale.ROOT)
    }

    private fun resolveItem(icon: String): Item? {
        val id = inventoryButtonVanillaIconIdentifier(icon) ?: return null
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null)
    }
    private val priorityIcons = setOf(
        "minecraft:crafting_table",
        "minecraft:chest",
        "minecraft:ender_chest",
        "minecraft:leather_chestplate",
        "minecraft:bone",
        "minecraft:gold_ingot",
        "minecraft:compass",
        "minecraft:emerald",
        "minecraft:diamond_sword",
        "minecraft:nether_star",
        "minecraft:book",
        "minecraft:command_block",
    )
}

private fun inventoryButtonVanillaIconIdentifier(icon: String): Identifier? {
    val alias = iconAliases[icon.uppercase(Locale.ROOT)] ?: icon
    val normalized = when {
        ':' in alias -> alias.lowercase(Locale.ROOT)
        alias.startsWith("minecraft/", ignoreCase = true) ->
            "minecraft:${alias.substringAfter('/').lowercase(Locale.ROOT)}"
        else -> alias.lowercase(Locale.ROOT).replace(' ', '_')
    }
    return Identifier.tryParse(normalized)
}

private val iconAliases = mapOf(
    "WORKBENCH" to "minecraft:crafting_table",
    "CRAFTING_TABLE" to "minecraft:crafting_table",
    "CHEST" to "minecraft:chest",
    "ENDER_CHEST" to "minecraft:ender_chest",
    "LEATHER_CHESTPLATE" to "minecraft:leather_chestplate",
    "BONE" to "minecraft:bone",
    "GOLD_BARDING" to "minecraft:golden_horse_armor",
    "GOLD_BLOCK" to "minecraft:gold_block",
    "EMPTY_MAP" to "minecraft:map",
    "RAW_FISH" to "minecraft:cod",
    "FISHING_ROD" to "minecraft:fishing_rod",
    "EMERALD" to "minecraft:emerald",
    "IRON_SWORD" to "minecraft:iron_sword",
    "POTION" to "minecraft:potion",
    "NETHER_STAR" to "minecraft:nether_star",
    "PAINTING" to "minecraft:painting",
    "COMMAND" to "minecraft:command_block",
    "BOOK" to "minecraft:book",
)
