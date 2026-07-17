package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.features.helditem.HeldItemEditorScreen
import com.skysoft.utils.ChangeResult
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import java.util.Locale

class HeldItemConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Apply held item position, scale, swing, and texture settings.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Held item settings.")
    @field:Accordion
    val settings = HeldItemSettingsConfig()

    @JvmField
    @field:Expose
    val global = HeldItemTransformConfig()

    @JvmField
    @field:Expose
    var itemTransforms: MutableMap<String, HeldItemTransformConfig> = linkedMapOf()

    @JvmField
    @field:Expose
    var globalTextureMode = HeldItemTextureMode.PACK

    @JvmField
    @field:Expose
    var itemTextureModes: MutableMap<String, HeldItemTextureMode> = linkedMapOf()

    @JvmField
    @field:Expose
    var editorX = AUTO_EDITOR_POSITION

    @JvmField
    @field:Expose
    var editorY = AUTO_EDITOR_POSITION

    fun transformFor(itemId: String?): HeldItemTransformConfig =
        itemId?.let { itemTransforms[normalizedItemId(it)] } ?: global

    fun customize(itemId: String): HeldItemTransformConfig =
        itemTransforms.getOrPut(normalizedItemId(itemId)) { global.copyValues() }

    fun removeCustomization(itemId: String): ChangeResult =
        ChangeResult.from(itemTransforms.remove(normalizedItemId(itemId)) != null)

    fun hasCustomization(itemId: String?): Boolean =
        itemId != null && itemTransforms.containsKey(normalizedItemId(itemId))

    fun usesVanillaTexture(itemId: String?): Boolean =
        textureModeFor(itemId) == HeldItemTextureMode.VANILLA

    fun toggleGlobalTexture(): ChangeResult {
        globalTextureMode = globalTextureMode.toggled()
        return ChangeResult.CHANGED
    }

    fun toggleItemTexture(itemId: String): ChangeResult {
        val normalizedId = normalizedItemId(itemId)
        if (normalizedId.isEmpty()) return ChangeResult.UNCHANGED
        itemTextureModes[normalizedId] = textureModeFor(normalizedId).toggled()
        return ChangeResult.CHANGED
    }

    fun hasTextureOverride(itemId: String?): Boolean =
        itemId != null && itemTextureModes.containsKey(normalizedItemId(itemId))

    fun removeTextureOverride(itemId: String): ChangeResult =
        ChangeResult.from(itemTextureModes.remove(normalizedItemId(itemId)) != null)

    fun hasGlobalCustomization(): Boolean = !global.isDefault() || globalTextureMode != HeldItemTextureMode.PACK

    fun resetGlobalCustomization(): ChangeResult {
        val result = ChangeResult.from(hasGlobalCustomization())
        global.reset()
        globalTextureMode = HeldItemTextureMode.PACK
        return result
    }

    fun hasItemCustomization(itemId: String?): Boolean = hasCustomization(itemId) || hasTextureOverride(itemId)

    fun removeItemCustomization(itemId: String): ChangeResult {
        val transformResult = removeCustomization(itemId)
        val textureResult = removeTextureOverride(itemId)
        return if (transformResult == ChangeResult.CHANGED || textureResult == ChangeResult.CHANGED) {
            ChangeResult.CHANGED
        } else {
            ChangeResult.UNCHANGED
        }
    }

    internal fun snapshotCustomization(itemId: String?): HeldItemCustomizationSnapshot {
        if (itemId == null) {
            return HeldItemCustomizationSnapshot(global.snapshot(), globalTextureMode)
        }
        val normalizedId = normalizedItemId(itemId)
        return HeldItemCustomizationSnapshot(
            itemTransforms[normalizedId]?.snapshot(),
            itemTextureModes[normalizedId],
        )
    }

    internal fun restoreCustomization(itemId: String?, snapshot: HeldItemCustomizationSnapshot) {
        if (itemId == null) {
            val transform = requireNotNull(snapshot.transform) { "Global held item history is missing its transform" }
            global.restore(transform)
            globalTextureMode = requireNotNull(snapshot.textureMode) {
                "Global held item history is missing its texture mode"
            }
            return
        }
        val normalizedId = normalizedItemId(itemId)
        snapshot.transform?.let { itemTransforms[normalizedId] = it.toConfig() } ?: itemTransforms.remove(normalizedId)
        snapshot.textureMode?.let { itemTextureModes[normalizedId] = it } ?: itemTextureModes.remove(normalizedId)
    }

    fun repairLoadedValues() {
        global.repairLoadedValues()
        val repairedTransforms = linkedMapOf<String, HeldItemTransformConfig>()
        itemTransforms.forEach { (itemId, transform) ->
            val normalizedId = normalizedItemId(itemId)
            if (normalizedId.isNotEmpty()) {
                transform.repairLoadedValues()
                repairedTransforms[normalizedId] = transform
            }
        }
        itemTransforms = repairedTransforms
        val repairedTextureModes = linkedMapOf<String, HeldItemTextureMode>()
        itemTextureModes.forEach { (itemId, textureMode) ->
            val normalizedId = normalizedItemId(itemId)
            if (normalizedId.isNotEmpty()) repairedTextureModes[normalizedId] = textureMode
        }
        itemTextureModes = repairedTextureModes
        if (editorX < AUTO_EDITOR_POSITION) editorX = AUTO_EDITOR_POSITION
        if (editorY < AUTO_EDITOR_POSITION) editorY = AUTO_EDITOR_POSITION
    }

    private fun textureModeFor(itemId: String?): HeldItemTextureMode =
        itemId?.let { itemTextureModes[normalizedItemId(it)] } ?: globalTextureMode

    companion object {
        const val AUTO_EDITOR_POSITION = -1

        private fun normalizedItemId(itemId: String): String = itemId.trim().uppercase(Locale.US)
    }
}

