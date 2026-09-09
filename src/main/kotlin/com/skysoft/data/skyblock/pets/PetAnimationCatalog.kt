package com.skysoft.data.skyblock.pets

internal class PetAnimationCatalog(val data: PetAnimationsJson) {
    private val textureMatches = HashMap<Pair<String, String>, AnimatedSkinJson?>()

    fun resolve(skinInternalName: String, displayIconTexture: String?): AnimatedSkinJson? {
        if (displayIconTexture != null) {
            return matchingTexture(skinInternalName, displayIconTexture)
        }
        data.skins[skinInternalName]?.let { return it }
        return data.skins.asSequence()
            .filter { (internalName) -> internalName.startsWith("${skinInternalName}_LOCAL_") }
            .map { it.value }
            .singleOrNull()
    }

    private fun matchingTexture(skinInternalName: String, displayIconTexture: String): AnimatedSkinJson? {
        val texture = PetSkins.textureIdentity(displayIconTexture)
        val key = skinInternalName to texture
        return synchronized(textureMatches) {
            if (textureMatches.containsKey(key)) return@synchronized textureMatches[key]
            val match = data.skins.asSequence()
                .filter { (internalName) ->
                    internalName == skinInternalName || internalName.startsWith("${skinInternalName}_")
                }
                .map { it.value }
                .firstOrNull { animation ->
                    (animation.matchTextures + animation.textures).any { PetSkins.textureIdentity(it) == texture }
                }
            textureMatches[key] = match
            match
        }
    }
}
