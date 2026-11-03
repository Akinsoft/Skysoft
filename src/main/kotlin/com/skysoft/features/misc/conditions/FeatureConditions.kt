package com.skysoft.features.misc.conditions

import com.mojang.brigadier.Command
import com.skysoft.config.FeatureCondition
import com.skysoft.config.FeatureConditionCombination
import com.skysoft.config.FeatureConditionKind
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockEvent
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft

object FeatureConditions {
    fun builtInConditions(): List<FeatureCondition> = buildList {
        SkyBlockEvent.entries.forEach { event ->
            add(FeatureCondition(FeatureConditionKind.EVENT, event.name, "$EVENT_PREFIX${event.displayName}"))
        }
        SkyBlockIsland.entries.forEach { island ->
            add(FeatureCondition(FeatureConditionKind.ISLAND, island.name, "$ISLAND_PREFIX${island.displayName}"))
        }
    }

    fun FeatureCondition.key(): String = "${kind.name}:${value.normalizedValue()}"

    fun FeatureCondition.copyCondition(): FeatureCondition = FeatureCondition(kind, value, displayName)

    fun FeatureCondition.selectedDisplayName(): String = displayName.removePrefix(
        when (kind) {
            FeatureConditionKind.EVENT -> EVENT_PREFIX
            FeatureConditionKind.ISLAND -> ISLAND_PREFIX
            FeatureConditionKind.ITEM -> ITEM_PREFIX
        },
    )

    internal fun isActivationAllowed(
        combinations: List<FeatureConditionCombination>,
        context: FeatureConditionContext,
        isConditionActivationReversed: Boolean,
    ): Boolean = combinations.isEmpty() || combinations.any { combination ->
        combination.conditions.isNotEmpty() && combination.conditions.all { it.matches(context) }
    } != isConditionActivationReversed

    private fun FeatureCondition.matches(context: FeatureConditionContext): Boolean = when (kind) {
        FeatureConditionKind.EVENT ->
            context.isInSkyBlock && runCatching { SkyBlockEvent.valueOf(value) }.getOrNull() in context.activeEvents
        FeatureConditionKind.ISLAND ->
            context.island != null && SkyBlockIsland.getByConditionValue(value) == context.island
        FeatureConditionKind.ITEM -> context.heldItemId?.normalizedValue() == value.normalizedValue()
    }

    private fun String.normalizedValue(): String = trim().uppercase(Locale.US)
}

internal data class FeatureConditionContext(
    val isInSkyBlock: Boolean,
    val island: SkyBlockIsland?,
    val activeEvents: Set<SkyBlockEvent>,
    val heldItemId: String?,
)

internal class FeatureConditionState {
    private val itemNames = linkedMapOf<String, String>()
    private var cachedActivation: CachedActivation? = null
    @Volatile
    private var version = 0L

    fun startSession(combinations: List<FeatureConditionCombination>) {
        val conditions = combinations.flatMap { it.conditions }
        conditions.filter { it.kind == FeatureConditionKind.ISLAND }.forEach { condition ->
            val island = SkyBlockIsland.getByConditionValue(condition.value) ?: return@forEach
            condition.value = island.name
            condition.displayName = "$ISLAND_PREFIX${island.displayName}"
        }
        itemNames.clear()
        conditions.filter { it.kind == FeatureConditionKind.ITEM }.forEach { condition ->
            itemNames.putIfAbsent(condition.value, condition.displayName)
        }
    }

    fun isActivationAllowed(
        combinations: List<FeatureConditionCombination>,
        heldItemId: String?,
        isConditionActivationReversed: Boolean,
    ): Boolean {
        val key = ActivationKey(
            locationVersion = HypixelLocationState.locationVersion,
            eventVersion = SkyBlockEventState.version,
            rulesVersion = version,
            heldItemId = heldItemId,
            isConditionActivationReversed = isConditionActivationReversed,
        )
        cachedActivation?.takeIf { it.key == key }?.let { return it.isActivationAllowed }
        val isActivationAllowed = FeatureConditions.isActivationAllowed(
            combinations,
            FeatureConditionContext(
                isInSkyBlock = HypixelLocationState.inSkyBlock,
                island = HypixelLocationState.currentIsland,
                activeEvents = SkyBlockEventState.activeEvents(),
                heldItemId = heldItemId,
            ),
            isConditionActivationReversed,
        )
        cachedActivation = CachedActivation(key, isActivationAllowed)
        return isActivationAllowed
    }

    fun addHeldItem(
        source: FabricClientCommandSource,
        featureName: String,
        combinations: MutableList<FeatureConditionCombination>,
    ): Int {
        val stack = Minecraft.getInstance().player?.mainHandItem
        val rawItemId = stack?.skyBlockId()
        val item = when (
            val result = FeatureItemConditionInput.resolve(
                isEmpty = stack == null || stack.isEmpty,
                itemId = rawItemId,
                canonicalName = SkyBlockItemNames.displayName(rawItemId),
            )
        ) {
            is FeatureItemInputResult.Rejected -> {
                SkysoftChat.error(source, result.reason.message)
                return 0
            }
            is FeatureItemInputResult.Ready -> result
        }
        val label = "$ITEM_PREFIX${item.cleanName}"
        if (itemNames.putIfAbsent(item.itemId, label) != null) {
            SkysoftChat.error(source, "That item is already available in $featureName combinations.")
            return 0
        }
        combinations.add(
            0,
            FeatureConditionCombination(mutableListOf(FeatureCondition(FeatureConditionKind.ITEM, item.itemId, label))),
        )
        markChanged()
        SkysoftConfigGui.config().saveNow()
        SkysoftChat.feedback(source, "Added ${item.cleanName} as a new $featureName combination.")
        return Command.SINGLE_SUCCESS
    }

    fun itemConditions(): List<FeatureCondition> = itemNames.map { (itemId, displayName) ->
        FeatureCondition(FeatureConditionKind.ITEM, itemId, displayName)
    }

    fun markChanged() {
        version++
    }

    private data class ActivationKey(
        val locationVersion: Long,
        val eventVersion: Long,
        val rulesVersion: Long,
        val heldItemId: String?,
        val isConditionActivationReversed: Boolean,
    )

    private data class CachedActivation(val key: ActivationKey, val isActivationAllowed: Boolean)
}

object FeatureItemConditionInput {
    fun resolve(isEmpty: Boolean, itemId: String?, canonicalName: String?): FeatureItemInputResult {
        if (isEmpty) return FeatureItemInputResult.Rejected(FeatureItemRejection.EMPTY_HAND)
        val normalizedId = itemId?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
            ?: return FeatureItemInputResult.Rejected(FeatureItemRejection.MISSING_ID)
        val cleanName = canonicalName?.cleanSkyBlockText()?.takeIf { it.isNotEmpty() }
            ?: return FeatureItemInputResult.Rejected(FeatureItemRejection.NAME_UNAVAILABLE)
        return FeatureItemInputResult.Ready(normalizedId, cleanName)
    }
}

sealed interface FeatureItemInputResult {
    data class Ready(val itemId: String, val cleanName: String) : FeatureItemInputResult
    data class Rejected(val reason: FeatureItemRejection) : FeatureItemInputResult
}

enum class FeatureItemRejection(val message: String) {
    EMPTY_HAND("Hold a SkyBlock item first."),
    MISSING_ID("The held item has no SkyBlock item ID."),
    NAME_UNAVAILABLE("The clean item name is still loading. Try the command again shortly."),
}

private const val EVENT_PREFIX = "Event: "
private const val ISLAND_PREFIX = "On Island: "
private const val ITEM_PREFIX = "Holding Item: "
