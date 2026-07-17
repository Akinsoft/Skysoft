package com.skysoft.features.pets

import com.google.gson.JsonObject
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

internal object PetSkins {
    fun load() {
        if (PetRepoCache.petAnimations == null) {
            val stream = requireNotNull(PetSkins::class.java.getResourceAsStream(ANIMATIONS_RESOURCE)) {
                "Missing bundled pet animations"
            }
            val animations = stream.bufferedReader().use { reader ->
                PetRepoCache.gson.fromJson(reader, PetAnimationsJson::class.java)
            }
            require(animations.skins.isNotEmpty()) { "Bundled pet animations are empty" }
            PetRepoCache.petAnimations = animations
        }
        loadLearned()
    }

    fun animatedTexture(skinInternalName: String): String? =
        animated(skinInternalName)?.textures?.firstOrNull()

    fun animated(skinInternalName: String, displayIconTexture: String? = null): AnimatedSkinJson? {
        return bundled(skinInternalName, displayIconTexture)
            ?: learned(skinInternalName, displayIconTexture)
    }

    fun hasBundled(skinInternalName: String, displayIconTexture: String?): Boolean =
        bundled(skinInternalName, displayIconTexture) != null

    fun hasLearned(skinInternalName: String, displayIconTexture: String?): Boolean =
        learned(skinInternalName, displayIconTexture) != null

    fun storeLearned(
        skinInternalName: String,
        displayIconTexture: String,
        animation: AnimatedSkinJson,
    ): Result<String> = runCatching {
        val key = learnedAnimationKey(skinInternalName, displayIconTexture)
        val learned = PetRepoCache.learnedPetAnimations.skins.toMutableMap()
        learned[key] = animation.copy(
            matchTextures = (animation.matchTextures + displayIconTexture).distinct(),
        )
        val updated = PetAnimationsJson(learned.toSortedMap())
        SkysoftConfigFiles.writeStringSafely(
            SkysoftConfigFiles.learnedPetAnimations,
            PetRepoCache.gson.toJson(updated),
        )
        PetRepoCache.learnedPetAnimations = updated
        key
    }

    internal fun textureIdentity(texture: String): String {
        val encoded = texture.substringAfter(':')
        val decoded = runCatching {
            String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrNull() ?: return encoded
        val url = runCatching {
            PetRepoCache.gson.fromJson(decoded, JsonObject::class.java)
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .asString
        }.getOrNull() ?: return encoded
        return url.substringAfterLast('/')
    }

    private fun bundled(skinInternalName: String, displayIconTexture: String?): AnimatedSkinJson? =
        PetRepoCache.petAnimations?.let { animations ->
            resolve(animations, "bundled", skinInternalName, displayIconTexture)
        }

    private fun learned(skinInternalName: String, displayIconTexture: String?): AnimatedSkinJson? =
        resolve(PetRepoCache.learnedPetAnimations, "learned", skinInternalName, displayIconTexture)

    private fun resolve(
        animations: PetAnimationsJson,
        source: String,
        skinInternalName: String,
        displayIconTexture: String?,
    ): AnimatedSkinJson? {
        if (displayIconTexture != null) {
            return animationMatchingTexture(animations, source, skinInternalName, displayIconTexture)
        }
        animations.skins[skinInternalName]?.let { return it }
        return animations.skins.asSequence()
            .filter { (internalName) -> internalName.startsWith("${skinInternalName}_LOCAL_") }
            .map { it.value }
            .singleOrNull()
    }

    private fun animationMatchingTexture(
        animated: PetAnimationsJson,
        source: String,
        skinInternalName: String,
        displayIconTexture: String,
    ): AnimatedSkinJson? {
        val texture = textureIdentity(displayIconTexture)
        val cacheKey = "$source:$skinInternalName:$texture"
        PetRepoCache.animatedSkinMatches[cacheKey]?.let { return it }
        if (!PetRepoCache.missingAnimatedSkinMatches.add(cacheKey)) return null
        val match = animated.skins.asSequence()
            .filter { (internalName) ->
                internalName == skinInternalName || internalName.startsWith("${skinInternalName}_")
            }
            .map { it.value }
            .firstOrNull { animation ->
                (animation.matchTextures + animation.textures).any { textureIdentity(it) == texture }
            } ?: return null
        PetRepoCache.missingAnimatedSkinMatches.remove(cacheKey)
        PetRepoCache.animatedSkinMatches[cacheKey] = match
        return match
    }

    private fun learnedAnimationKey(skinInternalName: String, displayIconTexture: String): String {
        val suffix = textureIdentity(displayIconTexture)
            .filter(Char::isLetterOrDigit)
            .takeLast(LEARNED_KEY_SUFFIX_LENGTH)
            .uppercase()
        return "${skinInternalName}_LOCAL_$suffix"
    }

    private fun loadLearned() {
        val path = SkysoftConfigFiles.learnedPetAnimations
        if (!SkysoftConfigFiles.hasFileOrBackup(path)) return
        runCatching {
            SkysoftConfigFiles.readWithBackup(path) { source ->
                Files.newBufferedReader(source).use { reader ->
                    requireNotNull(PetRepoCache.gson.fromJson(reader, PetAnimationsJson::class.java)) {
                        "Learned pet animations are null"
                    }
                }
            }
        }.onSuccess { learned ->
            PetRepoCache.learnedPetAnimations = learned
        }.onFailure { error ->
            SkysoftMod.LOGGER.error("Failed to load learned pet animations from $path", error)
        }
    }

    fun isSkinForPet(internalName: String, properName: String): Boolean {
        val skinName = internalName.removePrefix("PET_SKIN_")
        return skinNamePrefixes(properName).any { prefix ->
            skinName == prefix || skinName.startsWith("${prefix}_")
        }
    }

    private fun skinNamePrefixes(properName: String): List<String> =
        if (properName == "PHOENIX") listOf("PHOENIX", "PHEONIX") else listOf(properName)

    private const val ANIMATIONS_RESOURCE = "/assets/skysoft/data/pet_animations.json"
    private const val LEARNED_KEY_SUFFIX_LENGTH = 16
}
