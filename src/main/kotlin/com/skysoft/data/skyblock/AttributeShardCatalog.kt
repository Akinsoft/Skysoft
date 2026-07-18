package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.skysoft.SkysoftMod
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getCompoundOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.features.pets.PetFeatureDemand
import com.skysoft.utils.net.PendingHttpRequests
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

object AttributeShardCatalog {
    private val storage get() = ProfileStorageApi.storage.attributeShards
    private var wasActive = false

    fun register() {
        ProfileStorageApi.registerConsumer("Attribute Shard Catalog", PetFeatureDemand::isActive)
        SkysoftClientEvents.onEndTick(
            "Attribute Shard loading",
            isActive = { PetFeatureDemand.isActive() || wasActive },
        ) {
            if (!PetFeatureDemand.isActive()) {
                if (wasActive) AttributeShardConstants.cancelAll()
                wasActive = false
                return@onEndTick
            }
            wasActive = true
            AttributeShardConstants.ensureLoaded()
        }
        ChatEvents.onVisibleMessage(
            "Attribute Shard chat",
            isActive = PetFeatureDemand::isActive,
        ) { message ->
            handleIncomingMessage(message.component)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onClientStopping("Attribute Shard request cancellation") {
            AttributeShardConstants.cancelAll()
        }
    }

    fun readOpenInventory(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (!AttributeShardConstants.ensureLoaded()) return
        when {
            AttributeShardItemResolver.isAttributeMenuName(inventoryName) ||
                inventoryItems.values.any { AttributeShardItemResolver.hasAttributeStateLine(it) } ->
                processAttributeMenuItems(inventoryItems)

            inventoryName == "Hunting Box" -> processHuntingBoxItems(inventoryItems.values)
        }
    }

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
            val internalName = AttributeShardItemResolver.internalNameOrNull(item, "Attribute Menu") ?: continue
            var tier = 0
            val hoverName = item.formattedHoverName()
            val cleanHoverName = hoverName.cleanSkyBlockText()
            (
                attributeShardNamePattern.matchEntire(hoverName)
                    ?: cleanAttributeShardNamePattern.matchEntire(cleanHoverName)
                )?.let { match ->
                tier = match.groupOrNull("tier")?.romanToDecimal() ?: 0
            }

            val lore = item.loreLines()
            var toNextTier = 0
            lore.firstNotNullOfOrNull { line ->
                syphonAmountPattern.matchEntire(line)?.group("amount")?.formatInt()
                    ?: cleanSyphonAmountPattern.matchEntire(line.removeColor())?.group("amount")?.formatInt()
            }?.let { toNextTier = it }

            processShard(internalName, tier, toNextTier)
            lore.firstNotNullOfOrNull { line ->
                attributeStatePattern.matchEntire(line)?.group("state")
                    ?: cleanAttributeStatePattern.matchEntire(line.removeColor())?.group("state")
            }?.let { state ->
                setAttributeState(internalName, enabled = AttributeShardItemResolver.isEnabledAttributeState(state))
            }
        }

