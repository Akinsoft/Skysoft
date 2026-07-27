package com.skysoft.config.core

import com.google.gson.annotations.Expose
import kotlin.math.abs
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

    @Expose
    private var horizontalAnchor: HudAnchor? = null

    @Expose
    private var verticalAnchor: HudAnchor? = null

    @Transient
    private var defaultCopy: HudPosition? = null

    @Transient
    private var referenceWidth = UNKNOWN_DIMENSION

    @Transient
    private var referenceHeight = UNKNOWN_DIMENSION

    @Transient
    private var referenceObjectWidth = 0

    @Transient
    private var referenceObjectHeight = 0

    val effectiveScale: Float get() = scale

    fun rememberDefault(default: HudPosition? = null): HudPosition {
        if (default != null) defaultCopy = copyPosition(default)
        else if (defaultCopy == null) defaultCopy = copyPosition(this)
        return this
    }

    fun resetToDefault() {
        defaultCopy?.snapshot()?.let(::restore)
    }

    fun isAtDefault(): Boolean = defaultCopy?.let {
        x == it.x &&
            y == it.y &&
            scale == it.scale &&
            effectiveAnchor(x, centerX, horizontalAnchor) == effectiveAnchor(it.x, it.centerX, it.horizontalAnchor) &&
            effectiveAnchor(y, centerY, verticalAnchor) == effectiveAnchor(it.y, it.centerY, it.verticalAnchor)
    } ?: true

    fun anchorToTop(objHeight: Int) = anchorToTop(Minecraft.getInstance().window.guiScaledHeight, objHeight)

    internal fun anchorToTop(screenHeight: Int, objHeight: Int) {
        if (effectiveAnchor(y, centerY, verticalAnchor) != HudAnchor.CENTER) return
        val absoluteY = calcAbs0(y, screenHeight, objHeight, HudAnchor.CENTER, clampEnd = false)
        setAxis(horizontal = false, absoluteY, screenHeight, objHeight, HudAnchor.START)
    }

    fun moveToAbsoluteAllowingOverflow(absX: Int, absY: Int, objWidth: Int, objHeight: Int): HudPosition =
        moveToAbsolute(absX, absY, objWidth, objHeight, clampEnd = false)

    fun moveToAbsolute(absX: Int, absY: Int, objWidth: Int, objHeight: Int): HudPosition =
        moveToAbsolute(absX, absY, objWidth, objHeight, clampEnd = true)

    fun moveBy(deltaX: Int, deltaY: Int) {
        x += deltaX
        y += deltaY
    }

    private fun moveToAbsolute(
        absX: Int,
        absY: Int,
        objWidth: Int,
        objHeight: Int,
        clampEnd: Boolean,
    ): HudPosition {
        val window = Minecraft.getInstance().window
        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight
        val clampedX = clampAbsolute(absX, screenWidth, objWidth, clampEnd)
        val clampedY = clampAbsolute(absY, screenHeight, objHeight, clampEnd)
        setAxis(horizontal = true, clampedX, screenWidth, objWidth, nearestAnchor(clampedX, screenWidth, objWidth))
        setAxis(horizontal = false, clampedY, screenHeight, objHeight, nearestAnchor(clampedY, screenHeight, objHeight))
        return this
    }

    fun getAbsX0(objWidth: Int): Int = getAbsX0(Minecraft.getInstance().window.guiScaledWidth, objWidth)
    fun getAbsY0(objHeight: Int): Int = getAbsY0(Minecraft.getInstance().window.guiScaledHeight, objHeight)

    fun getAbsX0(screenWidth: Int, objWidth: Int): Int {
        prepareAxis(horizontal = true, screenWidth, objWidth)
        return calcAbs0(x, screenWidth, objWidth, effectiveAnchor(x, centerX, horizontalAnchor), clampEnd = true)
    }

    fun getAbsY0(screenHeight: Int, objHeight: Int): Int {
        prepareAxis(horizontal = false, screenHeight, objHeight)
        return calcAbs0(y, screenHeight, objHeight, effectiveAnchor(y, centerY, verticalAnchor), clampEnd = true)
    }

    fun getAbsX0AllowingOverflow(objWidth: Int): Int {
        val screenWidth = Minecraft.getInstance().window.guiScaledWidth
        prepareAxis(horizontal = true, screenWidth, objWidth)
        return calcAbs0(x, screenWidth, objWidth, effectiveAnchor(x, centerX, horizontalAnchor), clampEnd = false)
    }

    fun getAbsY0AllowingOverflow(objHeight: Int): Int {
        val screenHeight = Minecraft.getInstance().window.guiScaledHeight
        prepareAxis(horizontal = false, screenHeight, objHeight)
        return calcAbs0(y, screenHeight, objHeight, effectiveAnchor(y, centerY, verticalAnchor), clampEnd = false)
    }

    internal fun snapshot(): Snapshot = Snapshot(
        x,
        y,
        scale,
        centerX,
        centerY,
        horizontalAnchor,
        verticalAnchor,
    )

    internal fun restore(snapshot: Snapshot) {
        x = snapshot.x
        y = snapshot.y
        scale = snapshot.scale
        centerX = snapshot.centerX
        centerY = snapshot.centerY
        horizontalAnchor = snapshot.horizontalAnchor
        verticalAnchor = snapshot.verticalAnchor
        referenceWidth = UNKNOWN_DIMENSION
        referenceHeight = UNKNOWN_DIMENSION
    }

    private fun prepareAxis(horizontal: Boolean, length: Int, objectLength: Int) {
        val referenceLength = if (horizontal) referenceWidth else referenceHeight
        val referenceObjectLength = if (horizontal) referenceObjectWidth else referenceObjectHeight
        val explicitAnchor = if (horizontal) horizontalAnchor else verticalAnchor
        if (referenceLength != UNKNOWN_DIMENSION && referenceLength != length && explicitAnchor == null) {
            val value = if (horizontal) x else y
            val centered = if (horizontal) centerX else centerY
            val oldAbsolute = calcAbs0(
                value,
                referenceLength,
                referenceObjectLength,
                effectiveAnchor(value, centered, explicitAnchor),
                clampEnd = false,
            )
            setAxis(
                horizontal,
                oldAbsolute,
                referenceLength,
                referenceObjectLength,
                nearestAnchor(oldAbsolute, referenceLength, referenceObjectLength),
            )
        }
        if (horizontal) {
            referenceWidth = length
            referenceObjectWidth = objectLength
        } else {
            referenceHeight = length
            referenceObjectHeight = objectLength
        }
    }

    private fun setAxis(
        horizontal: Boolean,
        absolute: Int,
        length: Int,
        objectLength: Int,
        anchor: HudAnchor,
    ) {
        val encoded = encode(absolute, length, objectLength, anchor)
        if (horizontal) {
            x = encoded
            centerX = anchor == HudAnchor.CENTER
            horizontalAnchor = anchor
            referenceWidth = length
            referenceObjectWidth = objectLength
        } else {
            y = encoded
            centerY = anchor == HudAnchor.CENTER
            verticalAnchor = anchor
            referenceHeight = length
            referenceObjectHeight = objectLength
        }
    }

    internal data class Snapshot(
        val x: Int,
        val y: Int,
        val scale: Float,
        val centerX: Boolean,
        val centerY: Boolean,
        val horizontalAnchor: HudAnchor?,
        val verticalAnchor: HudAnchor?,
    )

    companion object {
        const val DEFAULT_SCALE = 1f
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 10f
        private const val UNKNOWN_DIMENSION = -1
    }
}

