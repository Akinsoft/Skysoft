package com.skysoft.config.core

import com.google.gson.annotations.Expose
import net.minecraft.client.Minecraft

class HudPosition @JvmOverloads constructor(
    x: Int = 0,
    y: Int = 0,
    scale: Float = DEFAULT_SCALE,
    centerX: Boolean = false,
    centerY: Boolean = true,
) {
    @Expose
    var x: Int = x
        private set

    @Expose
    var y: Int = y
        private set

    @Expose
    var scale: Float = scale
        get() = if (field <= 0f) DEFAULT_SCALE else field.coerceIn(MIN_SCALE, MAX_SCALE)

    @Expose
    var centerX: Boolean = centerX
        private set

    @Expose
    var centerY: Boolean = centerY
        private set

    @Transient
    private var defaultCopy: HudPosition? = null

    val effectiveScale: Float get() = scale

    fun rememberDefault(): HudPosition {
        if (defaultCopy == null) defaultCopy = HudPosition(x, y, scale, centerX, centerY)
        return this
    }

    fun rememberDefault(default: HudPosition): HudPosition {
        defaultCopy = HudPosition(default.x, default.y, default.scale, default.centerX, default.centerY)
        return this
    }

    fun resetToDefault() {
        defaultCopy?.let {
            x = it.x
            y = it.y
            scale = it.scale
            centerX = it.centerX
            centerY = it.centerY
        }
    }

    fun isAtDefault(): Boolean = defaultCopy?.let {
        x == it.x && y == it.y && scale == it.scale && centerX == it.centerX && centerY == it.centerY
    } ?: true

    fun moveToAbsoluteAllowingOverflow(absX: Int, absY: Int, objWidth: Int, objHeight: Int): HudPosition {
        return moveToAbsolute(absX, absY, objWidth, objHeight, clampEnd = false)
    }

    fun moveToAbsolute(absX: Int, absY: Int, objWidth: Int, objHeight: Int): HudPosition {
        return moveToAbsolute(absX, absY, objWidth, objHeight, clampEnd = true)
    }

    private fun moveToAbsolute(
        absX: Int,
        absY: Int,
        objWidth: Int,
        objHeight: Int,
        clampEnd: Boolean,
    ): HudPosition {
        val screenWidth = screenWidth()
        val screenHeight = screenHeight()
        val clampedX = if (clampEnd) absX.coerceIn(0, (screenWidth - objWidth).coerceAtLeast(0)) else absX.coerceAtLeast(0)
        val clampedY = if (clampEnd) absY.coerceIn(0, (screenHeight - objHeight).coerceAtLeast(0)) else absY.coerceAtLeast(0)
        x = if (centerX) clampedX - (screenWidth - objWidth) / 2 else clampedX
        y = if (centerY) clampedY - (screenHeight - objHeight) / 2 else clampedY
        return this
    }

    fun getAbsX0(objWidth: Int): Int = calcAbs0(x, screenWidth(), objWidth, centerX, clampEnd = true)
    fun getAbsY0(objHeight: Int): Int = calcAbs0(y, screenHeight(), objHeight, centerY, clampEnd = true)
    fun getAbsX0(screenWidth: Int, objWidth: Int): Int = calcAbs0(x, screenWidth, objWidth, centerX, clampEnd = true)
    fun getAbsY0(screenHeight: Int, objHeight: Int): Int = calcAbs0(y, screenHeight, objHeight, centerY, clampEnd = true)
    fun getAbsX0AllowingOverflow(objWidth: Int): Int = calcAbs0(x, screenWidth(), objWidth, centerX, clampEnd = false)
    fun getAbsY0AllowingOverflow(objHeight: Int): Int = calcAbs0(y, screenHeight(), objHeight, centerY, clampEnd = false)

    private fun calcAbs0(axis: Int, length: Int, objLength: Int, centered: Boolean, clampEnd: Boolean): Int {
        val result = if (centered) {
            axis + (length - objLength) / 2
        } else if (axis < 0) {
            length + axis - objLength
        } else {
            axis
        }
        return if (clampEnd) result.coerceIn(0, (length - objLength).coerceAtLeast(0)) else result.coerceAtLeast(0)
    }

    private fun screenWidth(): Int = Minecraft.getInstance().window.guiScaledWidth
    private fun screenHeight(): Int = Minecraft.getInstance().window.guiScaledHeight

    companion object {
        const val DEFAULT_SCALE = 1f
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 10f
    }
}