        if (items[ADVANCED_MODE_SLOT]?.loreLines().orEmpty().any { advancedModeNotUnlockedPattern.matchEntire(it.removeColor()) != null }) {
            addAllMissingShards()
        }
    }

    private fun processHuntingBoxItems(items: Collection<ItemStack>) {
        for (item in items) {
            val internalName = AttributeShardItemResolver.internalNameOrNull(item, "Hunting Box") ?: continue
            var tier = 0
            var toNextTier = 0
            for (line in item.loreLines()) {
                val cleanLine = line.cleanSkyBlockText()
                (
                    attributeShardNameLorePattern.matchEntire(line)
                        ?: cleanAttributeShardNameLorePattern.matchEntire(cleanLine)
                    )?.let { match ->
                    tier = match.groupOrNull("tier")?.romanToDecimal() ?: 0
                }
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
        val data = storage.getOrPut(shardName) { ProfileStorage.AttributeShardData() }
        if (data.amountInBox == amount) return
        data.amountInBox = amount
        ProfileStorageApi.markDirty()
    }

    private fun updateAmountInBoxDelta(internalName: String, amount: Int) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        val data = storage.getOrPut(shardName) { ProfileStorage.AttributeShardData() }
        val newAmount = (data.amountInBox + amount).coerceAtLeast(0)
        if (data.amountInBox == newAmount) return
        data.amountInBox = newAmount
        ProfileStorageApi.markDirty()
    }

    private fun processShard(internalName: String, currentTier: Int, toNextTier: Int) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        val totalAmount = AttributeShardConstants.findTotalAmount(shardName, currentTier, toNextTier) ?: return
        val data = storage.getOrPut(shardName) { ProfileStorage.AttributeShardData() }
        if (data.amountSyphoned == totalAmount) return
        data.amountSyphoned = totalAmount
        ProfileStorageApi.markDirty()
    }

    private fun setAttributeState(internalName: String, enabled: Boolean) {
        val shardName = AttributeShardConstants.shardNameByInternalName(internalName) ?: return
        if (!AttributeShardConstants.isConsumable(shardName)) return
        val data = storage.getOrPut(shardName) { ProfileStorage.AttributeShardData() }
        if (data.enabled == enabled) return
        data.enabled = enabled
        ProfileStorageApi.markDirty()
    }

    private fun handleShardAmountMessage(message: String) {
        caughtShardsPattern.matchEntire(message)?.let { match ->
            updateAmountInBoxDeltaByDisplayName(match.group("shardName"), match.groupOrNull("amount")?.toInt() ?: 1)
            return
        }
        lootShareShardPattern.matchEntire(message)?.let { match ->
            updateAmountInBoxDeltaByDisplayName(match.group("shardName"), match.groupOrNull("amount")?.toInt() ?: 1)
            return
        }
        charmedShardPattern.matchEntire(message)?.let { match ->
            updateAmountInBoxDeltaByDisplayName(match.group("shardName"), match.groupOrNull("amount")?.toInt() ?: 1)
            return
        }
        sentToHuntingBoxPattern.matchEntire(message)?.let { match ->
            updateAmountInBoxDeltaByDisplayName(match.group("shardName"), match.groupOrNull("amount")?.toInt() ?: 1)
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
        for (shardInfo in AttributeShardConstants.consumableShards()) {
            if (shardInfo.bazaarName in storage) continue
            processShard(shardInfo.internalName, currentTier = 0, toNextTier = 1)
        }
    }

    private const val SHARD_BOX_BOOTSTRAP_LIMIT = 30
}

private const val ADVANCED_MODE_SLOT = 52
private val attributeShardNamePattern = Regex("""§6(?<name>.+?) ?(?<tier>[IVXL]+)?$""")
private val cleanAttributeShardNamePattern = Regex("""(?<name>.+?) ?(?<tier>[IVXL]+)?$""")
private val attributeShardNameLorePattern = Regex("""§6(?<name>.+?) ?(?<tier>[IVXL]+)? §8\(\w+\)$""")
private val cleanAttributeShardNameLorePattern = Regex("""(?<name>.+?) ?(?<tier>[IVXL]+)? \(\w+\)$""")
private val attributeStatePattern = Regex("""§7Enabled: §.(?<state>.+)""")
private val syphonAmountPattern = Regex("""§7Syphon §b(?<amount>\d+) §7shards? to (?:level up|unlock)!""")
private val amountOwnedPattern = Regex("""§7Owned: §b(?<amount>[\d,]+) Shards?""")
private val cleanAttributeStatePattern = Regex("""Enabled: (?<state>.+)""")
private val cleanSyphonAmountPattern = Regex("""Syphon (?<amount>\d+) shards? to (?:level up|unlock)!""")
private val cleanAmountOwnedPattern = Regex("""Owned: (?<amount>[\d,]+) Shards?""")
private val attributeSourcePattern = Regex("""§7Source: §.(?<source>.+)""")
private val cleanAttributeSourcePattern = Regex("""Source: (?<source>.+)""")
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
private val sentToHuntingBoxPattern =
    Regex("""You sent (?:an?|a|(?<amount>\d+)) (?<shardName>.+) Shards? to your Hunting Box\.""")
