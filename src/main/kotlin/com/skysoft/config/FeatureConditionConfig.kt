package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.features.misc.conditions.FeatureConditionState
import com.skysoft.features.misc.conditions.FeatureConditions
import com.skysoft.features.misc.conditions.FeatureConditions.copyCondition
import com.skysoft.features.misc.conditions.FeatureConditions.key
import com.skysoft.features.misc.conditions.FeatureConditions.selectedDisplayName
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.editors.ConfigEditorCombinationsProvider

class FeatureConditionCombination(
    @field:Expose val conditions: MutableList<FeatureCondition> = mutableListOf(),
)

class FeatureCondition(
    @field:Expose var kind: FeatureConditionKind = FeatureConditionKind.ISLAND,
    @field:Expose var value: String = "",
    @field:Expose var displayName: String = "",
)

enum class FeatureConditionKind {
    EVENT,
    ISLAND,
    ITEM,
}

abstract class FeatureCombinationsProvider internal constructor(
    private val state: () -> FeatureConditionState,
    private val offersItemConditions: Boolean,
) : ConfigEditorCombinationsProvider<FeatureConditionCombination, FeatureCondition>(
    FeatureConditionCombination::class.java,
    FeatureCondition::class.java,
) {
    override fun getChoices(): List<FeatureCondition> =
        FeatureConditions.builtInConditions() + if (offersItemConditions) state().itemConditions() else emptyList()

    override fun onChanged() = state().markChanged()

    override fun getEntries(combination: FeatureConditionCombination): MutableList<FeatureCondition> =
        combination.conditions

    override fun createCombination(firstChoice: FeatureCondition): FeatureConditionCombination =
        FeatureConditionCombination(mutableListOf(firstChoice))

    override fun getChoiceLabel(choice: FeatureCondition): StructuredText = StructuredText.of(choice.displayName)

    override fun getSelectedLabel(choice: FeatureCondition): StructuredText =
        StructuredText.of(choice.selectedDisplayName())

    override fun getChoiceId(choice: FeatureCondition): Any = choice.key()

    override fun getChoiceGroup(choice: FeatureCondition): Any = choice.kind

    override fun copyChoice(choice: FeatureCondition): FeatureCondition = choice.copyCondition()
}
