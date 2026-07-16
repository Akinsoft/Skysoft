package com.skysoft.utils

import com.skysoft.SkysoftMod
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

object SoundUtilities {
    private val clickSound by lazy { createSound("ui.button.click", 1f) }
    private val itemProtectedSound by lazy { createSound("entity.ender_eye.death", 1f, 1f, 4096L) }
    private val itemUnprotectedSound by lazy { createSound("entity.ender_eye.death", 1f, 1f, 0L) }

    fun playClickSound() {
        playSound(clickSound)
    }

    fun playItemProtectedSound() {
        playSound(itemProtectedSound)
    }

    fun playItemUnprotectedSound() {
        playSound(itemUnprotectedSound)
    }

    private fun createSound(name: String, pitch: Float, volume: Float = 50f, seed: Long? = null): SoundInstance {
        val identifier = Identifier.parse(name.replace(Regex("[^a-z0-9/._-]"), ""))
        if (seed == null) return SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(identifier), pitch, volume)
        return SimpleSoundInstance(
            identifier,
            SoundSource.UI,
            volume,
            pitch,
            RandomSource.create(seed),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true,
        )
    }

    private fun playSound(sound: SoundInstance) {
        try {
            Minecraft.getInstance().soundManager.play(sound)
        } catch (e: IllegalArgumentException) {
            if (e.message?.startsWith("value already present:") == true) return
            SkysoftMod.LOGGER.warn("Failed to play sound {}", sound.identifier, e)
        }
    }
}
