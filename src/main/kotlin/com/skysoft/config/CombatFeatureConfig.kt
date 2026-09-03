package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import com.skysoft.data.skyblock.SkyBlockSlayerType
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property
import java.util.Locale

class CombatFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Healing Pool", desc = "Mark active Wisp healing pools.")
    val healingPool = HealingPoolConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Cocoon Display", desc = "Show cocooned mob names and hatch timers in the world.")
    val cocoonDisplay = CocoonDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Better Shurikens", desc = "Show shuriken status at mobs' feet.")
    val betterShurikens = BetterShurikensConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Bestiary Helper", desc = "Select Bestiary mobs to highlight in the world.")
    val bestiaryHelper = BestiaryHelperConfig()

    override fun repairLoadedValues() {
        betterShurikens.settings.repairLoadedValues()
        bestiaryHelper.repairLoadedValues()
    }
}

class BestiaryHelperConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show a mob selector beside Bestiary menus and highlight selected mobs.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the target marker and mob highlight.")
    @field:Accordion
    val details = BestiaryHelperDetailsConfig()

    @JvmField
    @field:Expose
    val selectedMobs: MutableList<String> = mutableListOf()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 70, centerX = false, centerY = false).rememberDefault()

    override fun repairLoadedValues() {
        val repairedMobs = selectedMobs.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { name -> name.lowercase(Locale.ROOT) }
            .toList()
        selectedMobs.clear()
        selectedMobs.addAll(repairedMobs)
    }
}

class BestiaryHelperDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Target Text", desc = "Text shown above selected Bestiary mobs.")
    @field:ConfigEditorText
    var targetText = ""

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mob Highlight Color", desc = "Color used to highlight selected Bestiary mobs.")
    @field:ConfigEditorColour
    val highlightColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Text Color", desc = "Color used for the target marker.")
    @field:ConfigEditorColour
    val textColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 85, 0, 255))
}

class HealingPoolConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Mark active Wisp healing pools.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which healing pool indicators appear.")
    @field:Accordion
    val settings = HealingPoolSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize healing pool indicators.")
    @field:Accordion
    val details = HealingPoolDetailsConfig()
}

class HealingPoolSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Text", desc = "Show text above active healing pools.")
    @field:ConfigEditorBoolean
    var showText = true
}

class HealingPoolDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Line and Text Color", desc = "Color used for the line and text.")
    @field:ConfigEditorColour
    val color: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Text", desc = "Text shown above active healing pools.")
    @field:ConfigEditorText
    var text = "Heal!"
}

class CocoonDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show cocooned mob names and hatch timers in the world.")
    @field:MainFeatureToggle
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
    @field:MainFeatureToggle
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
    REVENANT_HORRORS(
        "Revenant Horror / Atoned Horror",
        *SkyBlockSlayerType.ZOMBIE.bossNames(5).toTypedArray(),
    ),
    TARANTULA_BROODFATHERS(
        "Tarantula Broodfather / Conjoined Brood",
        *SkyBlockSlayerType.SPIDER.bossNames(5).toTypedArray(),
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