class HeldItemSettingsConfig {
    @JvmField
    @field:ConfigOption(name = "Editor", desc = "Open the held item editor.")
    @field:ConfigEditorButton(buttonText = "Open")
    val openEditor = Runnable { HeldItemEditorScreen.open() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ignore Mining Effects", desc = "Keep swing duration unchanged by Haste and Mining Fatigue.")
    @field:ConfigEditorBoolean
    var ignoresMiningEffects = false
}

internal data class HeldItemCustomizationSnapshot(
    val transform: HeldItemTransformSnapshot?,
    val textureMode: HeldItemTextureMode?,
)

internal data class HeldItemTransformSnapshot(
    val x: Float,
    val y: Float,
    val z: Float,
    val scale: Float,
    val swingSpeed: Float,
    val swingStyle: HeldItemSwingStyle,
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float,
)

private fun HeldItemTransformConfig.snapshot(): HeldItemTransformSnapshot = HeldItemTransformSnapshot(
    x = x,
    y = y,
    z = z,
    scale = scale,
    swingSpeed = swingSpeed,
    swingStyle = swingStyle,
    rotationX = rotationX,
    rotationY = rotationY,
    rotationZ = rotationZ,
)

private fun HeldItemTransformSnapshot.toConfig(): HeldItemTransformConfig = HeldItemTransformConfig(
    x = x,
    y = y,
    z = z,
    scale = scale,
    swingSpeed = swingSpeed,
    swingStyle = swingStyle,
    rotationX = rotationX,
    rotationY = rotationY,
    rotationZ = rotationZ,
)

private fun HeldItemTransformConfig.restore(snapshot: HeldItemTransformSnapshot) {
    x = snapshot.x
    y = snapshot.y
    z = snapshot.z
    scale = snapshot.scale
    swingSpeed = snapshot.swingSpeed
    swingStyle = snapshot.swingStyle
    rotationX = snapshot.rotationX
    rotationY = snapshot.rotationY
    rotationZ = snapshot.rotationZ
}

enum class HeldItemTextureMode {
    PACK,
    VANILLA,
    ;

    fun toggled(): HeldItemTextureMode = if (this == PACK) VANILLA else PACK
}

enum class HeldItemSwingStyle {
    VANILLA,
    ITEM_ONLY,
}

class HeldItemTransformConfig(
    @JvmField @field:Expose var x: Float = 0f,
    @JvmField @field:Expose var y: Float = 0f,
    @JvmField @field:Expose var z: Float = 0f,
    @JvmField @field:Expose var scale: Float = 1f,
    @JvmField
    @field:Expose
    @field:SerializedName(value = "swingSpeed", alternate = ["swingDuration"])
    var swingSpeed: Float = 1f,
    @JvmField @field:Expose var swingStyle: HeldItemSwingStyle = HeldItemSwingStyle.VANILLA,
    @JvmField @field:Expose var rotationX: Float = 0f,
    @JvmField @field:Expose var rotationY: Float = 0f,
    @JvmField @field:Expose var rotationZ: Float = 0f,
) {
    fun copyValues(): HeldItemTransformConfig = HeldItemTransformConfig(
        x = x,
        y = y,
        z = z,
        scale = scale,
        swingSpeed = swingSpeed,
        swingStyle = swingStyle,
        rotationX = rotationX,
        rotationY = rotationY,
        rotationZ = rotationZ,
    )

    fun reset() {
        x = 0f
        y = 0f
        z = 0f
        scale = 1f
        swingSpeed = 1f
        swingStyle = HeldItemSwingStyle.VANILLA
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
    }

    fun repairLoadedValues() {
        x = x.coerceIn(HeldItemTransformLimits.MIN_X, HeldItemTransformLimits.MAX_X)
        y = y.coerceIn(HeldItemTransformLimits.MIN_Y, HeldItemTransformLimits.MAX_Y)
        z = z.coerceIn(HeldItemTransformLimits.MIN_Z, HeldItemTransformLimits.MAX_Z)
        scale = scale.coerceIn(HeldItemTransformLimits.MIN_SCALE, HeldItemTransformLimits.MAX_SCALE)
        swingSpeed = swingSpeed.coerceIn(
            HeldItemTransformLimits.MIN_SWING_SPEED,
            HeldItemTransformLimits.MAX_SWING_SPEED,
        )
        rotationX = rotationX.coerceIn(HeldItemTransformLimits.MIN_ROTATION, HeldItemTransformLimits.MAX_ROTATION)
        rotationY = rotationY.coerceIn(HeldItemTransformLimits.MIN_ROTATION, HeldItemTransformLimits.MAX_ROTATION)
        rotationZ = rotationZ.coerceIn(HeldItemTransformLimits.MIN_ROTATION, HeldItemTransformLimits.MAX_ROTATION)
    }

    fun hasRenderChanges(): Boolean =
        x != 0f || y != 0f || z != 0f || scale != 1f || rotationX != 0f || rotationY != 0f || rotationZ != 0f

    fun isDefault(): Boolean =
        !hasRenderChanges() && swingSpeed == 1f && swingStyle == HeldItemSwingStyle.VANILLA
}

object HeldItemTransformLimits {
    const val MIN_X = -2f
    const val MAX_X = 2f
    const val MIN_Y = -2f
    const val MAX_Y = 2f
    const val MIN_Z = -1.5f
    const val MAX_Z = 0.6f
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 3f
    const val MIN_SWING_SPEED = 0.25f
    const val MAX_SWING_SPEED = 3f
    const val MIN_ROTATION = -180f
    const val MAX_ROTATION = 180f
}