private val fusionShardPattern =
    Regex("""FUSION! You obtained(?: an?)? (?<shardName>.+) Shard(?: x(?<amount>\d+))?!(?: NEW!)?""")

private object AttributeShardConstants {
    private const val ATTRIBUTE_SHARDS_URL =
        "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/attribute_shards.json"

    private val gson = Gson()
    private val requests = PendingHttpRequests()
    private val loadingConstants = AtomicBoolean(false)
    private var constantsLastFailure = ElapsedTimeMark.farPast()

    @Volatile
    private var attributeLevelling = mapOf<SkyBlockRarity, List<Int>>()

    @Volatile
    private var attributeInfo = mapOf<String, NeuAttributeShardData>()

    @Volatile
    private var internalNameToShard = mapOf<String, String>()

    @Volatile
    private var attributeAbilityNameToShard = mapOf<String, String>()

    @Volatile
    private var attributeDisplayNameToShard = mapOf<String, String>()

    @Volatile
    private var unconsumableAttributes = emptySet<String>()

    fun cancelAll() {
        requests.cancelAll()
    }

    fun ensureLoaded(): Boolean {
        if (attributeInfo.isNotEmpty()) return true
        loadLocalConstants()
        if (attributeInfo.isNotEmpty()) return true
        loadConstants()
        return false
    }

    fun activeLevel(storage: Map<String, ProfileStorage.AttributeShardData>, abilityName: String): Int {
        val shardName = attributeAbilityNameToShard[abilityName] ?: return 0
        return if (storage[shardName]?.enabled == true) level(storage, shardName) else 0
    }

    fun internalNameByAbilityName(abilityName: String): String? {
        if (!ensureLoaded()) return null
        val cleanName = abilityName.trim()
        val shardName = attributeAbilityNameToShard[cleanName]
            ?: attributeDisplayNameToShard[cleanName]
            ?: return null
        return attributeInfo[shardName]?.internalName
    }

    fun internalNameByDisplayName(shardName: String): String? {
        if (!ensureLoaded()) return null
        val cleanName = shardName.trim().removeSuffix(" Shard").trim()
        val bazaarName = attributeDisplayNameToShard[cleanName]
            ?: attributeAbilityNameToShard[cleanName]
            ?: return null
        return attributeInfo[bazaarName]?.internalName
    }

    fun shardNameByInternalName(internalName: String): String? = internalNameToShard[internalName]

    fun isConsumable(shardName: String): Boolean = shardName !in unconsumableAttributes

    fun findTotalAmount(shardName: String, currentTier: Int, toNextTier: Int): Int? {
        val rarity = attributeInfo[shardName]?.rarity ?: return null
        val tierLevelling = attributeLevelling[rarity] ?: return 0
        val cumulativeAmount = tierLevelling.take((currentTier + 1).coerceIn(0, tierLevelling.size)).sum()
        return (cumulativeAmount - toNextTier).coerceAtLeast(0)
    }

    fun consumableShards(): List<NeuAttributeShardData> =
        attributeInfo.values.filter { it.bazaarName !in unconsumableAttributes }

    fun shardByDisplayOrAbilityName(name: String): String? =
        attributeDisplayNameToShard[name] ?: attributeAbilityNameToShard[name]

    fun internalNameByBazaarName(shardName: String): String? = attributeInfo[shardName]?.internalName

    fun internalNameFromKnownShardId(internalName: String): String? {
        val normalized = internalName.uppercase(Locale.US).replace(':', '-')
        val attributeInternalName = normalized.normalizeAttributeShardInternalName()
        if (attributeInternalName.startsWith("ATTRIBUTE_SHARD_")) return attributeInternalName
        return attributeInfo[normalized]?.internalName
    }

