package com.skysoft.utils.chat

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

fun Component.hoverTextComponents(): List<Component> = buildList {
    fun visit(component: Component) {
        val hover = component.style.hoverEvent
        if (hover is HoverEvent.ShowText) add(hover.value())
        component.siblings.forEach(::visit)
    }
    visit(this@hoverTextComponents)
}
