package com.skysoft.features.pets

import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.config.features.pets.display.text.PetTextDisplaySettings
import com.skysoft.config.features.pets.display.text.PetTextConfig
import com.skysoft.data.StoredPetData
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.roundTo
import com.skysoft.utils.NumberUtilities.shortFormat
import java.util.Locale

private typealias TextElement = PetTextConfig.TextElement
private typealias NumberFormatEntry = PetTextConfig.NumberFormatEntry

object PetDisplayTextFormatter {
    fun formatElement(
        petData: StoredPetData,
        textElement: TextElement,
        textConfig: PetTextDisplaySettings,
    ): String? = with(petData) {
        val xpFormat = textConfig.xpFormat.get()
        when (textElement) {
            TextElement.PET_NAME -> getUserFriendlyName(textConfig.nameLevel.get(), textConfig.nameSkinSymbol.get())
            TextElement.HELD_ITEM -> heldItemInternalName?.let { PetRepository.itemName(it) }
            TextElement.OVERFLOW_XP -> overflowXp.takeIf { it > 0.0 }?.let { "§7+§b${formatExp(it, xpFormat)}" }
            TextElement.TOTAL_XP -> exp?.takeIf { it > 0.0 }?.let { "§b${formatExp(it, xpFormat)}" }
            TextElement.NEXT_LEVEL -> formatNextLevel(petData, textConfig, xpFormat)
        }
    }

    private fun formatNextLevel(
        petData: StoredPetData,
        textConfig: PetTextDisplaySettings,
        xpFormat: NumberFormatEntry,
    ): String? = with(petData) {
        if (level >= PetRepository.getMaxLevel(fauxInternalName)) return null
        val currentExp = exp ?: 0.0
        val currentXpOverLevel = currentExp - currentLevelXp
        val neededXp = nextLevelXp - currentLevelXp
        val percentageFormat = if (textConfig.nextLevelPercent.get()) {
            " §7- §e${formatLevelProgressionPercentage(levelProgressionPercentage)}%"
        } else ""
        return formatExpPair(currentXpOverLevel, neededXp, xpFormat) + percentageFormat
    }

    private fun formatExp(exp: Double, xpFormat: NumberFormatEntry): String = when (xpFormat) {
        NumberFormatEntry.DEFAULT, NumberFormatEntry.UNFORMATTED -> exp.toLong().addSeparators()
        NumberFormatEntry.FORMATTED -> exp.toLong().shortFormat()
    }

    private fun formatLevelProgressionPercentage(percentage: Double): String =
        String.format(
            Locale.US,
            "%.1f",
            percentage.coerceIn(0.0, MAX_PERCENT).roundTo(PERCENT_DECIMAL_PLACES),
        )

    private fun formatExpPair(firstExp: Double, secondExp: Double, xpFormat: NumberFormatEntry): String = when (xpFormat) {
        NumberFormatEntry.DEFAULT -> "§b${firstExp.toLong().addSeparators()}§9/§b${secondExp.toLong().shortFormat()}"
        NumberFormatEntry.FORMATTED -> "§b${firstExp.toLong().shortFormat()}§9/§b${secondExp.toLong().shortFormat()}"
        NumberFormatEntry.UNFORMATTED -> "§b${firstExp.toLong().addSeparators()}§9/§b${secondExp.toLong().addSeparators()}"
    }

    private const val MAX_PERCENT = 100.0
    private const val PERCENT_DECIMAL_PLACES = 1
}
