package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object AttributeShardCatalog {
    private val storage get() = ProfileStorageApi.storage.attributeShards
    private val consumers = ActiveConsumerRegistry()
    private var wasActive = false
    private val gainListeners = ActiveListenerRegistry<(SkyBlockAttributeShardGain) -> Unit>()

    fun register() {
        ProfileStorageApi.registerConsumer("Attribute Shard Catalog", isCatalogActive)
        SkysoftClientEvents.onEndTick(
            "Attribute Shard loading",
            isActive = { isCatalogActive() || !AttributeShardConstants.remoteConstantsLoaded || wasActive },
        ) {
            AttributeShardConstants.ensureLoaded()
            if (!isCatalogActive()) {
                if (wasActive) AttributeShardConstants.cancelAll()
                wasActive = false
                return@onEndTick
            }
            wasActive = true
        }
        AttributeShardTransfers.register()
        ChatEvents.onVisibleMessage(
            "Attribute Shard chat",
            isActive = isCatalogActive,
        ) { message ->
            handleIncomingMessage(message.component)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onClientStopping("Attribute Shard request cancellation") {
            AttributeShardConstants.cancelAll()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    fun onGain(boundary: String, isActive: () -> Boolean, listener: (SkyBlockAttributeShardGain) -> Unit) {
        registerConsumer(boundary, isActive)
        gainListeners.register(boundary, isActive, listener)
    }

    fun readOpenInventory(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (!AttributeShardConstants.ensureLoaded()) return
        when {
            AttributeShardItemReader.isAttributeMenuName(inventoryName) ||
                inventoryItems.values.any { AttributeShardItemReader.hasAttributeStateLine(it) } ->
                processAttributeMenuItems(inventoryItems)

            inventoryName == "Hunting Box" -> processHuntingBoxItems(inventoryItems.values)
        }
    }

    fun bazaarProductAliases(): Map<String, String> = AttributeShardConstants.bazaarProductAliases()

    fun getActiveLevelByAbilityName(abilityName: String): Int {
        if (!AttributeShardConstants.ensureLoaded()) return 0
        return AttributeShardConstants.activeLevel(storage, abilityName)
    }

    private fun handleIncomingMessage(component: Component) {
        val message = with(TextUtilities) { component.formattedText() }
        if (tryHandleAttributeUpdateMessage(message, colored = true)) return
        val cleanMessage = message.cleanSkyBlockText()
        if (tryHandleAttributeUpdateMessage(cleanMessage, colored = false)) return
        handleShardAmountMessage(cleanMessage)
    }

    private fun tryHandleAttributeUpdateMessage(message: String, colored: Boolean): Boolean =
        tryHandleShardSyphonedMessage(message, if (colored) shardSyphonedPattern else cleanShardSyphonedPattern) ||
            tryHandleShardSyphonedMaxedMessage(
                message,
                if (colored) shardSyphonedMaxedPattern else cleanShardSyphonedMaxedPattern,
            ) ||
            tryHandleAttributeStateMessage(
                message,
                if (colored) attributeEnabledPattern else cleanAttributeEnabledPattern,
                enabled = true,
            ) ||
            tryHandleAttributeStateMessage(
                message,
                if (colored) attributeDisabledPattern else cleanAttributeDisabledPattern,
                enabled = false,
            )

    private fun tryHandleShardSyphonedMessage(message: String, pattern: Regex): Boolean {
        val match = pattern.matchEntire(message) ?: return false
        val shardInternalName = AttributeShardConstants.internalNameByAbilityName(match.group("attributeName")) ?: return false
        processShard(
            shardInternalName,
            currentTier = match.group("level").toInt(),
            toNextTier = match.group("untilNext").toInt(),
        )
        updateAmountInBoxDelta(shardInternalName, -match.group("amount").toInt())
        return true
    }

    private fun tryHandleShardSyphonedMaxedMessage(message: String, pattern: Regex): Boolean {
        val match = pattern.matchEntire(message) ?: return false
        val shardInternalName = AttributeShardConstants.internalNameByAbilityName(match.group("attributeName")) ?: return false
        processShard(shardInternalName, currentTier = 10, toNextTier = 0)
        updateAmountInBoxDelta(shardInternalName, -match.group("amount").toInt())
        return true
    }

    private fun tryHandleAttributeStateMessage(message: String, pattern: Regex, enabled: Boolean): Boolean {
        val match = pattern.matchEntire(message) ?: return false
        val shardInternalName = AttributeShardConstants.internalNameByAbilityName(match.group("attributeName")) ?: return false
        setAttributeState(shardInternalName, enabled)
        return true
    }

    private fun processAttributeMenuItems(items: Map<Int, ItemStack>) {
        for (item in items.values) {
            val internalName = AttributeShardItemReader.internalNameOrNull(item, "Attribute Menu") ?: continue
            val tier = AttributeShardItemReader.tier(item)

            val lore = item.loreLines()
            val toNextTier = lore.firstNotNullOfOrNull { line ->
                syphonAmountPattern.matchEntire(line)?.group("amount")?.formatInt()
                    ?: cleanSyphonAmountPattern.matchEntire(line.removeColor())?.group("amount")?.formatInt()
            } ?: 0

            processShard(internalName, tier, toNextTier)
            lore.firstNotNullOfOrNull(AttributeShardItemReader::enabledState)?.let { enabled ->
                setAttributeState(internalName, enabled)
            }
        }

        if (items[ADVANCED_MODE_SLOT]?.loreLines().orEmpty().any { advancedModeNotUnlockedPattern.matchEntire(it.removeColor()) != null }) {
            addAllMissingShards()
        }
    }

    private fun processHuntingBoxItems(items: Collection<ItemStack>) {
        for (item in items) {
            val internalName = AttributeShardItemReader.internalNameOrNull(item, "Hunting Box") ?: continue
            var tier = 0
            var toNextTier = 0
            for (line in item.loreLines()) {
                val cleanLine = line.cleanSkyBlockText()
                AttributeShardItemReader.tierFromLore(line)?.let { tier = it }
                syphonAmountPattern.matchEntire(line)?.let { match ->
                    toNextTier = match.group("amount").formatInt()
                }
                cleanSyphonAmountPattern.matchEntire(cleanLine)?.let { match ->
                    toNextTier = match.group("amount").formatInt()
                }
                (
                    amountOwnedPattern.matchEntire(line)
                        ?: cleanAmountOwnedPattern.matchEntire(cleanLine)
                    )?.let { match ->
                    updateAmountInBox(internalName, match.group("amount").formatInt())
                }
            }
            processShard(internalName, tier, toNextTier)
        }
    }

    private fun updateAmountInBox(internalName: String, amount: Int) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        if (storage[shardName]?.amountInBox == amount) return
        ProfileStorageApi.updateProfile { profile ->
            profile.attributeShards.getOrPut(shardName) { ProfileStorage.AttributeShardData() }.amountInBox = amount
        }
    }

    private fun updateAmountInBoxDelta(internalName: String, amount: Int) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        val data = storage[shardName]
        val newAmount = ((data?.amountInBox ?: 0) + amount).coerceAtLeast(0)
        if (data?.amountInBox == newAmount) return
        ProfileStorageApi.updateProfile { profile ->
            profile.attributeShards.getOrPut(shardName) { ProfileStorage.AttributeShardData() }.amountInBox = newAmount
        }
    }

    private fun processShard(internalName: String, currentTier: Int, toNextTier: Int) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        val totalAmount = AttributeShardConstants.findTotalAmount(shardName, currentTier, toNextTier) ?: return
        if (storage[shardName]?.amountSyphoned == totalAmount) return
        ProfileStorageApi.updateProfile { profile ->
            profile.attributeShards.getOrPut(shardName) { ProfileStorage.AttributeShardData() }.amountSyphoned = totalAmount
        }
    }

    private fun setAttributeState(internalName: String, enabled: Boolean) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        if (storage[shardName]?.enabled == enabled) return
        ProfileStorageApi.updateProfile { profile ->
            profile.attributeShards.getOrPut(shardName) { ProfileStorage.AttributeShardData() }.enabled = enabled
        }
    }

    private fun handleShardAmountMessage(message: String) {
        parseAttributeShardGain(message)?.let { gain ->
            val internalName = AttributeShardConstants.internalNameByDisplayName(gain.displayName) ?: return
            updateAmountInBoxDelta(internalName, gain.amount)
            val observation = SkyBlockAttributeShardGain(internalName, gain.amount)
            gainListeners.forEachActive { listener -> listener(observation) }
            return
        }
        parseHuntingBoxDeposit(message)?.let { deposit ->
            val internalName = AttributeShardConstants.internalNameByDisplayName(deposit.displayName) ?: return
            updateAmountInBoxDelta(internalName, deposit.amount)
            AttributeShardTransfers.recordDeposit(internalName, deposit.amount)
            return
        }
        fusionShardPattern.matchEntire(message)?.let { match ->
            updateAmountInBoxDeltaByDisplayName(match.group("shardName"), match.groupOrNull("amount")?.toInt() ?: 1)
        }
    }

    private fun updateAmountInBoxDeltaByDisplayName(shardName: String, amount: Int) {
        val internalName = AttributeShardConstants.internalNameByDisplayName(shardName) ?: return
        updateAmountInBoxDelta(internalName, amount)
    }

    private fun addAllMissingShards() {
        if (storage.size > SHARD_BOX_BOOTSTRAP_LIMIT) return
        for ((shardName, internalName) in AttributeShardConstants.consumableShardInternalNames()) {
            if (shardName in storage) continue
            processShard(internalName, currentTier = 0, toNextTier = 1)
        }
    }

    private val isCatalogActive: () -> Boolean = {
        consumers.hasActiveConsumers || gainListeners.hasActiveListeners || AttributeShardTransfers.hasActiveListeners()
    }

    private const val SHARD_BOX_BOOTSTRAP_LIMIT = 30
}

