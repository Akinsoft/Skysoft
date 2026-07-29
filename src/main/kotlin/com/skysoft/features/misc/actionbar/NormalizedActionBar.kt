package com.skysoft.features.misc.actionbar

import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

internal class NormalizedActionBar(private val raw: String) {
    private val rawIndices: IntArray
    val text: String

    init {
        val indices = mutableListOf<Int>()
        text = buildString {
            var index = 0
            while (index < raw.length) {
                if (raw[index] == LEGACY_FORMAT_PREFIX && index + 1 < raw.length) {
                    index += LEGACY_FORMAT_LENGTH
                } else {
                    append(raw[index])
                    indices += index
                    index++
                }
            }
        }
        rawIndices = indices.toIntArray()
    }

    fun rawRange(range: IntRange, preserveLastCharacter: Boolean = false): IntRange {
        val start = formattingStart(rawIndices[range.first])
        val last = rawIndices[range.last]
        return if (preserveLastCharacter) start until formattingStart(last) else start..last
    }

    private fun formattingStart(index: Int): Int {
        var start = index
        while (start >= LEGACY_FORMAT_LENGTH && raw[start - LEGACY_FORMAT_LENGTH] == LEGACY_FORMAT_PREFIX) {
            start -= LEGACY_FORMAT_LENGTH
        }
        return start
    }
}

internal fun String.actionBarSegmentRange(match: IntRange): IntRange {
    var start = match.first
    val endExclusive = match.last + 1
    while (start > 0 && this[start - 1] == ' ') start--
    var trailingEnd = endExclusive
    while (getOrNull(trailingEnd) == ' ') trailingEnd++
    if (match.first - start >= STATUS_SEPARATOR_LENGTH) {
        return start until if (trailingEnd == length) trailingEnd else endExclusive
    }
    return if (trailingEnd - endExclusive >= STATUS_SEPARATOR_LENGTH) match.first until trailingEnd else match
}

internal fun Component.withoutRanges(ranges: List<IntRange>): Component {
    if (ranges.isEmpty()) return this
    val output = Component.empty()
    var offset = 0
    visit({ style: Style, text: String ->
        val kept = text.filterIndexed { index, _ -> ranges.none { offset + index in it } }
        if (kept.isNotEmpty()) output.append(Component.literal(kept).withStyle(style))
        offset += text.length
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return output
}

private const val STATUS_SEPARATOR_LENGTH = 2
private const val LEGACY_FORMAT_PREFIX = '§'
private const val LEGACY_FORMAT_LENGTH = 2
