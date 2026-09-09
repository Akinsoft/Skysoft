package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.skysoft.SkysoftMod
import com.skysoft.data.ProfileStorageView
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.PendingHttpRequests
import com.skysoft.utils.net.RefreshSchedule
import com.skysoft.utils.net.isCancellationFailure
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal object AttributeShardConstants {
    private const val ATTRIBUTE_SHARDS_URL =
        "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/attribute_shards.json"

    private val gson = Gson()
    private val requests = PendingHttpRequests()
    private val requestSlot = AsyncRequestSlot<SkysoftAttributeShardRepoJson>()
    private val failureSchedule = RefreshSchedule()

    @Volatile
    var remoteConstantsLoaded = false
        private set

    @Volatile
    private var snapshot = AttributeShardConstantsSnapshot()

    fun cancelAll() {
        requestSlot.cancel()
        requests.cancelAll()
    }

    fun ensureLoaded(): Boolean {
        if (snapshot.attributeInfo.isEmpty()) loadLocalConstants()
        if (!remoteConstantsLoaded) loadConstants()
        return snapshot.attributeInfo.isNotEmpty()
    }

    fun activeLevel(storage: Map<String, ProfileStorageView.AttributeShardData>, abilityName: String): Int {
        val constants = snapshot
        val shardName = constants.attributeAbilityNameToShard[abilityName] ?: return 0
        return if (storage[shardName]?.enabled == true) constants.level(storage, shardName) else 0
    }

    fun internalNameByAbilityName(abilityName: String): String? {
        if (!ensureLoaded()) return null
        val constants = snapshot
        val cleanName = abilityName.trim()
        val shardName = constants.attributeAbilityNameToShard[cleanName]
            ?: constants.attributeDisplayNameToShard[cleanName]
            ?: return null
        return constants.attributeInfo[shardName]?.internalName
    }

    fun internalNameByDisplayName(shardName: String): String? {
        if (!ensureLoaded()) return null
        val constants = snapshot
        val cleanName = shardName.trim().removeSuffix(" Shard").trim()
        val bazaarName = constants.attributeDisplayNameToShard[cleanName]
            ?: constants.attributeAbilityNameToShard[cleanName]
            ?: return null
        return constants.attributeInfo[bazaarName]?.internalName
    }

    fun shardNameByInternalName(internalName: String): String? = snapshot.internalNameToShard[internalName]

    fun bazaarProductAliases(): Map<String, String> = snapshot.internalNameToShard

    fun isConsumable(shardName: String): Boolean = shardName !in snapshot.unconsumableAttributes

    fun findTotalAmount(shardName: String, currentTier: Int, toNextTier: Int): Int? {
        val constants = snapshot
        val rarity = constants.attributeInfo[shardName]?.rarity ?: return null
        val tierLevelling = constants.attributeLevelling[rarity] ?: return 0
        val cumulativeAmount = tierLevelling.take((currentTier + 1).coerceIn(0, tierLevelling.size)).sum()
        return (cumulativeAmount - toNextTier).coerceAtLeast(0)
    }

    fun consumableShardInternalNames(): Map<String, String> = with(snapshot) {
        attributeInfo.values.filter { it.bazaarName !in unconsumableAttributes }
            .associate { it.bazaarName to it.internalName }
    }

    fun shardByDisplayOrAbilityName(name: String): String? = with(snapshot) {
        attributeDisplayNameToShard[name] ?: attributeAbilityNameToShard[name]
    }

    fun internalNameByBazaarName(shardName: String): String? = snapshot.attributeInfo[shardName]?.internalName

    fun internalNameFromKnownShardId(internalName: String): String? {
        val normalized = internalName.normalizeAttributeShardInternalName()
        if (normalized.startsWith("ATTRIBUTE_SHARD_")) return normalized
        return snapshot.attributeInfo[normalized]?.internalName
    }

    private fun AttributeShardConstantsSnapshot.level(
        storage: Map<String, ProfileStorageView.AttributeShardData>,
        shardName: String,
    ): Int {
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
        val now = System.currentTimeMillis()
        if (!failureSchedule.isDue(now)) return
        requestSlot.startIfIdle(
            requestFactory = {
                requests.getString(ATTRIBUTE_SHARDS_URL)
                    .thenApply { gson.fromJson(it, SkysoftAttributeShardRepoJson::class.java) }
            },
        ) { data, error ->
            SkysoftErrorBoundary.run("Attribute Shard constants async completion") {
                if (error == null && data != null) {
                    applyConstants(data)
                    remoteConstantsLoaded = true
                    failureSchedule.reset()
                } else if (error?.isCancellationFailure() != true) {
                    failureSchedule.schedule(System.currentTimeMillis(), CONSTANTS_RETRY_DELAY_MILLIS)
                    SkysoftMod.LOGGER.warn("Failed to load attribute shard constants", error)
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
        snapshot = AttributeShardConstantsSnapshot(
            attributeLevelling = data.attributeLevelling,
            unconsumableAttributes = data.unconsumableAttributes.toSet(),
            attributeInfo = data.attributes.associateBy { it.bazaarName },
            internalNameToShard = buildMap {
                for (attribute in data.attributes) {
                    put(attribute.internalName, attribute.bazaarName)
                    put(attribute.internalName.substringBefore(';'), attribute.bazaarName)
                }
            },
            attributeAbilityNameToShard = data.attributes.associate { it.abilityName to it.bazaarName },
            attributeDisplayNameToShard = data.attributes.associate { it.displayName to it.bazaarName },
        )
    }

    private const val CONSTANTS_RETRY_DELAY_MILLIS = 30_000L

    private fun String.normalizeAttributeShardInternalName(): String {
        val normalized = uppercase(Locale.US).replace(':', '-')
        val baseName = normalized.substringBefore(';')
        return if (baseName.startsWith("ATTRIBUTE_SHARD_")) "$baseName;1" else normalized
    }
}

private data class AttributeShardConstantsSnapshot(
    val attributeLevelling: Map<SkyBlockRarity, List<Int>> = emptyMap(),
    val unconsumableAttributes: Set<String> = emptySet(),
    val attributeInfo: Map<String, NeuAttributeShardData> = emptyMap(),
    val internalNameToShard: Map<String, String> = emptyMap(),
    val attributeAbilityNameToShard: Map<String, String> = emptyMap(),
    val attributeDisplayNameToShard: Map<String, String> = emptyMap(),
)

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
