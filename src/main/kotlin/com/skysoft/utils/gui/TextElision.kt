package com.skysoft.utils.gui

import net.minecraft.client.gui.Font
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence

fun Font.elide(text: String, maximumWidth: Int): String {
    if (width(text) <= maximumWidth) return text
    return plainSubstrByWidth(text, (maximumWidth - width(ELLIPSIS)).coerceAtLeast(0)) + ELLIPSIS
}

fun Font.elide(text: Component, maximumWidth: Int): FormattedCharSequence {
    if (width(text) <= maximumWidth) return text.visualOrderText
    val suffix = FormattedText.of(ELLIPSIS, text.style)
    val head = substrByWidth(text, (maximumWidth - width(suffix)).coerceAtLeast(0))
    return Language.getInstance().getVisualOrder(FormattedText.composite(head, suffix))
}

private const val ELLIPSIS = "…"
