package com.skysoft.features.pets

internal class PetAnimationLoopRecorder {
    private val frames = mutableListOf<ObservedPetAnimationFrame>()
    private var previousTexture: String? = null
    private var startingTexture: String? = null

    fun observe(texture: String): ConfirmedPetAnimationLoop? {
        val previous = previousTexture
        if (previous == null) {
            previousTexture = texture
            return null
        }
        if (previous == texture) {
            if (frames.isNotEmpty()) {
                val current = frames.last()
                frames[frames.lastIndex] = current.copy(ticks = current.ticks + 1)
            }
            return null
        }

        previousTexture = texture
        val start = startingTexture
        if (start == null) {
            startingTexture = texture
            frames += ObservedPetAnimationFrame(texture, 1)
            return null
        }
        if (texture == start && frames.hasCompleteLoop()) {
            return ConfirmedPetAnimationLoop(
                textures = frames.map { it.texture },
                ticksPerTexture = frames.map { it.ticks },
            )
        }
        frames += ObservedPetAnimationFrame(texture, 1)
        return null
    }

    private fun List<ObservedPetAnimationFrame>.hasCompleteLoop(): Boolean =
        size >= MINIMUM_LOOP_FRAMES && map { it.texture }.distinct().size >= MINIMUM_DISTINCT_TEXTURES

    private companion object {
        const val MINIMUM_LOOP_FRAMES = 2
        const val MINIMUM_DISTINCT_TEXTURES = 2
    }
}

internal data class ObservedPetAnimationFrame(
    val texture: String,
    val ticks: Int,
)

internal data class ConfirmedPetAnimationLoop(
    val textures: List<String>,
    val ticksPerTexture: List<Int>,
)
