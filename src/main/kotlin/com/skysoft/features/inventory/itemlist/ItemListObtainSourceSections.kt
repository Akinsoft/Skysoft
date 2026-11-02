package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.SkyBlockObtainInfo
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType

internal fun SkyBlockObtainInfo.withOnlyMethodsMatching(markers: List<String>): SkyBlockObtainInfo? =
    withMethods { method -> method.containsAnyMarker(markers) }

internal fun SkyBlockObtainInfo.withoutSpecializedMethods(recipes: List<SkyBlockRecipe>): SkyBlockObtainInfo? {
    val hasFusionRecipe = recipes.any { it.type == SkyBlockRecipeType.ATTRIBUTE_FUSION }
    return withMethods { method ->
        !method.containsAnyMarker(SPECIALIZED_DETAIL_MARKERS) && !(hasFusionRecipe && method.startsWith("Fusing "))
    }
}

internal fun String.containsAnyMarker(markers: List<String>): Boolean =
    markers.any { marker -> contains(marker, ignoreCase = true) }

private fun SkyBlockObtainInfo.withMethods(isKept: (String) -> Boolean): SkyBlockObtainInfo? {
    val methods = summary.split(METHOD_SEPARATOR).filter(isKept)
    return methods.takeIf(List<String>::isNotEmpty)?.let { copy(summary = it.joinToString(METHOD_SEPARATOR)) }
}

internal val SPECIALIZED_DETAIL_MARKERS = listOf("Pocket Black Hole", "Salts")

private const val METHOD_SEPARATOR = "; "
