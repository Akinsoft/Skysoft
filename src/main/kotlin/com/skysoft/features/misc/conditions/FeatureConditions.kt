package com.skysoft.features.misc.conditions

import com.mojang.brigadier.Command
import com.skysoft.config.FeatureCondition
import com.skysoft.config.FeatureConditionCombination
import com.skysoft.config.FeatureConditionKind
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockEvent
import com.skysoft.features.pets.CanonicalItemNames
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft

data class FeatureConditionContext(
    val isInSkyBlock: Boolean,
    val island: SkyBlockIsland?,
    val activeEvents: Set<SkyBlockEvent>,
    val heldItemId: String?,
)

object FeatureConditions {
    fun isActivationAllowed(
        combinations: List<FeatureConditionCombination>,
        context: FeatureConditionContext,
        isConditionActivationReversed: Boolean,
    ): Boolean = combinations.isEmpty() || matches(combinations, context) != isConditionActivationReversed

    fun matches(
        combinations: List<FeatureConditionCombination>,
        context: FeatureConditionContext,
    ): Boolean = combinations.isEmpty() || combinations.any { combination ->
        combination.conditions.isNotEmpty() && combination.conditions.all { it.matches(context) }
    }

    fun builtInConditions(): List<FeatureCondition> = buildList {
        SkyBlockEvent.entries.forEach { event ->
            add(FeatureCondition(FeatureConditionKind.EVENT, event.name, "Event: ${event.displayName}"))
        }
        SkyBlockIsland.entries.forEach { island ->
            add(FeatureCondition(FeatureConditionKind.ISLAND, island.name, "On Island: ${island.displayName}"))
        }
    }

    fun FeatureCondition.key(): String = "${kind.name}:${value.normalizedValue()}"

    fun FeatureCondition.copyCondition(): FeatureCondition = FeatureCondition(kind, value, displayName)

    fun FeatureCondition.selectedDisplayName(): String = when (kind) {
        FeatureConditionKind.EVENT -> displayName.removePrefix("Event: ")
        FeatureConditionKind.ISLAND -> displayName.removePrefix("On Island: ")
        FeatureConditionKind.ITEM -> displayName.removePrefix("Holding Item: ")
    }

    private fun FeatureCondition.matches(context: FeatureConditionContext): Boolean = when (kind) {
        FeatureConditionKind.EVENT ->
            context.isInSkyBlock && runCatching { SkyBlockEvent.valueOf(value) }.getOrNull() in context.activeEvents
        FeatureConditionKind.ISLAND -> SkyBlockIsland.getByConditionValue(value) == context.island
        FeatureConditionKind.ITEM -> context.heldItemId?.normalizedValue() == value.normalizedValue()
    }

    private fun String.normalizedValue(): String = trim().uppercase(Locale.US)
}

class FeatureItemConditionCatalogue {
    private val itemNames = linkedMapOf<String, String>()

    fun startSession(combinations: List<FeatureConditionCombination>) {
        itemNames.clear()
        rememberActiveItems(combinations)
    }

    fun rememberActiveItems(combinations: List<FeatureConditionCombination>) {
        combinations.asSequence()
            .flatMap { it.conditions.asSequence() }
            .filter { it.kind == FeatureConditionKind.ITEM }
            .forEach { condition -> itemNames.putIfAbsent(condition.value, condition.displayName) }
    }

    fun addActiveItem(
        combinations: MutableList<FeatureConditionCombination>,
        itemId: String,
        displayName: String,
    ): FeatureItemAddResult {
        if (itemNames.putIfAbsent(itemId, displayName) != null) return FeatureItemAddResult.ALREADY_AVAILABLE
        combinations.add(
            0,
            FeatureConditionCombination(
                mutableListOf(FeatureCondition(FeatureConditionKind.ITEM, itemId, displayName)),
            ),
        )
        return FeatureItemAddResult.ADDED
    }

    fun contains(itemId: String): Boolean = itemId in itemNames

    fun conditions(): List<FeatureCondition> = itemNames.map { (itemId, displayName) ->
        FeatureCondition(FeatureConditionKind.ITEM, itemId, displayName)
    }
}

enum class FeatureItemAddResult {
    ADDED,
    ALREADY_AVAILABLE,
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

enum class FeatureItemRejection {
    EMPTY_HAND,
    MISSING_ID,
    NAME_UNAVAILABLE,
}

object FeatureItemConditionCommand {
    fun addHeldItem(
        source: FabricClientCommandSource,
        featureName: String,
        combinations: MutableList<FeatureConditionCombination>,
        catalogue: FeatureItemConditionCatalogue,
        onChanged: () -> Unit,
    ): Int {
        val stack = Minecraft.getInstance().player?.mainHandItem
        val rawItemId = stack?.skyBlockId()
        return when (
            val result = FeatureItemConditionInput.resolve(
                isEmpty = stack == null || stack.isEmpty,
                itemId = rawItemId,
                canonicalName = rawItemId?.let(CanonicalItemNames::resolve),
            )
        ) {
            is FeatureItemInputResult.Rejected -> reject(source, result.reason)
            is FeatureItemInputResult.Ready -> {
                val label = "Holding Item: ${result.cleanName}"
                if (
                    catalogue.addActiveItem(combinations, result.itemId, label) ==
                    FeatureItemAddResult.ALREADY_AVAILABLE
                ) {
                    SkysoftChat.error(source, "That item is already available in $featureName combinations.")
                    return 0
                }
                onChanged()
                SkysoftConfigGui.config().saveNow()
                SkysoftChat.feedback(source, "Added ${result.cleanName} as a new $featureName combination.")
                Command.SINGLE_SUCCESS
            }
        }
    }

    private fun reject(source: FabricClientCommandSource, rejection: FeatureItemRejection): Int {
        val message = when (rejection) {
            FeatureItemRejection.EMPTY_HAND -> "Hold a SkyBlock item first."
            FeatureItemRejection.MISSING_ID -> "The held item has no SkyBlock item ID."
            FeatureItemRejection.NAME_UNAVAILABLE ->
                "The clean item name is still loading. Try the command again shortly."
        }
        SkysoftChat.error(source, message)
        return 0
    }
}

class FeatureConditionVersion {
    @Volatile
    var version: Long = 0
        private set

    fun markChanged() {
        version++
    }
}

internal data class FeatureConditionActivationKey(
    val locationVersion: Long,
    val eventVersion: Long,
    val rulesVersion: Long,
    val heldItemId: String?,
    val isConditionActivationReversed: Boolean = false,
)

internal class FeatureConditionActivationCache {
    private var cachedKey: FeatureConditionActivationKey? = null
    private var cachedValue = false

    fun isActivationAllowed(key: FeatureConditionActivationKey, calculateIsActivationAllowed: () -> Boolean): Boolean {
        if (key == cachedKey) return cachedValue
        cachedKey = key
        cachedValue = calculateIsActivationAllowed()
        return cachedValue
    }
}
