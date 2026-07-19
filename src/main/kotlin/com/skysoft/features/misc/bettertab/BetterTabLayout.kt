package com.skysoft.features.misc.bettertab

import com.skysoft.data.hypixel.TabListEntry
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import java.util.Locale
import java.util.Optional
import java.util.UUID

internal data class BetterTabRow(
    val component: Component,
    val playerId: UUID? = null,
    val isTitle: Boolean = false,
)

internal data class BetterTabColumn(val rows: List<BetterTabRow>)

internal data class BetterTabLayout(
    val headerLines: List<Component>,
    val columns: List<BetterTabColumn>,
    val footerLines: List<Component>,
)

internal object BetterTabLayoutBuilder {
    private const val SOURCE_COLUMN_ROWS = 20
    private const val MAXIMUM_PROFILE_NAME_LENGTH = 14
    private const val HYPIXEL_ADVERTISING_TEXT = "HYPIXEL.NET"
    private val profileLinePattern = Regex("^(?<prefix>\\s*Profile:\\s*)(?<name>.+)$")
    private val playerColumnPattern = Regex("Players \\((?<count>\\d+)\\)")
    private val otherPlayersPattern = Regex("and \\d+ other players?\\.\\.\\.", RegexOption.IGNORE_CASE)

    fun build(
        entries: List<TabListEntry>,
        header: Component?,
        footer: Component?,
        maximumRows: Int,
        isSecondPlayerColumnHidden: Boolean,
    ): BetterTabLayout {
        val footerContent = parseFooter(footer)
        val blocks = parseEntryBlocks(entries, isSecondPlayerColumnHidden) + footerContent.blocks
        return BetterTabLayout(
            headerLines = header?.splitStyledLines()?.filterNot { it.isVisuallyBlank() }.orEmpty(),
            columns = packBlocks(blocks, maximumRows),
            footerLines = footerContent.bannerLines,
        )
    }

    private fun parseEntryBlocks(entries: List<TabListEntry>, isSecondPlayerColumnHidden: Boolean): List<ContentBlock> {
        if (entries.isEmpty()) return emptyList()
        val groups = linkedMapOf<String, SourceGroup>()
        val rowsPerColumn = sourceColumnRows(entries.size)
        for (start in entries.indices step rowsPerColumn) {
            val sourceEntries = entries.subList(start, minOf(start + rowsPerColumn, entries.size))
            val title = sourceEntries.first().displayName.trimmedCopy()
            val titleText = title.cleanSkyBlockText()
            val key = titleText.lowercase(Locale.ROOT).ifEmpty { "column:$start" }
            val playerCount = playerColumnPattern.matchEntire(titleText)?.groups?.get("count")?.value?.toInt()
            val existingGroup = groups[key]
            if (isSecondPlayerColumnHidden && playerCount != null && existingGroup != null) {
                existingGroup.otherPlayerCount = (playerCount - existingGroup.visiblePlayerCount).coerceAtLeast(0)
                continue
            }
            val group = existingGroup ?: SourceGroup(key, title.takeUnless { it.isVisuallyBlank() }).also {
                groups[key] = it
            }
            if (playerCount != null) {
                group.visiblePlayerCount = sourceEntries.drop(1).count { entry ->
                    !entry.displayName.isVisuallyBlank() &&
                        !otherPlayersPattern.matches(entry.displayName.string.trim())
                }
            }
            group.sections += splitEntrySections(sourceEntries.drop(1))
        }
        return groups.values.flatMap { group ->
            val sections = group.sections.ifEmpty {
                if (group.otherPlayerCount > 0) listOf(emptyList()) else emptyList()
            }
            sections.mapIndexed { index, section ->
                val rows = section.mapTo(mutableListOf()) { entry ->
                    BetterTabRow(
                        component = entry.displayName.withCompactProfileName(),
                        playerId = entry.uuid.takeIf { entry.isPlayerEntry() },
                    )
                }
                if (index == sections.lastIndex && group.otherPlayerCount > 0) {
                    val noun = if (group.otherPlayerCount == 1) "player" else "players"
                    val summary = Component.literal("and ${group.otherPlayerCount} other $noun...")
                        .withStyle(ChatFormatting.GRAY)
                    rows += BetterTabRow(summary)
                }
                ContentBlock(groupKey = group.key, title = group.title, rows = rows)
            }
        }
    }

    private fun sourceColumnRows(entryCount: Int): Int {
        var columns = 1
        var rows = entryCount
        while (rows > SOURCE_COLUMN_ROWS) {
            columns++
            rows = (entryCount + columns - 1) / columns
        }
        return rows.coerceAtLeast(1)
    }

    private fun splitEntrySections(entries: List<TabListEntry>): List<List<TabListEntry>> {
        val sections = mutableListOf<List<TabListEntry>>()
        var current = mutableListOf<TabListEntry>()
        for (entry in entries) {
            if (entry.displayName.isVisuallyBlank()) {
                if (current.isNotEmpty()) {
                    sections += current
                    current = mutableListOf()
                }
            } else {
                current += entry
            }
        }
        if (current.isNotEmpty()) sections += current
        return sections
    }

    private fun parseFooter(footer: Component?): FooterContent {
        val lines = footer?.splitStyledLines().orEmpty()
        val bannerLines = lines.filter { it.isHypixelAdvertisingLine() }
        val contentLines = lines.map { line ->
            if (line.isHypixelAdvertisingLine()) Component.empty() else line
        }
        val blocks = splitComponentSections(contentLines).map { section ->
            ContentBlock(groupKey = null, title = null, rows = section.map(::BetterTabRow))
        }
        return FooterContent(blocks, bannerLines)
    }

