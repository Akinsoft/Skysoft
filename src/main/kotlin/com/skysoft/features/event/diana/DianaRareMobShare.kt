package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.data.skyblock.withoutSkyBlockMobModifierPrefix
import com.skysoft.features.combat.CocoonMessageParser
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.worldVecOrNull
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.WorldVec
import java.util.regex.Pattern

internal data class DianaRareMobShare(
    val mob: DianaRareMobOption,
    val location: WorldVec,
    val marker: String = mob.shareMarker,
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

    fun format(share: DianaRareMobShare): String {
        val x = share.location.x.toInt()
        val y = share.location.y.toInt()
        val z = share.location.z.toInt()
        return "x: $x, y: $y, z: $z | ${share.mob.label}"
    }

    fun formatClear(mob: DianaRareMobOption): String =
        mob.shareMarker.replaceFirst("Found", "Defeated")

    fun formatCocoon(mob: DianaRareMobOption): String =
        "Cocooned a ${mob.label}!"

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
            val mob = DianaRareMobOption.fromLabel(match.group("mob")) ?: return null
            return DianaRareMobClear(mob, match.group("marker"))
        }
        return genericClearPattern.matchEntire(body)?.let { match -> DianaRareMobClear(null, match.group("marker")) }
    }

    fun parsePlayerDeath(message: String): DianaRareMobPlayerDeath? {
        val match = playerDeathPattern.matchEntire(message) ?: return null
        val mob = mobFromDeathKiller(match.group("killer")) ?: return null
        return DianaRareMobPlayerDeath(match.group("player"), mob)
    }

    fun parseLocalCocoon(message: String): DianaRareMobCocoon? {
        val cocoon = CocoonMessageParser.parseLocal(message) ?: return null
        val mob = mobFromShareText(cocoon.mobName) ?: return null
        return DianaRareMobCocoon(mob, cocoon.marker)
    }

    fun parseCocoon(message: String): DianaRareMobCocoon? {
        val match = partyCocoonPattern.matchEntire(message.partyBodyOrSelf()) ?: return null
        val mob = mobFromShareText(match.group("mob")) ?: return null
        return DianaRareMobCocoon(mob, match.group("marker"))
    }

    private fun String.partyBodyOrSelf(): String {
        val clean = cleanSkyBlockText()
        return (partyBodyPattern.matchEntire(clean)?.group("body") ?: clean).trim()
    }

    private fun parseLegacyFoundMessage(match: MatchResult): DianaRareMobShare? {
        val mob = DianaRareMobOption.fromLabel(match.group("mob")) ?: return null
        return shareFromGroups(match, mob)
    }

    private fun parseCoordinateShare(match: MatchResult): DianaRareMobShare? {
        val mob = mobFromShareText(match.group("mob")) ?: return null
        return shareFromGroups(match, mob)
    }

    private fun parseInquisitorSpawnShare(match: MatchResult): DianaRareMobShare? =
        shareFromGroups(match, DianaRareMobOption.MINOS_INQUISITOR)

    private fun shareFromGroups(match: MatchResult, mob: DianaRareMobOption): DianaRareMobShare? =
        DianaRareMobShare(mob, match.worldVecOrNull() ?: return null, match.group("marker"))

    private fun mobFromShareText(text: String): DianaRareMobOption? {
        val cleaned = text.trim().removePrefix("|").trim().removeSuffix("!").trim()
        return DianaRareMobOption.fromMobName(cleaned)
    }

    private fun mobFromDeathKiller(text: String): DianaRareMobOption? {
        val cleaned = text.trim().removeSuffix(".").trim()
        DianaRareMobOption.fromLabel(cleaned)?.let { return it }
        return cleaned.withoutSkyBlockMobModifierPrefix()
            .takeIf { it != cleaned }
            ?.let(DianaRareMobOption::fromLabel)
    }
}