internal enum class HudAnchor {
    START,
    CENTER,
    END,
}

private fun copyPosition(position: HudPosition): HudPosition =
    HudPosition().also { it.restore(position.snapshot()) }

private fun effectiveAnchor(value: Int, centered: Boolean, explicit: HudAnchor?): HudAnchor = explicit ?: when {
    centered -> HudAnchor.CENTER
    value < 0 -> HudAnchor.END
    else -> HudAnchor.START
}

private fun nearestAnchor(absolute: Int, length: Int, objectLength: Int): HudAnchor {
    val availableLength = (length - objectLength).coerceAtLeast(0)
    return HudAnchor.entries.minBy { anchor -> abs(absolute - anchor.coordinate(availableLength)) }
}

private fun HudAnchor.coordinate(availableLength: Int): Int = when (this) {
    HudAnchor.START -> 0
    HudAnchor.CENTER -> availableLength / 2
    HudAnchor.END -> availableLength
}

private fun encode(absolute: Int, length: Int, objectLength: Int, anchor: HudAnchor): Int = when (anchor) {
    HudAnchor.START -> absolute
    HudAnchor.CENTER -> absolute - (length - objectLength) / 2
    HudAnchor.END -> absolute - (length - objectLength)
}

private fun clampAbsolute(value: Int, length: Int, objectLength: Int, clampEnd: Boolean): Int =
    if (clampEnd) value.coerceIn(0, (length - objectLength).coerceAtLeast(0)) else value.coerceAtLeast(0)

private fun calcAbs0(
    axis: Int,
    length: Int,
    objectLength: Int,
    anchor: HudAnchor,
    clampEnd: Boolean,
): Int {
    val result = when (anchor) {
        HudAnchor.START -> axis
        HudAnchor.CENTER -> axis + (length - objectLength) / 2
        HudAnchor.END -> length + axis - objectLength
    }
    return if (clampEnd) {
        result.coerceIn(0, (length - objectLength).coerceAtLeast(0))
    } else {
        result.coerceAtLeast(0)
    }
}
