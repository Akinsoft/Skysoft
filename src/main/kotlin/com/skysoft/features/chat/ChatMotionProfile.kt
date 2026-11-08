package com.skysoft.features.chat

import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.exp
import net.minecraft.client.gui.GuiGraphicsExtractor

object ChatMotionProfile {
    @JvmStatic
    fun messageDisplacement(lineHeight: Int, progress: Float): Float =
        (lineHeight * MESSAGE_TRAVEL_RATIO * settleDistance(progress, MESSAGE_RESPONSE)).toFloat()

    @JvmStatic
    fun inputDisplacement(viewportHeight: Int, progress: Float): Float {
        val travel = (viewportHeight * INPUT_TRAVEL_RATIO).coerceIn(MIN_INPUT_TRAVEL, MAX_INPUT_TRAVEL)
        return (travel * settleDistance(progress, INPUT_RESPONSE)).toFloat()
    }

    @JvmStatic
    fun renderShifted(graphics: GuiGraphicsExtractor, displacement: Float, render: Runnable) {
        if (displacement == 0.0f) {
            render.run()
            return
        }
        graphics.withIsolatedPose {
            pose().translate(0.0f, displacement)
            render.run()
        }
    }

    private fun settleDistance(progress: Float, response: Double): Double {
        val elapsed = progress.coerceIn(0.0f, 1.0f).toDouble()
        if (elapsed == 0.0) return 1.0
        if (elapsed == 1.0) return 0.0

        val end = (1.0 + response) * exp(-response)
        val current = (1.0 + response * elapsed) * exp(-response * elapsed)
        return ((current - end) / (1.0 - end)).coerceIn(0.0, 1.0)
    }

    private const val MESSAGE_TRAVEL_RATIO = 0.72
    private const val MESSAGE_RESPONSE = 7.25
    private const val INPUT_TRAVEL_RATIO = 0.018
    private const val MIN_INPUT_TRAVEL = 5.0
    private const val MAX_INPUT_TRAVEL = 9.0
    private const val INPUT_RESPONSE = 6.0
}
