package com.skysoft.features.helditem

import com.skysoft.config.HeldItemTransformConfig
import com.skysoft.config.HeldItemTransformLimits
import java.util.Locale

internal enum class TransformField(
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
) {
    X("X", HeldItemTransformLimits.MIN_X, HeldItemTransformLimits.MAX_X, TransformFieldSteps.POSITION_SCROLL_STEP),
    Y("Y", HeldItemTransformLimits.MIN_Y, HeldItemTransformLimits.MAX_Y, TransformFieldSteps.POSITION_SCROLL_STEP),
    Z("Z", HeldItemTransformLimits.MIN_Z, HeldItemTransformLimits.MAX_Z, TransformFieldSteps.DEPTH_SCROLL_STEP),
    SCALE("Scale", HeldItemTransformLimits.MIN_SCALE, HeldItemTransformLimits.MAX_SCALE, TransformFieldSteps.SCALE_SCROLL_STEP),
    SWING(
        "Swing",
        HeldItemTransformLimits.MIN_SWING_SPEED,
        HeldItemTransformLimits.MAX_SWING_SPEED,
        TransformFieldSteps.SWING_SCROLL_STEP,
    ),
    ROTATION_X(
        "Rotate X",
        HeldItemTransformLimits.MIN_ROTATION,
        HeldItemTransformLimits.MAX_ROTATION,
        TransformFieldSteps.ROTATION_SCROLL_STEP,
    ),
    ROTATION_Y(
        "Rotate Y",
        HeldItemTransformLimits.MIN_ROTATION,
        HeldItemTransformLimits.MAX_ROTATION,
        TransformFieldSteps.ROTATION_SCROLL_STEP,
    ),
    ROTATION_Z(
        "Rotate Z",
        HeldItemTransformLimits.MIN_ROTATION,
        HeldItemTransformLimits.MAX_ROTATION,
        TransformFieldSteps.ROTATION_SCROLL_STEP,
    ),
    ;

    fun value(transform: HeldItemTransformConfig): Float = when (this) {
        X -> transform.x
        Y -> transform.y
        Z -> transform.z
        SCALE -> transform.scale
        SWING -> transform.swingSpeed
        ROTATION_X -> transform.rotationX
        ROTATION_Y -> transform.rotationY
        ROTATION_Z -> transform.rotationZ
    }

    fun setValue(transform: HeldItemTransformConfig, value: Float) {
        when (this) {
            X -> transform.x = value
            Y -> transform.y = value
            Z -> transform.z = value
            SCALE -> transform.scale = value
            SWING -> transform.swingSpeed = value
            ROTATION_X -> transform.rotationX = value
            ROTATION_Y -> transform.rotationY = value
            ROTATION_Z -> transform.rotationZ = value
        }
    }

    fun formattedValue(value: Float): String {
        if (this in HeldItemEditorFields.ROTATION) return String.format(Locale.US, "%.0f°", value)
        val text = String.format(Locale.US, "%.2f", value)
        return if (this == SCALE || this == SWING) "${text}x" else text
    }
}

internal object HeldItemEditorFields {
    val BASIC = listOf(
        TransformField.X,
        TransformField.Y,
        TransformField.Z,
        TransformField.SCALE,
        TransformField.SWING,
    )
    val ROTATION = listOf(
        TransformField.ROTATION_X,
        TransformField.ROTATION_Y,
        TransformField.ROTATION_Z,
    )
    val ALL = BASIC + ROTATION
}

private object TransformFieldSteps {
    const val POSITION_SCROLL_STEP = 0.05f
    const val DEPTH_SCROLL_STEP = 0.05f
    const val SCALE_SCROLL_STEP = 0.05f
    const val SWING_SCROLL_STEP = 0.05f
    const val ROTATION_SCROLL_STEP = 5f
}
