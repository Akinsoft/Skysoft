package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object SkyBlockSackContents {
    fun register() {
        ProfileStorageApi.registerConsumer("SkyBlock Sack Contents") { HypixelLocationState.inSkyBlock }
        SkyBlockOpenInventoryApi.onUpdate(
            "SkyBlock Sack Contents inventory",
            isActive = {
                HypixelLocationState.inSkyBlock && MinecraftClient.screen() is AbstractContainerScreen<*>
            },
            listener = ::readOpenInventory,
        )
        SkyBlockSackChanges.onChange(
            "SkyBlock Sack Contents changes",
            isActive = { HypixelLocationState.inSkyBlock },
            listener = ::applyChanges,
        )
    }

    fun amount(itemId: String): SkyBlockSackAmount? =
        ProfileStorageApi.storage.sackContents[itemId]?.let { data ->
            SkyBlockSackAmount(data.amount, data.exact)
        }

    fun snapshot(): Map<String, SkyBlockSackAmount> =
        ProfileStorageApi.storage.sackContents.mapValues { (_, data) ->
            SkyBlockSackAmount(data.amount, data.exact)
        }

    private fun readOpenInventory(snapshot: SkyBlockOpenInventorySnapshot?) {
        if (snapshot == null || !isSackContentsMenu(snapshot.title) || SkyBlockProfileApi.currentProfileKey == null) return
        var changed = false
        snapshot.items.values.forEach { stack ->
            val itemId = stack.skyBlockId() ?: return@forEach
            val amount = storedSackAmount(stack.loreLines()) ?: return@forEach
            val displayName = stack.hoverName.string
            val current = ProfileStorageApi.storage.sackContents[itemId]
            if (current?.amount != amount || !current.exact || current.displayName != displayName) {
                ProfileStorageApi.storage.sackContents[itemId] = ProfileStorage.SackItemData(
                    amount = amount,
                    exact = true,
                    displayName = displayName,
                )
                changed = true
            }
        }
        if (changed) ProfileStorageApi.markDirty()
    }

    private fun applyChanges(batch: SkyBlockSackChangeBatch) {
        if (SkyBlockProfileApi.currentProfileKey == null) return
        val contents = ProfileStorageApi.storage.sackContents
        var changed = false
        if (batch.incomplete) {
            contents.values.forEach { item ->
                if (item.exact) {
                    item.exact = false
                    changed = true
                }
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
                contents[itemId] = updated
                changed = true
            }
        }
        if (changed) ProfileStorageApi.markDirty()
    }

    private fun isSackContentsMenu(title: String): Boolean =
        title.endsWith(SACK_MENU_SUFFIX) && title != SACK_OF_SACKS_MENU
}

data class SkyBlockSackAmount(
    val amount: Long,
    val exact: Boolean,
)

internal fun storedSackAmount(loreLines: Iterable<String>): Long? =
    loreLines.asSequence()
        .map { line -> line.cleanSkyBlockText() }
        .mapNotNull { line -> STORED_AMOUNT_PATTERN.find(line)?.groups?.get("amount")?.value }
        .mapNotNull { value -> value.replace(",", "").toLongOrNull() }
        .firstOrNull()

internal fun updatedSackItem(
    current: ProfileStorage.SackItemData?,
    change: Int,
    incomplete: Boolean,
    displayName: String = current?.displayName.orEmpty(),
): ProfileStorage.SackItemData {
    val rawAmount = (current?.amount ?: 0L) + change
    return ProfileStorage.SackItemData(
        amount = rawAmount.coerceAtLeast(0L),
        exact = current?.exact == true && !incomplete && rawAmount >= 0L,
        displayName = displayName,
    )
}

private const val SACK_MENU_SUFFIX = " Sack"
private const val SACK_OF_SACKS_MENU = "Sack of Sacks"
private val STORED_AMOUNT_PATTERN = Regex("^Stored: (?<amount>[\\d,]+)/")
