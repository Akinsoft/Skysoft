package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.mixin.KeyMappingAccessor
import com.skysoft.mixin.ToggleKeyMappingAccessor
import com.skysoft.utils.SkysoftScreenEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.ToggleKeyMapping

object ToggleStateIssuesFix {
    fun register() {
        SkysoftScreenEvents.onBeforeInit(
            "Toggle state issues fix",
            isActive = { SkysoftConfigGui.config().fixes.fixToggleStateIssues },
        ) { minecraft, _ ->
            reset(minecraft.options.keyAttack)
            reset(minecraft.options.keyUse)
        }
    }

    private fun reset(keyMapping: KeyMapping) {
        (keyMapping as KeyMappingAccessor).skysoftSetClickCount(0)
        if (keyMapping is ToggleKeyMapping) {
            val accessor = keyMapping as ToggleKeyMappingAccessor
            accessor.skysoftSetReleasedByScreenWhenDown(false)
            accessor.skysoftReset()
            return
        }
        keyMapping.setDown(false)
    }
}
