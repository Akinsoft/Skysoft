package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.ColorUtilities
import net.minecraft.client.Camera
import net.minecraft.util.ARGB
import net.minecraft.world.level.material.FogType
import org.joml.Vector4f

object SkyColor {
    private val config get() = SkysoftConfigGui.config().misc.skyColor

    @JvmStatic
    fun skyColor(original: Int): Int {
        val config = config
        return if (config.enabled) {
            config.details.color.get().getEffectiveColourRGB() and ColorUtilities.RGB_MASK
        } else {
            original
        }
    }

    @JvmStatic
    fun voidColor(original: Vector4f): Vector4f {
        val config = config
        if (!config.enabled) return original
        val rgb = config.details.voidColor.get().getEffectiveColourRGB()
        return ARGB.vector4fFromARGB32(rgb or OPAQUE_ALPHA)
    }

    @JvmStatic
    fun applyHorizonColor(camera: Camera, destination: Vector4f) {
        val config = config
        if (!config.enabled || camera.fluidInCamera != FogType.NONE) return
        val rgb = config.details.horizonColor.get().getEffectiveColourRGB()
        destination.set(ARGB.vector4fFromARGB32(rgb or OPAQUE_ALPHA))
    }

    private const val OPAQUE_ALPHA = 0xFF000000.toInt()
}
