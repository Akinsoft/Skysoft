package com.skysoft.data.skyblock

internal enum class SkyBlockStatGlyph(val character: Char) {
    DAMAGE('\uE050'),
    HEALTH('\uE010'),
    DEFENSE('\uE008'),
    TRUE_DEFENSE('\uE027'),
    STRENGTH('\uE00D'),
    CRIT_CHANCE('\uE02C'),
    CRIT_DAMAGE('\uE007'),
    ATTACK_SPEED('\uE001'),
    FEROCITY('\uE00B'),
    SWING_RANGE('\uE024'),
    INTELLIGENCE('\uE003'),
    OVERFLOW_MANA('\uE017'),
    ABILITY_DAMAGE('\uE002'),
    HEALTH_REGEN('\uE011'),
    VITALITY('\uE028'),
    MENDING('\uE014'),
    BREAKING_POWER('\uE005'),
    MINING_SPEED('\uE015'),
    MINING_SPREAD('\uE016'),
    GEMSTONE_SPREAD('\uE00F'),
    PRISTINE('\uE01C'),
    MINING_FORTUNE('\uE053'),
    BONUS_PEST_CHANCE('\uE019'),
    OVERBLOOM('\uE02B'),
    FARMING_FORTUNE('\uE051'),
    SWEEP('\uE023'),
    FORAGING_FORTUNE('\uE054'),
    FISHING_SPEED('\uE00C'),
    SEA_CREATURE_CHANCE('\uE021'),
    DOUBLE_HOOK_CHANCE('\uE009'),
    TROPHY_FISH_CHANCE('\uE02A'),
    TREASURE_CHANCE('\uE025'),
    SPEED('\uE022'),
    MAGIC_FIND('\uE01A'),
    PET_LUCK('\uE013'),
    HEAT_RESISTANCE('\uE012'),
    COLD_RESISTANCE('\uE006'),
    RESPIRATION('\uE01D'),
    PRESSURE_RESISTANCE('\uE01B'),
    FEAR('\uE00A'),
    TRACKING('\uE077'),
    PULL('\uE02D'),
    HUNTER_FORTUNE('\uE05B'),
    RIFT_TIME('\uE020'),
    RIFT_DAMAGE('\uE01E'),
    ;

    override fun toString(): String = character.toString()

    companion object {
        fun normalizeForRendering(text: String): String =
            text.replace(LEGACY_MAGIC_FIND_GLYPH, MAGIC_FIND.character)

        fun forServerChat(text: String): String =
            entries.fold(normalizeForRendering(text)) { result, glyph -> result.replace(glyph.character.toString(), "") }
                .replace(whitespacePattern, " ")
                .trim()

        private const val LEGACY_MAGIC_FIND_GLYPH = '\u272F'
        private val whitespacePattern = Regex("""\s+""")
    }
}