data class SkyBlockAttributeShardGain(
    val itemId: String,
    val amount: Int,
)

internal data class ParsedAttributeShardGain(
    val displayName: String,
    val amount: Int,
)

internal data class ParsedHuntingBoxDeposit(
    val displayName: String,
    val amount: Int,
)

internal fun parseHuntingBoxDeposit(message: String): ParsedHuntingBoxDeposit? {
    val match = sentToHuntingBoxPattern.matchEntire(message) ?: return null
    return ParsedHuntingBoxDeposit(
        displayName = match.group("shardName"),
        amount = match.groupOrNull("amount")?.toInt() ?: 1,
    )
}

internal fun parseAttributeShardGain(message: String): ParsedAttributeShardGain? {
    val match = sequenceOf(caughtShardsPattern, lootShareShardPattern, charmedShardPattern, receivedCharmedShardPattern)
        .firstNotNullOfOrNull { pattern -> pattern.matchEntire(message) }
        ?: return null
    return ParsedAttributeShardGain(
        displayName = match.group("shardName"),
        amount = match.groupOrNull("amount")?.toInt() ?: 1,
    )
}

private const val ADVANCED_MODE_SLOT = 52
private val syphonAmountPattern = Regex("""§7Syphon §b(?<amount>\d+) §7shards? to (?:level up|unlock)!""")
private val amountOwnedPattern = Regex("""§7Owned: §b(?<amount>[\d,]+) Shards?""")
private val cleanSyphonAmountPattern = Regex("""Syphon (?<amount>\d+) shards? to (?:level up|unlock)!""")
private val cleanAmountOwnedPattern = Regex("""Owned: (?<amount>[\d,]+) Shards?""")
private val advancedModeNotUnlockedPattern = Regex("""Advanced Mode unlocked at 30""")
private val shardSyphonedPattern =
    Regex("""§a\+(?<amount>\d+) (?<attributeName>.+) Attribute §r§7\(Level (?<level>\d+)\) - (?<untilNext>\d+) more to upgrade!""")
