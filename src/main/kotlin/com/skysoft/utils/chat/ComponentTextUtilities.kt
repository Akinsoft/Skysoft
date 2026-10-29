package com.skysoft.utils.chat

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

fun Component.hoverTextComponents(): List<Component> = buildList {
    fun visit(component: Component) {
        val hover = component.style.hoverEvent
        if (hover is HoverEvent.ShowText) add(hover.value())
        component.siblings.forEach(::visit)
    }
    visit(this@hoverTextComponents)
}

fun Component.styleAt(index: Int): Style {
    var start = 0
    for (part in toFlatList()) {
        val end = start + part.string.length
        if (index in start until end) return part.style
        start = end
    }
    return Style.EMPTY
}

fun Component.styledSubstring(start: Int, end: Int): MutableComponent {
    val result = Component.empty()
    var offset = 0
    for (part in toFlatList()) {
        val text = part.string
        val from = (start - offset).coerceAtLeast(0)
        val to = (end - offset).coerceAtMost(text.length)
        if (from < to) result.append(Component.literal(text.substring(from, to)).withStyle(part.style))
        offset += text.length
    }
    return result
}