    private fun level(storage: Map<String, ProfileStorage.AttributeShardData>, shardName: String): Int {
        val rarity = attributeInfo[shardName]?.rarity ?: return 0
        val levelling = attributeLevelling[rarity] ?: return 0
        val totalAmount = storage[shardName]?.amountSyphoned ?: return 0
        var tier = 0
        var cumulativeCount = 0
        for (amount in levelling) {
            cumulativeCount += amount
            if (cumulativeCount > totalAmount) break
            tier++
        }
        return tier
    }

    private fun loadConstants() {
        if (constantsLastFailure.passedSince() < CONSTANTS_RETRY_DELAY) return
        if (!loadingConstants.compareAndSet(false, true)) return
        request(ATTRIBUTE_SHARDS_URL)
            .thenApply { gson.fromJson(it, SkysoftAttributeShardRepoJson::class.java) }
            .whenComplete { data, error ->
                SkysoftErrorBoundary.run("Attribute Shard constants async completion") {
                    try {
                        if (error == null && data != null) {
                            applyConstants(data)
                        } else {
                            constantsLastFailure = ElapsedTimeMark.now()
                            SkysoftMod.LOGGER.warn("Failed to load attribute shard constants", error)
                        }
                    } finally {
                        loadingConstants.set(false)
                    }
                }
            }
    }

    private fun loadLocalConstants() {
        for (path in localAttributeShardPaths()) {
            if (!Files.isRegularFile(path)) continue
            val data = runCatching {
                Files.newBufferedReader(path).use { reader ->
                    gson.fromJson(reader, SkysoftAttributeShardRepoJson::class.java)
                }
            }.getOrNull() ?: continue
            applyConstants(data)
            return
        }
    }

    private fun localAttributeShardPaths(): List<Path> {
        val gameDir = FabricLoader.getInstance().gameDir
        return listOf(
            gameDir.resolve("config/notenoughupdates/repo/constants/attribute_shards.json"),
            gameDir.resolve("config/skyblocker/item-repo/constants/attribute_shards.json"),
        )
    }

    private fun applyConstants(data: SkysoftAttributeShardRepoJson) {
        attributeLevelling = data.attributeLevelling
        unconsumableAttributes = data.unconsumableAttributes.toSet()
        attributeInfo = data.attributes.associateBy { it.bazaarName }
        internalNameToShard = buildMap {
            for (attribute in data.attributes) {
                put(attribute.internalName, attribute.bazaarName)
                put(attribute.internalName.substringBefore(';'), attribute.bazaarName)
            }
        }
        attributeAbilityNameToShard = data.attributes.associate { it.abilityName to it.bazaarName }
        attributeDisplayNameToShard = data.attributes.associate { it.displayName to it.bazaarName }
    }

    private fun request(url: String) = requests.getString(url)

    private val CONSTANTS_RETRY_DELAY = 30.seconds

    private fun String.normalizeAttributeShardInternalName(): String {
        val normalized = uppercase(Locale.US).replace(':', '-')
        val baseName = normalized.substringBefore(';')
        return if (baseName.startsWith("ATTRIBUTE_SHARD_")) "$baseName;1" else normalized
    }
}

private object AttributeShardItemResolver {
    fun internalNameOrNull(item: ItemStack, inventoryName: String?): String? {
        if (isAttributeShardInventoryName(inventoryName)) {
            resolveContextualShardInternalName(item, inventoryName)?.let { return it }
        }

        item.skyBlockId()
            ?.let { AttributeShardConstants.internalNameFromKnownShardId(it) }
            ?.let { return it }

        val extraAttributes = item.extraAttributes() ?: return resolveContextualShardInternalName(item, inventoryName)
        val id = extraAttributes.getStringOrNull("id")?.uppercase(Locale.US)?.replace(':', '-')
        return if (id == "ATTRIBUTE_SHARD") {
            extraAttributes.getCompoundOrNull("attributes")
                ?.keySet()
                ?.singleOrNull()
                ?.let { attributeName -> "ATTRIBUTE_SHARD_${attributeName.uppercase(Locale.US)};1" }
                ?: resolveContextualShardInternalName(item, inventoryName)
        } else {
            id?.let { AttributeShardConstants.internalNameFromKnownShardId(it) }
                ?: resolveContextualShardInternalName(item, inventoryName)
        }
    }

