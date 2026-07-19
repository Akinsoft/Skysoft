package com.skysoft.utils

import com.skysoft.SkysoftMod
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import kotlin.random.Random

object SoundUtilities {
    private val clickSound by lazy { createSound("ui.button.click", 1f) }
    private val navigationLeftSound by lazy { createSound(NAVIGATION_LEFT_SOUND_ID, 1f, 1f) }
    private val navigationRightSound by lazy { createSound(NAVIGATION_RIGHT_SOUND_ID, 1f, 1f) }
    private val itemProtectedSound by lazy { createSound("entity.ender_eye.death", 1f, 1f, 4096L) }
    private val itemUnprotectedSound by lazy { createSound("entity.ender_eye.death", 1f, 1f, 0L) }
    private val screenshotShutterSound by lazy { createSound("block.piston.contract", 1.7f, 0.35f) }
    private val screenshotSnapSound by lazy { createSound("entity.item_frame.add_item", 1.2f, 0.55f) }

    fun playClickSound() {
        playSound(clickSound)
    }

    fun playNavigationSound(delta: Int) {
        when {
            delta < 0 -> playSound(navigationLeftSound)
            delta > 0 -> playSound(navigationRightSound)
        }
    }

    fun playRandomNavigationSound() {
        playNavigationSound(if (Random.nextBoolean()) -1 else 1)
    }

    fun playItemProtectedSound() {
        playSound(itemProtectedSound)
    }

    fun playItemUnprotectedSound() {
        playSound(itemUnprotectedSound)
    }

    fun playScreenshotShutterSound() {
        playSound(screenshotShutterSound)
        playSound(screenshotSnapSound)
    }

    fun playUiSound(soundId: String, pitch: Float, volume: Float) {
        playSound(createSound(soundId, pitch, volume))
    }

    private fun createSound(name: String, pitch: Float, volume: Float = 50f, seed: Long? = null): SoundInstance {
        val identifier = Identifier.parse(name.replace(Regex("[^a-z0-9/:._-]"), ""))
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

    const val NAVIGATION_LEFT_SOUND_ID = "skysoft:item_list.page_left"
    const val NAVIGATION_RIGHT_SOUND_ID = "skysoft:item_list.page_right"
    const val CHAT_NOTIFY_DEFAULT_SOUND_ID = "minecraft:block.note_block.pling"
}
