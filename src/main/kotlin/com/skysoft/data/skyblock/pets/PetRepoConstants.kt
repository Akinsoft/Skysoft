package com.skysoft.data.skyblock.pets

import com.google.gson.Gson

internal object PetRepoConstants {
    val data: SkysoftPetsRepoJson by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream(PET_CONSTANTS_RESOURCE)) {
            "Missing bundled pet constants"
        }
        stream.bufferedReader().use { reader ->
            requireNotNull(Gson().fromJson(reader, SkysoftPetsRepoJson::class.java)) {
                "Bundled pet constants are null"
            }
        }
    }

    private const val PET_CONSTANTS_RESOURCE = "/assets/skysoft/data/pet_constants.json"
}
