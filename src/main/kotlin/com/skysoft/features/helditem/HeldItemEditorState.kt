package com.skysoft.features.helditem

import com.skysoft.config.HeldItemConfig
import com.skysoft.config.HeldItemSwingStyle
import com.skysoft.config.HeldItemTransformConfig
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.gui.Rect
import net.minecraft.world.item.ItemStack

internal class HeldItemEditorState(
    private val config: HeldItemConfig,
    initialTarget: EditTarget = EditTarget.GLOBAL,
    private val targetSelection: (EditTarget) -> Unit = {},
    private val currentItemIdProvider: () -> String? = {
        HeldItemTransforms.itemId(HeldItemTransforms.currentItem())
    },
) {
    private var preferredTarget = initialTarget
    var target = initialTarget
        private set

    fun ensureTargetAvailable() {
        target = if (preferredTarget == EditTarget.ITEM && currentItemId() == null) {
            EditTarget.GLOBAL
        } else {
            preferredTarget
        }
    }

    fun selectTarget(selectedTarget: EditTarget) {
        if (selectedTarget == EditTarget.ITEM && currentItemId() == null) return
        preferredTarget = selectedTarget
        target = selectedTarget
        targetSelection(selectedTarget)
    }

    fun currentItem(): ItemStack = HeldItemTransforms.currentItem()

    fun currentItemId(): String? = currentItemIdProvider()

    fun displayTransform(): HeldItemTransformConfig =
        if (target == EditTarget.ITEM) config.transformFor(currentItemId()) else config.global

    fun canResetCurrentTarget(): Boolean = when (target) {
        EditTarget.GLOBAL -> config.hasGlobalCustomization()
        EditTarget.ITEM -> config.hasItemCustomization(currentItemId())
    }

    fun isTextureToggleVisible(): Boolean = HeldItemTextureOverrides.hasPackTexture(currentItem())

    fun canToggleTexture(): Boolean =
        HeldItemTextureOverrides.canUseVanillaTexture(currentItem()) &&
            (target == EditTarget.GLOBAL || currentItemId() != null)

    fun usesVanillaTexture(): Boolean = when (target) {
        EditTarget.GLOBAL -> config.usesVanillaTexture(null)
        EditTarget.ITEM -> config.usesVanillaTexture(currentItemId())
    }

    fun previewItem(): ItemStack = HeldItemTextureOverrides.previewStack(currentItem())

    fun toggleTexture(): ChangeResult {
        if (!canToggleTexture()) return ChangeResult.UNCHANGED
        return when (target) {
            EditTarget.GLOBAL -> config.toggleGlobalTexture()
            EditTarget.ITEM -> currentItemId()?.let(config::toggleItemTexture) ?: ChangeResult.UNCHANGED
        }
    }

    fun moveItem(deltaX: Int, deltaY: Int, unitsPerPixel: Float) {
        val transform = editableTransform() ?: return
        setFieldValue(transform, TransformField.X, transform.x + deltaX * unitsPerPixel)
        setFieldValue(transform, TransformField.Y, transform.y - deltaY * unitsPerPixel)
    }

    fun moveItemDepth(deltaX: Int) {
        val transform = editableTransform() ?: return
        setFieldValue(transform, TransformField.Z, transform.z + deltaX * DEPTH_PER_PIXEL)
    }

    fun updateSlider(field: TransformField, mouseX: Int, track: Rect) {
        val progress = ((mouseX - track.x) / track.width.toFloat()).coerceIn(0f, 1f)
        setField(field, field.min + (field.max - field.min) * progress)
    }

    fun changeFieldBy(field: TransformField, amount: Float) {
        setField(field, field.value(displayTransform()) + amount)
    }

    fun selectSwingStyle(style: HeldItemSwingStyle) {
        editableTransform()?.swingStyle = style
    }

    fun resetCurrentTarget(): ChangeResult {
        return if (target == EditTarget.GLOBAL) {
            config.resetGlobalCustomization()
        } else {
            currentItemId()?.let(config::removeItemCustomization) ?: ChangeResult.UNCHANGED
        }
    }

    val historyKey: HeldItemHistoryKey?
        get() = when (target) {
            EditTarget.GLOBAL -> HeldItemHistoryKey.GLOBAL
            EditTarget.ITEM -> currentItemId()?.let(HeldItemHistoryKey::item)
        }

    private fun setField(field: TransformField, value: Float) {
        editableTransform()?.let { setFieldValue(it, field, value) }
    }

    private fun setFieldValue(transform: HeldItemTransformConfig, field: TransformField, value: Float) {
        field.setValue(transform, value.coerceIn(field.min, field.max))
    }

    private fun editableTransform(): HeldItemTransformConfig? = when (target) {
        EditTarget.GLOBAL -> config.global
        EditTarget.ITEM -> currentItemId()?.let(config::customize)
    }
}

internal enum class EditTarget {
    GLOBAL,
    ITEM,
}

private const val DEPTH_PER_PIXEL = 0.004f
