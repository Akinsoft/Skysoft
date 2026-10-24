package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

enum class KeybindNumbering {
    GLFW,
    SDL,
    ;

    fun convert(binding: Int, target: KeybindNumbering): Int = when {
        this == target -> binding
        this == GLFW -> GLFW_TO_SDL[binding] ?: SDL_NO_BINDING
        else -> SDL_TO_GLFW[binding] ?: GLFW_NO_BINDING
    }

    companion object {
        const val SDL_MOUSE_BINDING_OFFSET = -100
    }
}

internal object KeybindNumberingMigration {
    fun convert(config: SkysoftConfig, source: KeybindNumbering, target: KeybindNumbering) {
        if (source == target) return
        convertKeybinds(config, source, target, Collections.newSetFromMap(IdentityHashMap()))
    }

    private fun convertKeybinds(
        holder: Any,
        source: KeybindNumbering,
        target: KeybindNumbering,
        visited: MutableSet<Any>,
    ) {
        if (!visited.add(holder)) return
        if (holder is InventoryButtonConfig) holder.requiredKey = source.convert(holder.requiredKey, target)
        persistedFields(holder.javaClass).forEach { field ->
            if (field.isAnnotationPresent(ConfigEditorKeybind::class.java) && field.type == Int::class.javaPrimitiveType) {
                field.setInt(holder, source.convert(field.getInt(holder), target))
            } else {
                when (val value = field.get(holder)) {
                    is Iterable<*> -> value.filterNotNull().filter(::isSkysoftObject)
                        .forEach { convertKeybinds(it, source, target, visited) }
                    null -> Unit
                    else -> if (isSkysoftObject(value)) convertKeybinds(value, source, target, visited)
                }
            }
        }
    }

    private fun persistedFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && it.isAnnotationPresent(Expose::class.java) }
            .onEach { it.isAccessible = true }

    private fun isSkysoftObject(value: Any): Boolean =
        !value.javaClass.isEnum && value.javaClass.name.startsWith(SKYSOFT_PACKAGE_PREFIX)
}

private class KeyCodes(val glfw: Int, val sdl: Int)

private const val SKYSOFT_PACKAGE_PREFIX = "com.skysoft."
private const val GLFW_NO_BINDING = -1
private const val SDL_NO_BINDING = 0