private val shardSyphonedMaxedPattern =
    Regex("""§a\+(?<amount>\d+) (?<attributeName>.+) Attribute §r§7\(Level (?<level>\d+)\) §r§a§lMAXED""")
private val attributeEnabledPattern = Regex("""§6(?<attributeName>.+) §r§ais now enabled!""")
private val attributeDisabledPattern = Regex("""§6(?<attributeName>.+) §r§cis now disabled!""")
private val cleanShardSyphonedPattern =
    Regex("""\+(?<amount>\d+) (?<attributeName>.+) Attribute \(Level (?<level>\d+)\) - (?<untilNext>\d+) more to upgrade!""")
private val cleanShardSyphonedMaxedPattern =
    Regex("""\+(?<amount>\d+) (?<attributeName>.+) Attribute \(Level (?<level>\d+)\) MAXED""")
private val cleanAttributeEnabledPattern = Regex("""(?<attributeName>.+) is now enabled!""")
private val cleanAttributeDisabledPattern = Regex("""(?<attributeName>.+) is now disabled!""")
private val caughtShardsPattern =
    Regex("""You caught(?: an?| x(?<amount>\d+))? (?<shardName>.+) Shards?!""")
private val lootShareShardPattern =
    Regex("""LOOT SHARE You received (?:an?|(?<amount>\d+)) (?<shardName>.+) Shards? for assisting .*!""")
private val charmedShardPattern =
    Regex("""(?:CHARM|SALT|NAGA) You charmed an? (?<shardName>.+) and captured (?:(?<amount>\d+) Shards from it|its Shard)\.""")
private val receivedCharmedShardPattern =
    Regex("""CHARM! You charmed the .+? and received (?<amount>\d+) (?<shardName>.+?) Shards?!""")
private val sentToHuntingBoxPattern =
    Regex("""You sent (?:an?|a|(?<amount>\d+)) (?<shardName>.+) Shards? to your Hunting Box\.""")
private val fusionShardPattern =
    Regex("""FUSION! You obtained(?: an?)? (?<shardName>.+) Shard(?: x(?<amount>\d+))?!(?: NEW!)?""")
