package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.SoundUtilities

internal enum class ViewerInputResult {
    HANDLED,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    IGNORED,
    ;

    val isHandled: Boolean get() = this != IGNORED
    val pageDelta: Int?
        get() = when (this) {
            PREVIOUS_PAGE -> -1
            NEXT_PAGE -> 1
            else -> null
        }

    inline fun orElse(action: () -> ViewerInputResult): ViewerInputResult = if (isHandled) this else action()

    fun playSound() {
        if (!isHandled) return
        pageDelta?.let(SoundUtilities::playNavigationSound) ?: SoundUtilities.playClickSound()
    }

    companion object {
        fun page(delta: Int): ViewerInputResult = if (delta < 0) PREVIOUS_PAGE else NEXT_PAGE
    }
}
