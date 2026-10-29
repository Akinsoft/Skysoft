package com.skysoft.utils

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.formattedText
import net.minecraft.network.chat.Component

class SkysoftMessage(
    val component: Component,
    val source: SkysoftMessageSource,
    val overlay: Boolean = false,
) {
    val plainText: String = component.string
    val cleanText: String by lazy { component.cleanSkyBlockText() }
    val formattedText: String by lazy { component.formattedText() }
}

enum class SkysoftMessageSource {
    CHAT,
    GAME,
}
