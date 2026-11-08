package com.skysoft.utils.input

enum class InputHandlingResult {
    CONSUMED,
    IGNORED,
    ;

    companion object {
        fun from(handled: Boolean): InputHandlingResult =
            if (handled) CONSUMED else IGNORED
    }
}
