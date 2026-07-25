package com.skysoft.config.core

import com.google.gson.annotations.Expose

class HudDimensions {
    @Expose
    var widthOffset: Int = 0
        private set

    @Expose
    var heightOffset: Int = 0
        private set

    fun width(defaultWidth: Int, minimumWidth: Int): Int =
        (defaultWidth + widthOffset).coerceAtLeast(minimumWidth)

    fun height(defaultHeight: Int, minimumHeight: Int): Int =
        (defaultHeight + heightOffset).coerceAtLeast(minimumHeight)

    fun resize(width: Int, height: Int, defaultWidth: Int, defaultHeight: Int) {
        widthOffset = width - defaultWidth
        heightOffset = height - defaultHeight
    }

    fun resetToDefault() {
        widthOffset = 0
        heightOffset = 0
    }
}