    private fun splitComponentSections(lines: List<Component>): List<List<Component>> {
        val sections = mutableListOf<List<Component>>()
        var current = mutableListOf<Component>()
        for (line in lines) {
            if (line.isVisuallyBlank()) {
                if (current.isNotEmpty()) {
                    sections += current
                    current = mutableListOf()
                }
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) sections += current
        return sections
    }

    private fun packBlocks(blocks: List<ContentBlock>, maximumRows: Int): List<BetterTabColumn> {
        require(maximumRows > 0) { "Better TAB requires at least one row per column" }
        val columns = mutableListOf<BetterTabColumn>()
        var column = ColumnBuilder()
        for (block in blocks) {
            var remainingRows = block.rows
            while (remainingRows.isNotEmpty()) {
                val hasTitle = block.title != null && column.groupKey != block.groupKey
                val hasSpacer = column.rows.isNotEmpty()
                val prefixSize = hasTitle.toInt() + hasSpacer.toInt()
                val availableRows = maximumRows - column.rows.size
                val completeBlockSize = prefixSize + remainingRows.size
                if (column.rows.isNotEmpty() && completeBlockSize > availableRows && block.minimumSize() <= maximumRows) {
                    columns += column.build()
                    column = ColumnBuilder()
                    continue
                }
                if (prefixSize >= availableRows) {
                    columns += column.build()
                    column = ColumnBuilder()
                    continue
                }
                if (hasSpacer) column.rows += BetterTabRow(Component.empty())
                if (hasTitle) column.rows += BetterTabRow(block.title, isTitle = true)
                column.groupKey = block.groupKey
                val count = minOf(remainingRows.size, maximumRows - column.rows.size)
                column.rows += remainingRows.take(count)
                remainingRows = remainingRows.drop(count)
                if (remainingRows.isNotEmpty()) {
                    columns += column.build()
                    column = ColumnBuilder()
                }
            }
        }
        if (column.rows.isNotEmpty()) columns += column.build()
        return columns
    }

    private fun ContentBlock.minimumSize(): Int = rows.size + (title != null).toInt()

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    private fun Component.isHypixelAdvertisingLine(): Boolean =
        string.contains(HYPIXEL_ADVERTISING_TEXT, ignoreCase = true)

    private fun Component.isVisuallyBlank(): Boolean = string.isBlank()

    private fun TabListEntry.isPlayerEntry(): Boolean {
        val start = displayName.string.indexOf(profileName, ignoreCase = true)
        if (start < 0) return false
        val before = displayName.string.getOrNull(start - 1)
        val after = displayName.string.getOrNull(start + profileName.length)
        return before?.isUsernameCharacter() != true && after?.isUsernameCharacter() != true
    }

    private fun Char.isUsernameCharacter(): Boolean = isLetterOrDigit() || this == '_'

    private fun Component.withCompactProfileName(): Component {
        val match = profileLinePattern.matchEntire(string) ?: return this
        val name = match.groups["name"] ?: return this
        if (name.value.length <= MAXIMUM_PROFILE_NAME_LENGTH) return this
        return styledPrefixWithEllipsis(name.range.first + MAXIMUM_PROFILE_NAME_LENGTH)
    }

    private fun Component.trimmedCopy(): Component {
        val text = string
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return Component.empty()
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        if (start == 0 && end == text.length) return copy()
        return styledSlice(start, end)
    }

    private fun Component.styledPrefixWithEllipsis(end: Int): Component {
        val result = Component.empty()
        var offset = 0
        var ellipsisStyle = Style.EMPTY
        visit({ style: Style, segment: String ->
            val segmentStart = offset
            offset += segment.length
            val to = minOf(end - segmentStart, segment.length)
            if (to > 0) {
                result.append(Component.literal(segment.substring(0, to)).withStyle(style))
                ellipsisStyle = style
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return result.append(Component.literal("...").withStyle(ellipsisStyle))
    }

    private fun Component.styledSlice(start: Int, end: Int): Component {
        val result = Component.empty()
        var offset = 0
        visit({ style: Style, segment: String ->
            val segmentStart = offset
            offset += segment.length
            val from = maxOf(start - segmentStart, 0)
            val to = minOf(end - segmentStart, segment.length)
            if (from < to) result.append(Component.literal(segment.substring(from, to)).withStyle(style))
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return result
    }

    private fun Component.splitStyledLines(): List<Component> {
        val lines = mutableListOf<MutableComponent>(Component.empty())
        visit({ style: Style, segment: String ->
            var start = 0
            for (index in segment.indices) {
                if (segment[index] == '\n') {
                    lines.last().appendStyledText(segment.substring(start, index), style)
                    lines += Component.empty()
                    start = index + 1
                }
            }
            lines.last().appendStyledText(segment.substring(start), style)
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return lines
    }

    private fun MutableComponent.appendStyledText(text: String, style: Style) {
        if (text.isNotEmpty()) append(Component.literal(text).withStyle(style))
    }

    private data class SourceGroup(
        val key: String,
        val title: Component?,
        val sections: MutableList<List<TabListEntry>> = mutableListOf(),
        var visiblePlayerCount: Int = 0,
        var otherPlayerCount: Int = 0,
    )

    private data class ContentBlock(
        val groupKey: String?,
        val title: Component?,
        val rows: List<BetterTabRow>,
    )

    private data class FooterContent(
        val blocks: List<ContentBlock>,
        val bannerLines: List<Component>,
    )

    private class ColumnBuilder {
        val rows = mutableListOf<BetterTabRow>()
        var groupKey: String? = null

        fun build() = BetterTabColumn(rows.toList())
    }
}
