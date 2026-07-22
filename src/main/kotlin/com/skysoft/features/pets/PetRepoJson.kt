package com.skysoft.features.pets

import com.google.gson.annotations.SerializedName
import com.skysoft.data.skyblock.SkyBlockRarity

internal data class SkysoftPetsRepoJson(
    @SerializedName("pet_levels") val basePetLeveling: List<Int> = emptyList(),
    @SerializedName("custom_pet_leveling") val customPetLeveling: Map<String, NeuPetData> = emptyMap(),
    @SerializedName("pet_types") val petTypes: Map<String, String> = emptyMap(),
    @SerializedName("id_to_display_name") val displayNameMap: Map<String, String> = emptyMap(),
    @SerializedName("pet_item_display_name_to_id") val petItemResolution: Map<String, String> = emptyMap(),
)

internal data class NeuPetData(
    @SerializedName("pet_levels") val petLevels: List<Int>? = null,
    @SerializedName("max_level") val maxLevel: Int? = null,
    @SerializedName("rarity_offset") val rarityOffset: Map<SkyBlockRarity, Int>? = null,
    @SerializedName("xp_multiplier") val xpMultiplier: Double? = null,
)

internal data class PetAnimationsJson(
    val skins: Map<String, AnimatedSkinJson> = emptyMap(),
)

internal data class AnimatedSkinJson(
    val ticks: Int = 1,
    val ticksPerTexture: List<Int> = emptyList(),
    val matchTextures: List<String> = emptyList(),
    val textures: List<String> = emptyList(),
)

internal data class GithubTreeJson(
    val tree: List<GithubTreeEntry> = emptyList(),
)

internal data class GithubTreeEntry(
    val path: String = "",
    val type: String = "",
)

internal data class SkysoftNeuItemJson(
    @SerializedName("itemid") val itemId: String = "minecraft:stone",
    @SerializedName("displayname") val displayName: String? = null,
    @SerializedName("nbttag") val nbtTag: String? = null,
    val lore: List<String> = emptyList(),
    @SerializedName("internalname") val internalName: String = "",
)
