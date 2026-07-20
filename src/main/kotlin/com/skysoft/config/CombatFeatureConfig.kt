package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class CombatFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Cocoon Display", desc = "Show cocooned mob names and hatch timers in the world.")
    val cocoonDisplay = CocoonDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Better Shurikens", desc = "Show shuriken status at mobs' feet.")
    val betterShurikens = BetterShurikensConfig()

    fun repairLoadedValues() {
        betterShurikens.settings.repairLoadedValues()
    }
}

class CocoonDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show cocooned mob names and hatch timers in the world.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Cocoon display settings.")
    @field:Accordion
    val settings = CocoonDisplaySettingsConfig()
}

class CocoonDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Only Slayer Targets",
        desc = "During active Slayer quests, only show bosses and mini-bosses.",
    )
    @field:ConfigEditorBoolean
    var onlySlayerTargets = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Timer Prefix", desc = "Show \"Hatches in\" before the cocoon timer.")
    @field:ConfigEditorBoolean
    var showTimerPrefix = true
}

class BetterShurikensConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show shuriken status at mobs' feet.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Better Shurikens settings.")
    @field:Accordion
    val settings = BetterShurikensSettingsConfig()
}

class BetterShurikensSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reminder Mobs", desc = "Choose mobs that show a red shuriken reminder.")
    @field:ConfigEditorDraggableList
    val reminderMobs: Property<MutableList<BetterShurikenReminderMob>> =
        Property.of(BetterShurikenReminderMob.entries.toMutableList())

    fun repairLoadedValues() {
        reminderMobs.set(validReminderMobs(reminderMobs.get()))
    }
}

enum class BetterShurikenReminderMob(
    private val displayName: String,
    vararg labels: String,
) {
    KING_MINOS("King Minos"),
    MINOS_INQUISITOR("Minos Inquisitor"),
    MINOS_CHAMPION("Minos Champion"),
    SPHINX("Sphinx"),
    MANTICORE("Manticore"),
    REVENANT_HORRORS("Revenant Horror / Atoned Horror", "Revenant Horror", "Atoned Horror"),
    TARANTULA_BROODFATHERS(
        "Tarantula Broodfather / Primordial Broodfather",
        "Tarantula Broodfather",
        "Primordial Broodfather",
    ),
    SVEN_PACKMASTER("Sven Packmaster"),
    VOIDGLOOM_SERAPH("Voidgloom Seraph"),
    INFERNO_DEMONLORD("Inferno Demonlord"),
    RIFTSTALKER_BLOODFIEND("Riftstalker Bloodfiend"),
    TITANOBOA("Titanoboa"),
    FROG_PRINCE("Frog Prince"),
    NESSIE("Nessie"),
    GIANT_ISOPOD("Giant Isopod"),
    GRIM_REAPER("Grim Reaper"),
    YETI("Yeti"),
    GREAT_WHITE_SHARK("Great White Shark"),
    THE_LOCH_EMPEROR("The Loch Emperor"),
    REINDRAKE("Reindrake"),
    PLHLEGBLAST("Plhlegblast"),
    THUNDER("Thunder"),
    WIKI_TIKI("Wiki Tiki"),
    LORD_JAWBUS("Lord Jawbus"),
    RAGNAROK("Ragnarok"),
    ;

    val matchLabels: List<String> = labels.toList().ifEmpty { listOf(displayName) }

    override fun toString(): String = displayName
}

internal fun validReminderMobs(mobs: Collection<BetterShurikenReminderMob?>): MutableList<BetterShurikenReminderMob> =
    mobs.filterNotNullTo(mutableListOf())
