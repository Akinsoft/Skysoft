package com.skysoft.data.skyblock

enum class SkyBlockMobType(val displayName: String, val glyph: Char) {
    AIRBORNE("Airborne", '\uE070'),
    ANIMAL("Animal", '\uE071'),
    AQUATIC("Aquatic", '\uE072'),
    ARCANE("Arcane", '\uE073'),
    ARTHROPOD("Arthropod", '\uE074'),
    CONSTRUCT("Construct", '\uE075'),
    CUBIC("Cubic", '\uE076'),
    ELUSIVE("Elusive", '\uE077'),
    ENDER("Ender", '\uE078'),
    FROZEN("Frozen", '\uE079'),
    GLACIAL("Glacial", '\uE07A'),
    HUMANOID("Humanoid", '\uE07B'),
    INFERNAL("Infernal", '\uE07C'),
    MAGMATIC("Magmatic", '\uE07D'),
    MYTHOLOGICAL("Mythological", '\uE07E'),
    PEST("Pest", '\uE07F'),
    SHIELDED("Shielded", '\uE080'),
    SKELETAL("Skeletal", '\uE081'),
    SPOOKY("Spooky", '\uE082'),
    SUBTERRANEAN("Subterranean", '\uE083'),
    UNDEAD("Undead", '\uE084'),
    WITHER("Wither", '\uE085'),
    WOODLAND("Woodland", '\uE086'),
    CRITTER("Critter", '\uE087'),
    TIMID("Timid", '\uE088'),
    ;

    companion object {
        private val byGlyph = entries.associateBy(SkyBlockMobType::glyph)

        fun fromGlyph(glyph: Char): SkyBlockMobType? = byGlyph[glyph]
    }
}
