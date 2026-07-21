package com.skysoft.utils.mixin

import com.skysoft.utils.SkysoftErrorBoundary
import java.util.function.Consumer
import java.util.function.Supplier

internal object MixinErrorBoundary {
    @JvmStatic
    fun run(boundary: String, action: Runnable) = SkysoftErrorBoundary.run(boundary, action::run)

    @JvmStatic
    fun <T> value(boundary: String, fallback: T, action: Supplier<T>): T =
        SkysoftErrorBoundary.value(boundary, fallback, action::get)

    @JvmStatic
    fun aroundUnit(boundary: String, original: Runnable, action: Consumer<Runnable>) =
        SkysoftErrorBoundary.aroundUnit(boundary, original::run) { action.accept(Runnable(it)) }

    @JvmStatic
    fun onClientThread(boundary: String, action: Runnable) = SkysoftErrorBoundary.onClientThread(boundary, action::run)

    @JvmStatic
    fun acknowledgeClipboardCopy(value: String) = SkysoftErrorBoundary.acknowledgeClipboardCopy(value)
}