    fun isAttributeMenuName(inventoryName: String?): Boolean =
        inventoryName == "Attribute Menu" || inventoryName?.startsWith("Attribute Menu ") == true

    fun hasAttributeStateLine(item: ItemStack): Boolean =
        item.loreLines().any { line ->
            attributeStatePattern.matchEntire(line) != null ||
                cleanAttributeStatePattern.matchEntire(line.removeColor()) != null
        }

    fun isEnabledAttributeState(state: String): Boolean =
        state.trim().equals("Yes", ignoreCase = true) ||
            state.trim().equals("On", ignoreCase = true) ||
            state.trim().equals("Enabled", ignoreCase = true)

    private fun resolveContextualShardInternalName(item: ItemStack, inventoryName: String?): String? {
        val cleanName = item.formattedHoverName().cleanSkyBlockText().removeSuffix(" NEW SHARD").trim()
        val shardName = when {
            isAttributeMenuName(inventoryName) -> findAttributeMenuShardName(item, cleanName)
            inventoryName == "Hunting Box" -> findHuntingBoxShardName(item, cleanName)
            else -> null
        } ?: return null
        return AttributeShardConstants.internalNameByBazaarName(shardName)
    }

    private fun findAttributeMenuShardName(item: ItemStack, cleanName: String): String? =
        displayNameCandidates(cleanName).firstNotNullOfOrNull { AttributeShardConstants.shardByDisplayOrAbilityName(it) }
            ?: item.loreLines().firstNotNullOfOrNull { line ->
                val cleanLine = line.cleanSkyBlockText()
                val source = attributeSourcePattern.matchEntire(line)?.group("source")
                    ?: cleanAttributeSourcePattern.matchEntire(cleanLine)?.group("source")
                source?.let { AttributeShardConstants.shardByDisplayOrAbilityName(it) }
                    ?: displayNameCandidates(cleanLine).firstNotNullOfOrNull {
                        AttributeShardConstants.shardByDisplayOrAbilityName(it)
                    }
            }

    private fun findHuntingBoxShardName(item: ItemStack, cleanName: String): String? =
        displayNameCandidates(cleanName).firstNotNullOfOrNull { AttributeShardConstants.shardByDisplayOrAbilityName(it) }
            ?: item.loreLines().firstNotNullOfOrNull { line ->
                displayNameCandidates(line.cleanSkyBlockText()).firstNotNullOfOrNull {
                    AttributeShardConstants.shardByDisplayOrAbilityName(it)
                }
            }

    private fun displayNameCandidates(cleanName: String): List<String> =
        listOfNotNull(
            cleanName,
            cleanAttributeShardNamePattern.matchEntire(cleanName)?.group("name")?.trim(),
            cleanAttributeShardNameLorePattern.matchEntire(cleanName)?.group("name")?.trim(),
        ).distinct()

    private fun isAttributeShardInventoryName(inventoryName: String?): Boolean =
        isAttributeMenuName(inventoryName) || inventoryName == "Hunting Box"
}

private data class SkysoftAttributeShardRepoJson(
    @SerializedName("attribute_levelling") val attributeLevelling: Map<SkyBlockRarity, List<Int>> = emptyMap(),
    @SerializedName("unconsumable_attributes") val unconsumableAttributes: List<String> = emptyList(),
    val attributes: List<NeuAttributeShardData> = emptyList(),
)

private data class NeuAttributeShardData(
    val bazaarName: String = "",
    val displayName: String = "",
    val rarity: SkyBlockRarity = SkyBlockRarity.COMMON,
    val internalName: String = "",
    val abilityName: String = "",
)
