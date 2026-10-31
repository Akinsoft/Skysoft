package com.skysoft.data.skyblock.pets

import com.google.gson.Gson
import com.skysoft.utils.BundledResources

internal object PetRepoConstants {
    val data: SkysoftPetsRepoJson by lazy {
        BundledResources.read(PET_CONSTANTS_RESOURCE, "pet constants") { reader ->
            requireNotNull(Gson().fromJson(reader, SkysoftPetsRepoJson::class.java)) {
                "Bundled pet constants are null"
            }
        }
    }

    private const val PET_CONSTANTS_RESOURCE = "/assets/skysoft/data/pet_constants.json"
}
