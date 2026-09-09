package com.skysoft.features.inventory

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
                texturedHeadStack(textureHash)
            }.copy()
        }
        playerNameFromIcon(trimmed)?.let { playerName ->
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

    fun searchIconCandidates(query: String, limit: Int = 1024): List<IconCandidate> {
        explicitPlayerNameQuery(query)?.let { playerName ->
            return listOf(playerHeadCandidate(playerName)).take(limit)
        }

        val results = linkedMapOf<String, IconCandidate>()
        vanillaIconCandidates(query, limit).forEach { candidate ->
            results.putIfAbsent(candidate.id, candidate)
        }
        if (query.isNotBlank()) {
            SkyBlockDataRepository.ensureLoaded()
            SkyBlockDataRepository.search(query).asSequence()
                .filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
                .take(limit)
                .forEach { entry ->
                    val stack = SkyBlockDataRepository.stack(entry.key) ?: return@forEach
                    val id = "skyblock:${entry.key.id}"
                    results.putIfAbsent(id, IconCandidate(id, entry.displayName, stack))
                }
        }
        playerIconCandidates(query, limit).forEach { candidate ->
            results.putIfAbsent(candidate.id, candidate)
        }
        return results.values.take(limit).toList()
    }

    fun clearIconCache() {
        iconStackCache.clear()
        nextPlayerHeadCacheRefreshMillis = 0L
    }

    private fun playerIconCandidates(query: String, limit: Int): List<IconCandidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val words = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = linkedMapOf<String, IconCandidate>()

        fun add(candidate: IconCandidate) {
            results.putIfAbsent(candidate.id.lowercase(Locale.ROOT), candidate)
        }

        TabListApi.playerProfiles.asSequence()
            .filter { player -> player.profileName.isNotBlank() && matchesPlayerQuery(player.profileName, words) }
            .take(limit)
            .forEach { player -> add(playerHeadCandidate(player.profileName, player.profile)) }

        typedPlayerNameQuery(trimmed)?.let { playerName ->
            add(playerHeadCandidate(playerName))
        }

        return results.values.take(limit).toList()
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

    private fun texturedHeadStack(textureHash: String): ItemStack =
        SkyBlockStackFactory.texturedHead(textureHash, name = null)

    private fun playerNameFromIcon(icon: String): String? = explicitPlayerNameQuery(icon)

    private fun explicitPlayerNameQuery(query: String): String? {
        val trimmed = query.trim()
        val prefix = playerIconPrefixes.firstOrNull { trimmed.startsWith("$it:", ignoreCase = true) } ?: return null
        return typedPlayerNameQuery(trimmed.substring(prefix.length + 1))
    }

    private fun typedPlayerNameQuery(query: String): String? {
        val name = query.trim().removePrefix("@").takeIf { it.isNotBlank() } ?: return null
        return name.takeIf { playerNamePattern.matches(it) }
    }

    private fun matchesPlayerQuery(playerName: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        val searchable = playerName.lowercase(Locale.ROOT)
        return words.all { it in searchable }
    }

    private fun vanillaIconCandidates(query: String, limit: Int): List<IconCandidate> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val words = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        return BuiltInRegistries.ITEM.keySet().asSequence()
            .mapNotNull { id ->
                val item = BuiltInRegistries.ITEM.getValue(id)
                if (item == Items.AIR) return@mapNotNull null
                val stack = ItemStack(item)
                val display = stack.hoverName.string
                val searchable = buildString {
                    append(id.toString()).append(' ')
                    append(id.path.replace('_', ' ')).append(' ')
                    append(display.lowercase(Locale.ROOT))
                }.lowercase(Locale.ROOT)
                if (words.isNotEmpty() && words.any { it !in searchable }) return@mapNotNull null
                IconCandidate(id.toString(), display, stack)
            }
            .sortedWith(
                compareByDescending<IconCandidate> {
                    it.id in priorityIcons
                }.thenBy { it.displayName },
            )
            .take(limit)
            .toList()
    }

    private fun skyBlockInternalName(icon: String): String? {
        if (icon.startsWith("skyblock:", ignoreCase = true)) return icon.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        if (':' in icon) return null
        if (resolveItem(icon) != null) return null
        return icon.trim().uppercase(Locale.ROOT).takeIf { it.isNotEmpty() }
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

internal fun inventoryButtonVanillaIconIdentifier(icon: String): Identifier? {
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
