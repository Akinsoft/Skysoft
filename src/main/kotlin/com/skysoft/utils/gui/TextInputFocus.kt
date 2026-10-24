package com.skysoft.utils.gui

import com.skysoft.utils.MinecraftInput

internal class TextInputFocus {
    var focused = false
        set(value) {
            if (value != field) MinecraftInput.onTextInputFocusChange(this, value)
            field = value
        }

    fun keepActive() {
        if (focused) MinecraftInput.onTextInputFocusChange(this, true)
    }
}
