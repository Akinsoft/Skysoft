package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.features.combat.CocoonMessageParser
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.WorldVec
import java.util.regex.Pattern

internal data class DianaRareMobShare(
    val mob: DianaRareMobOption,
    val location: WorldVec,
    val marker: String = DianaRareMobShareParser.marker(mob),
)

internal data class DianaRareMobClear(
    val mob: DianaRareMobOption?,
    val marker: String,
)

internal data class DianaRareMobPlayerDeath(
    val player: String,
    val mob: DianaRareMobOption,
)

internal data class DianaRareMobCocoon(
    val mob: DianaRareMobOption,
    val marker: String,
)

internal object DianaRareMobShareParser {
    private val labels = DianaRareMobOption.entries
        .flatMap { it.matchLabels }
        .joinToString("|") { Pattern.quote(it) }
    private const val NUMBER = """-?\d+(?:\.\d+)?"""
    private val legacyFoundPattern = Regex(
        "^(?<marker>Found (?:a |an )?(?<mob>$labels)!) @ " +
            """(?<x>-?\d+) (?<y>-?\d+) (?<z>-?\d+)$""",
    )
    private val coordinateSharePattern = Regex(
        "^(?<marker>x:) (?<x>$NUMBER),? y: " +
            """(?<y>$NUMBER),? z: (?<z>$NUMBER)(?: \| (?<mob>.*))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val inquisitorSpawnPattern = Regex(
        "^(?<marker>A MINOS INQUISITOR) has spawned near \\[.*] at Coords " +
            "(?<x>$NUMBER) (?<y>$NUMBER) (?<z>$NUMBER)$",
        RegexOption.IGNORE_CASE,
    )
    private val explicitClearPattern = Regex(
        """^(?<marker>Defeated (?:by )?(?:a |an )?(?<mob>$labels)!)$""",
    )
    private val genericClearPattern = Regex(
        """^(?<marker>(?:Inquisitor|Rare Diana Mob) dead!)$""",
        RegexOption.IGNORE_CASE,
    )
    private val playerDeathPattern = Regex(
        """^(?:☠ )?(?<player>[A-Za-z0-9_]{1,16}) was killed by (?<killer>.+)\.$""",
    )
    private val partyCocoonPattern = Regex(
        """^(?<marker>Cocooned an? (?<mob>[^!]+)!)$""",
        RegexOption.IGNORE_CASE,
    )
    private val partyBodyPattern = Regex("""^Party > .+?: (?<body>.+)$""")

    fun marker(mob: DianaRareMobOption): String = mob.shareMarker
    fun clearMarker(mob: DianaRareMobOption): String = mob.shareMarker.replaceFirst("Found", "Defeated")
    fun cocoonMarker(mob: DianaRareMobOption): String = "Cocooned a ${mob.label}!"

    fun format(share: DianaRareMobShare): String {
        val x = share.location.x.toInt()
        val y = share.location.y.toInt()
        val z = share.location.z.toInt()
        return "x: $x, y: $y, z: $z | ${share.mob.label}"
    }

    fun formatClear(mob: DianaRareMobOption): String =
        clearMarker(mob)

    fun formatCocoon(mob: DianaRareMobOption): String =
        cocoonMarker(mob)

    const val SHARED_CLEAR_MESSAGE = "Rare Diana Mob dead!"

    fun parse(message: String): DianaRareMobShare? {
        val body = message.partyBodyOrSelf()
        legacyFoundPattern.matchEntire(body)?.let { return parseLegacyFoundMessage(it) }
        coordinateSharePattern.matchEntire(body)?.let { return parseCoordinateShare(it) }
        inquisitorSpawnPattern.matchEntire(body)?.let { return parseInquisitorSpawnShare(it) }
        return null
    }

    fun parseClear(message: String): DianaRareMobClear? {
        val body = message.partyBodyOrSelf()
        explicitClearPattern.matchEntire(body)?.let { match ->
            return DianaRareMobClear(
                mob = DianaRareMobOption.fromLabel(match.groups["mob"]?.value.orEmpty()) ?: return null,
                marker = match.groups["marker"]?.value.orEmpty(),
            )
        }
        genericClearPattern.matchEntire(body)?.let { match ->
            return DianaRareMobClear(
                mob = null,
                marker = match.groups["marker"]?.value.orEmpty(),
            )
        }
        return null
    }

    fun parsePlayerDeath(message: String): DianaRareMobPlayerDeath? {
        val match = playerDeathPattern.matchEntire(message) ?: return null
        val mob = mobFromDeathKiller(match.groups["killer"]?.value.orEmpty()) ?: return null
        return DianaRareMobPlayerDeath(
            player = match.groups["player"]?.value.orEmpty(),
            mob = mob,
        )
    }

    fun parseLocalCocoon(message: String): DianaRareMobCocoon? {
        val cocoon = CocoonMessageParser.parseLocal(message) ?: return null
        val mob = mobFromShareText(cocoon.mobName) ?: return null
        return DianaRareMobCocoon(mob, cocoon.marker)
    }

    fun parseCocoon(message: String): DianaRareMobCocoon? {
        val match = partyCocoonPattern.matchEntire(message.partyBodyOrSelf()) ?: return null
        val mob = mobFromShareText(match.groups["mob"]?.value.orEmpty()) ?: return null
        return DianaRareMobCocoon(mob, match.groups["marker"]?.value.orEmpty())
    }

    private fun String.partyBodyOrSelf(): String {
        val clean = cleanSkyBlockText()
        return partyBodyPattern.matchEntire(clean)
            ?.groups
            ?.get("body")
            ?.value
            ?.trim()
            ?: clean.trim()
    }

    private fun parseLegacyFoundMessage(match: MatchResult): DianaRareMobShare? {
        val mob = DianaRareMobOption.fromLabel(match.groups["mob"]?.value.orEmpty()) ?: return null
        return shareFromGroups(match, mob, match.groups["marker"]?.value ?: marker(mob))
    }

    private fun parseCoordinateShare(match: MatchResult): DianaRareMobShare? {
        val mob = mobFromShareText(match.groups["mob"]?.value.orEmpty()) ?: return null
        return shareFromGroups(match, mob, match.groups["marker"]?.value ?: "x:")
    }

    private fun parseInquisitorSpawnShare(match: MatchResult): DianaRareMobShare? =
        shareFromGroups(
            match,
            DianaRareMobOption.MINOS_INQUISITOR,
            match.groups["marker"]?.value ?: "A MINOS INQUISITOR",
        )

    private fun shareFromGroups(match: MatchResult, mob: DianaRareMobOption, marker: String): DianaRareMobShare? {
        return DianaRareMobShare(
            mob = mob,
            location = WorldVec(
                match.groups["x"]?.value?.toDoubleOrNull() ?: return null,
                match.groups["y"]?.value?.toDoubleOrNull() ?: return null,
                match.groups["z"]?.value?.toDoubleOrNull() ?: return null,
            ),
            marker = marker,
        )
    }

    private fun mobFromShareText(text: String): DianaRareMobOption? {
        val cleaned = text.trim().removePrefix("|").trim().removeSuffix("!").trim()
        return DianaRareMobOption.fromMobName(cleaned)
    }

    private fun mobFromDeathKiller(text: String): DianaRareMobOption? {
        val cleaned = text.trim().removeSuffix(".").trim()
        DianaRareMobOption.fromLabel(cleaned)?.let { return it }
        return DIANA_DEATH_MOB_PREFIXES
            .asSequence()
            .mapNotNull { prefix -> cleaned.removePrefix("$prefix ").takeIf { it != cleaned } }
            .mapNotNull(DianaRareMobOption::fromLabel)
            .firstOrNull()
    }

    private val DIANA_DEATH_MOB_PREFIXES = setOf("Empyrean", "Exalted", "Runic", "Venerable", "Stalwart", "Blessed")
}
