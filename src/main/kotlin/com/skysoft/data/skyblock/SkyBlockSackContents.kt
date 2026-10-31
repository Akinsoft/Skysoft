package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.NumberUtilities.formatLongOrNull
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.world.item.ItemStack

object SkyBlockSackContents {
    const val SACK_OF_SACKS_MENU = "Sack of Sacks"

    fun register() {
        ProfileStorageApi.registerConsumer("SkyBlock Sack Contents") { HypixelLocationState.inSkyBlock }
        SkyBlockOpenInventoryApi.onChange(
            "SkyBlock Sack Contents inventory",
            isActive = { HypixelLocationState.inSkyBlock },
            listener = ::readOpenInventory,
        )
        SkyBlockSackChanges.onChange(
            "SkyBlock Sack Contents changes",
            isActive = { HypixelLocationState.inSkyBlock },
            listener = ::applyChanges,
        )
    }

    private fun readOpenInventory(snapshot: SkyBlockOpenInventorySnapshot?) {
        if (snapshot == null || !isSackContentsMenu(snapshot.title) || SkyBlockProfileApi.currentProfileKey == null) return
        snapshot.items.values.flatMap(::sackMenuItems).forEach { (itemId, amount, displayName) ->
            val current = ProfileStorageApi.storage.sackContents[itemId]
            if (current?.amount != amount || !current.exact || current.displayName != displayName) {
                ProfileStorageApi.updateProfile { profile ->
                    profile.sackContents[itemId] = ProfileStorage.SackItemData(
                        amount = amount,
                        exact = true,
                        displayName = displayName,
                    )
                }
            }
        }
    }

    private fun applyChanges(batch: SkyBlockSackChangeBatch) {
        if (SkyBlockProfileApi.currentProfileKey == null) return
        val contents = ProfileStorageApi.storage.sackContents
        if (batch.incomplete && contents.values.any { it.exact }) {
            ProfileStorageApi.updateProfile { profile ->
                profile.sackContents.values.forEach { it.exact = false }
            }
        }
        batch.changes.forEach { change ->
            val itemId = contents.entries
                .singleOrNull { (_, data) -> data.displayName == change.displayName }
                ?.key
                ?: SkyBlockItemNames.itemId(change.displayName)
                ?: return@forEach
            val current = contents[itemId]
            val updated = updatedSackItem(current, change.amount, batch.incomplete, change.displayName)
            if (current != updated) {
                ProfileStorageApi.updateProfile { it.sackContents[itemId] = updated }
            }
        }
    }
}

internal fun isSackContentsMenu(title: String): Boolean =
    title.endsWith(SACK_MENU_SUFFIX) && title != SkyBlockSackContents.SACK_OF_SACKS_MENU

internal fun sackMenuItems(stack: ItemStack): List<SackMenuItem> {
    val itemId = stack.skyBlockId() ?: return emptyList()
    val lore = stack.loreLines().map { line -> line.cleanSkyBlockText() }
    val gemstone = GEMSTONE_ID_PATTERN.matchEntire(itemId)
        ?: return storedSackAmount(lore)
            ?.let { amount -> listOf(SackMenuItem(itemId, amount, stack.hoverName.string, gemstoneRarity = null)) }
            .orEmpty()
    val shownRarity = GemstoneRarity.valueOf(gemstone.group("rarity"))
    val gemstoneType = gemstone.group("type")
    val gemstoneName = gemstoneType.lowercase().replaceFirstChar(Char::titlecase)
    return lore.mapNotNull { line ->
        val match = GEMSTONE_AMOUNT_PATTERN.find(line) ?: return@mapNotNull null
        val label = match.group("label")
        val rarity = if (label.equals(GEMSTONE_AMOUNT_LABEL, ignoreCase = true)) {
            shownRarity
        } else {
            GemstoneRarity.fromDisplayName(label) ?: return@mapNotNull null
        }
        val amount = match.group("amount").formatLongOrNull() ?: return@mapNotNull null
        SackMenuItem(
            itemId = "${rarity.name}_${gemstoneType}_GEM",
            amount = amount,
            displayName = "${rarity.displayName} $gemstoneName Gemstone",
            gemstoneRarity = rarity,
        )
    }
}

private fun storedSackAmount(lore: Iterable<String>): Long? =
    lore.firstNotNullOfOrNull { line -> STORED_AMOUNT_PATTERN.find(line)?.groups?.get("amount")?.value }
        ?.formatLongOrNull()

internal data class SackMenuItem(
    val itemId: String,
    val amount: Long,
    val displayName: String,
    val gemstoneRarity: GemstoneRarity?,
)

internal enum class GemstoneRarity(val displayName: String, val color: String) {
    ROUGH("Rough", "§f"),
    FLAWED("Flawed", "§a"),
    FINE("Fine", "§9"),
    FLAWLESS("Flawless", "§5"),
    PERFECT("Perfect", "§6"),
    ;

    companion object {
        fun fromDisplayName(name: String): GemstoneRarity? = entries.firstOrNull {
            it.displayName.equals(name, ignoreCase = true)
        }
    }
}

private fun updatedSackItem(
    current: ProfileStorageView.SackItemData?,
    change: Int,
    incomplete: Boolean,
    displayName: String,
): ProfileStorage.SackItemData {
    val rawAmount = (current?.amount ?: 0L) + change
    return ProfileStorage.SackItemData(
        amount = rawAmount.coerceAtLeast(0L),
        exact = current?.exact == true && !incomplete && rawAmount >= 0L,
        displayName = displayName,
    )
}

private const val SACK_MENU_SUFFIX = " Sack"
private val STORED_AMOUNT_PATTERN = Regex("^Stored: (?<amount>[\\d,]+)/")
private const val GEMSTONE_AMOUNT_LABEL = "Amount"
private val GEMSTONE_ID_PATTERN = Regex(
    "^(?<rarity>${GemstoneRarity.entries.joinToString("|") { it.name }})_(?<type>[A-Z]+)_GEM$",
)
private val GEMSTONE_AMOUNT_PATTERN = Regex(
    "^(?<label>${(GemstoneRarity.entries.map { it.displayName } + GEMSTONE_AMOUNT_LABEL).joinToString("|")}): " +
        "(?<amount>[\\d,]+)(?:\\s|$)",
    RegexOption.IGNORE_CASE,
)