private val KEYBOARD_CODES = listOf(
    "key.keyboard.unknown" to KeyCodes(glfw = -1, sdl = 0),
    "key.keyboard.space" to KeyCodes(glfw = 32, sdl = 44),
    "key.keyboard.apostrophe" to KeyCodes(glfw = 39, sdl = 52),
    "key.keyboard.comma" to KeyCodes(glfw = 44, sdl = 54),
    "key.keyboard.minus" to KeyCodes(glfw = 45, sdl = 45),
    "key.keyboard.period" to KeyCodes(glfw = 46, sdl = 55),
    "key.keyboard.slash" to KeyCodes(glfw = 47, sdl = 56),
    "key.keyboard.0" to KeyCodes(glfw = 48, sdl = 39),
    "key.keyboard.1" to KeyCodes(glfw = 49, sdl = 30),
    "key.keyboard.2" to KeyCodes(glfw = 50, sdl = 31),
    "key.keyboard.3" to KeyCodes(glfw = 51, sdl = 32),
    "key.keyboard.4" to KeyCodes(glfw = 52, sdl = 33),
    "key.keyboard.5" to KeyCodes(glfw = 53, sdl = 34),
    "key.keyboard.6" to KeyCodes(glfw = 54, sdl = 35),
    "key.keyboard.7" to KeyCodes(glfw = 55, sdl = 36),
    "key.keyboard.8" to KeyCodes(glfw = 56, sdl = 37),
    "key.keyboard.9" to KeyCodes(glfw = 57, sdl = 38),
    "key.keyboard.semicolon" to KeyCodes(glfw = 59, sdl = 51),
    "key.keyboard.equal" to KeyCodes(glfw = 61, sdl = 46),
    "key.keyboard.a" to KeyCodes(glfw = 65, sdl = 4),
    "key.keyboard.b" to KeyCodes(glfw = 66, sdl = 5),
    "key.keyboard.c" to KeyCodes(glfw = 67, sdl = 6),
    "key.keyboard.d" to KeyCodes(glfw = 68, sdl = 7),
    "key.keyboard.e" to KeyCodes(glfw = 69, sdl = 8),
    "key.keyboard.f" to KeyCodes(glfw = 70, sdl = 9),
    "key.keyboard.g" to KeyCodes(glfw = 71, sdl = 10),
    "key.keyboard.h" to KeyCodes(glfw = 72, sdl = 11),
    "key.keyboard.i" to KeyCodes(glfw = 73, sdl = 12),
    "key.keyboard.j" to KeyCodes(glfw = 74, sdl = 13),
    "key.keyboard.k" to KeyCodes(glfw = 75, sdl = 14),
    "key.keyboard.l" to KeyCodes(glfw = 76, sdl = 15),
    "key.keyboard.m" to KeyCodes(glfw = 77, sdl = 16),
    "key.keyboard.n" to KeyCodes(glfw = 78, sdl = 17),
    "key.keyboard.o" to KeyCodes(glfw = 79, sdl = 18),
    "key.keyboard.p" to KeyCodes(glfw = 80, sdl = 19),
    "key.keyboard.q" to KeyCodes(glfw = 81, sdl = 20),
    "key.keyboard.r" to KeyCodes(glfw = 82, sdl = 21),
    "key.keyboard.s" to KeyCodes(glfw = 83, sdl = 22),
    "key.keyboard.t" to KeyCodes(glfw = 84, sdl = 23),
    "key.keyboard.u" to KeyCodes(glfw = 85, sdl = 24),
    "key.keyboard.v" to KeyCodes(glfw = 86, sdl = 25),
    "key.keyboard.w" to KeyCodes(glfw = 87, sdl = 26),
    "key.keyboard.x" to KeyCodes(glfw = 88, sdl = 27),
    "key.keyboard.y" to KeyCodes(glfw = 89, sdl = 28),
    "key.keyboard.z" to KeyCodes(glfw = 90, sdl = 29),
    "key.keyboard.left.bracket" to KeyCodes(glfw = 91, sdl = 47),
    "key.keyboard.backslash" to KeyCodes(glfw = 92, sdl = 49),
    "key.keyboard.right.bracket" to KeyCodes(glfw = 93, sdl = 48),
    "key.keyboard.grave.accent" to KeyCodes(glfw = 96, sdl = 53),
    "key.keyboard.world.1" to KeyCodes(glfw = 161, sdl = 100),
    "key.keyboard.world.2" to KeyCodes(glfw = 162, sdl = 50),
    "key.keyboard.escape" to KeyCodes(glfw = 256, sdl = 41),
    "key.keyboard.enter" to KeyCodes(glfw = 257, sdl = 40),
    "key.keyboard.tab" to KeyCodes(glfw = 258, sdl = 43),
    "key.keyboard.backspace" to KeyCodes(glfw = 259, sdl = 42),
    "key.keyboard.insert" to KeyCodes(glfw = 260, sdl = 73),
    "key.keyboard.delete" to KeyCodes(glfw = 261, sdl = 76),
    "key.keyboard.right" to KeyCodes(glfw = 262, sdl = 79),
    "key.keyboard.left" to KeyCodes(glfw = 263, sdl = 80),
    "key.keyboard.down" to KeyCodes(glfw = 264, sdl = 81),
    "key.keyboard.up" to KeyCodes(glfw = 265, sdl = 82),
    "key.keyboard.page.up" to KeyCodes(glfw = 266, sdl = 75),
    "key.keyboard.page.down" to KeyCodes(glfw = 267, sdl = 78),
    "key.keyboard.home" to KeyCodes(glfw = 268, sdl = 74),
    "key.keyboard.end" to KeyCodes(glfw = 269, sdl = 77),
    "key.keyboard.caps.lock" to KeyCodes(glfw = 280, sdl = 57),
    "key.keyboard.scroll.lock" to KeyCodes(glfw = 281, sdl = 71),
    "key.keyboard.num.lock" to KeyCodes(glfw = 282, sdl = 83),
    "key.keyboard.print.screen" to KeyCodes(glfw = 283, sdl = 70),
    "key.keyboard.pause" to KeyCodes(glfw = 284, sdl = 72),
    "key.keyboard.f1" to KeyCodes(glfw = 290, sdl = 58),
    "key.keyboard.f2" to KeyCodes(glfw = 291, sdl = 59),
    "key.keyboard.f3" to KeyCodes(glfw = 292, sdl = 60),
    "key.keyboard.f4" to KeyCodes(glfw = 293, sdl = 61),
    "key.keyboard.f5" to KeyCodes(glfw = 294, sdl = 62),
    "key.keyboard.f6" to KeyCodes(glfw = 295, sdl = 63),
    "key.keyboard.f7" to KeyCodes(glfw = 296, sdl = 64),
    "key.keyboard.f8" to KeyCodes(glfw = 297, sdl = 65),
    "key.keyboard.f9" to KeyCodes(glfw = 298, sdl = 66),
    "key.keyboard.f10" to KeyCodes(glfw = 299, sdl = 67),
    "key.keyboard.f11" to KeyCodes(glfw = 300, sdl = 68),
    "key.keyboard.f12" to KeyCodes(glfw = 301, sdl = 69),
    "key.keyboard.f13" to KeyCodes(glfw = 302, sdl = 104),
    "key.keyboard.f14" to KeyCodes(glfw = 303, sdl = 105),
    "key.keyboard.f15" to KeyCodes(glfw = 304, sdl = 106),
    "key.keyboard.f16" to KeyCodes(glfw = 305, sdl = 107),
    "key.keyboard.f17" to KeyCodes(glfw = 306, sdl = 108),
    "key.keyboard.f18" to KeyCodes(glfw = 307, sdl = 109),
    "key.keyboard.f19" to KeyCodes(glfw = 308, sdl = 110),
    "key.keyboard.f20" to KeyCodes(glfw = 309, sdl = 111),
    "key.keyboard.f21" to KeyCodes(glfw = 310, sdl = 112),
    "key.keyboard.f22" to KeyCodes(glfw = 311, sdl = 113),
    "key.keyboard.f23" to KeyCodes(glfw = 312, sdl = 114),
    "key.keyboard.f24" to KeyCodes(glfw = 313, sdl = 115),
    "key.keyboard.keypad.0" to KeyCodes(glfw = 320, sdl = 98),
    "key.keyboard.keypad.1" to KeyCodes(glfw = 321, sdl = 89),
    "key.keyboard.keypad.2" to KeyCodes(glfw = 322, sdl = 90),
    "key.keyboard.keypad.3" to KeyCodes(glfw = 323, sdl = 91),
    "key.keyboard.keypad.4" to KeyCodes(glfw = 324, sdl = 92),
    "key.keyboard.keypad.5" to KeyCodes(glfw = 325, sdl = 93),
    "key.keyboard.keypad.6" to KeyCodes(glfw = 326, sdl = 94),
    "key.keyboard.keypad.7" to KeyCodes(glfw = 327, sdl = 95),
    "key.keyboard.keypad.8" to KeyCodes(glfw = 328, sdl = 96),
    "key.keyboard.keypad.9" to KeyCodes(glfw = 329, sdl = 97),
    "key.keyboard.keypad.decimal" to KeyCodes(glfw = 330, sdl = 220),
    "key.keyboard.keypad.divide" to KeyCodes(glfw = 331, sdl = 84),
    "key.keyboard.keypad.multiply" to KeyCodes(glfw = 332, sdl = 85),
    "key.keyboard.keypad.subtract" to KeyCodes(glfw = 333, sdl = 86),
    "key.keyboard.keypad.add" to KeyCodes(glfw = 334, sdl = 87),
    "key.keyboard.keypad.enter" to KeyCodes(glfw = 335, sdl = 88),
    "key.keyboard.keypad.equal" to KeyCodes(glfw = 336, sdl = 103),
    "key.keyboard.left.shift" to KeyCodes(glfw = 340, sdl = 225),
    "key.keyboard.left.control" to KeyCodes(glfw = 341, sdl = 224),
    "key.keyboard.left.alt" to KeyCodes(glfw = 342, sdl = 226),
    "key.keyboard.left.win" to KeyCodes(glfw = 343, sdl = 227),
    "key.keyboard.right.shift" to KeyCodes(glfw = 344, sdl = 229),
    "key.keyboard.right.control" to KeyCodes(glfw = 345, sdl = 228),
    "key.keyboard.right.alt" to KeyCodes(glfw = 346, sdl = 230),
    "key.keyboard.right.win" to KeyCodes(glfw = 347, sdl = 231),
    "key.keyboard.menu" to KeyCodes(glfw = 348, sdl = 118),
)

private val MOUSE_BUTTONS = listOf(
    "key.mouse.left" to KeyCodes(glfw = 0, sdl = 1),
    "key.mouse.right" to KeyCodes(glfw = 1, sdl = 3),
    "key.mouse.middle" to KeyCodes(glfw = 2, sdl = 2),
    "key.mouse.4" to KeyCodes(glfw = 3, sdl = 4),
    "key.mouse.5" to KeyCodes(glfw = 4, sdl = 5),
    "key.mouse.6" to KeyCodes(glfw = 5, sdl = 6),
    "key.mouse.7" to KeyCodes(glfw = 6, sdl = 7),
    "key.mouse.8" to KeyCodes(glfw = 7, sdl = 8),
)

private val GLFW_TO_SDL: Map<Int, Int> =
    KEYBOARD_CODES.associate { (_, codes) -> codes.glfw to codes.sdl } +
        MOUSE_BUTTONS.associate { (_, codes) -> codes.glfw to codes.sdl + KeybindNumbering.SDL_MOUSE_BINDING_OFFSET }

private val SDL_TO_GLFW: Map<Int, Int> = GLFW_TO_SDL.entries.associate { (glfw, sdl) -> sdl to glfw }
