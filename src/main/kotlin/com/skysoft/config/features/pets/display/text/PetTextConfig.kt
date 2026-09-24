package com.skysoft.config.features.pets.display.text

import com.google.gson.annotations.Expose
import com.skysoft.utils.gui.GuiAlignment
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.observer.Property

interface PetTextDisplaySettings {
    val enabledTexts: Property<MutableList<PetTextConfig.TextElement>>
    val textLabels: Property<Boolean>
    val nameLevel: Property<Boolean>
    val nameSkinSymbol: Property<Boolean>
    val nextLevelPercent: Property<Boolean>
    val xpFormat: Property<PetTextConfig.NumberFormatEntry>
    val textScale: Property<Float>
    val textLocation: Property<PetTextConfig.TextLocationOption>
    val verticalAlign: Property<GuiAlignment.VerticalAlignment>
    val horizontalAlign: Property<GuiAlignment.HorizontalAlignment>
}

class PetTextConfig {
    enum class TextElement(private val displayName: String, private val label: String = "") {
        PET_NAME("§7[Lvl 100] §6Golden Dragon §d✦"),
        NEXT_LEVEL("§b3,000§9/§b6,000 §7- §e50%", "Level Progress"),
        OVERFLOW_XP("§7+§b3,500,000", "Max-Level XP"),
        TOTAL_XP("§b25,300,000", "Pet XP"),
        HELD_ITEM("§9Minos Relic", "Pet Item"),
        ;

        fun getFormattedLabel(): String = label.takeIf { it.isNotEmpty() }?.let { "§e$it§7: " }.orEmpty()
        override fun toString(): String = getFormattedLabel() + displayName
    }

    enum class NumberFormatEntry(private val displayName: String) {
        DEFAULT("Default"),
        FORMATTED("Formatted"),
        UNFORMATTED("Unformatted"),
        ;

        override fun toString(): String = displayName
    }

    enum class TextLocationOption(private val displayName: String) {
        TOP("Top"),
        BOTTOM("Bottom"),
        LEFT("Left"),
        RIGHT("Right"),
        ;

        override fun toString(): String = displayName
    }

    @Expose
    @ConfigOption(name = "Equipped Pet Text", desc = "")
    @Accordion
    @ConfigOrder(10)
    val equippedPet: EquippedPetTextConfig = EquippedPetTextConfig()

    class EquippedPetTextConfig : PetTextDisplaySettings {
        @Expose
        @ConfigOption(
            name = "Enabled Text",
            desc = "Show text relating to your pet in the GUI element.\n§eItems that are gray are dependent on the items in red.",
        )
        @ConfigEditorDraggableList
        @ConfigOrder(10)
        override val enabledTexts: Property<MutableList<TextElement>> = Property.of(
            mutableListOf(TextElement.PET_NAME, TextElement.NEXT_LEVEL, TextElement.TOTAL_XP, TextElement.HELD_ITEM)
        )

        @Expose
        @ConfigOption(name = "Text Labels", desc = "Show labels before each text line explaining what data it is.")
        @ConfigEditorBoolean
        @ConfigOrder(20)
        override val textLabels: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Pet Level", desc = "Show pet level in the pet name text.\n§ePet Name must be enabled above.")
        @ConfigEditorBoolean
        @ConfigOrder(30)
        override val nameLevel: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Skin Symbol", desc = "Show a symbol for pet skin in the pet name text.\n§ePet Name must be enabled above.")
        @ConfigEditorBoolean
        @ConfigOrder(40)
        override val nameSkinSymbol: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(
            name = "Show Overflow XP",
            desc = "Show exact Pet XP for max-level pets instead of MAX LEVEL.\n" +
                "§eRequires Pet XP under Enabled Text and overflow XP in Hypixel's Pet widget.",
        )
        @ConfigEditorBoolean
        @ConfigOrder(50)
        val showOverflowXp: Property<Boolean> = Property.of(false)

        @Expose
        @ConfigOption(name = "Next Level %", desc = "Show a percentage after your exp progress.\n§eNext Level must be enabled above.")
        @ConfigEditorBoolean
        @ConfigOrder(60)
        override val nextLevelPercent: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(
            name = "XP Format",
            desc = "Either show default, formatted, or unformatted numbers.\n§eDefault: §72,240/2.2k\n" +
                "§eFormatted: §72.2k/2.2k\n§eUnformatted: §72,240/2,200",
        )
        @ConfigEditorDropdown
        @ConfigOrder(70)
        override val xpFormat: Property<NumberFormatEntry> = Property.of(NumberFormatEntry.DEFAULT)

        @Expose
        @ConfigOption(name = "Text Scale", desc = "How large equipped pet text should be.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 2.0f, minStep = 0.05f)
        @ConfigOrder(80)
        override val textScale: Property<Float> = Property.of(1.0f)

        @Expose
        @ConfigOption(
            name = "Text Location",
            desc = "Where the text will be placed, relative to the Visual Elements above.\n" +
                "§eOnly has any effect if one or more Visual Elements are enabled.",
        )
        @ConfigEditorDropdown
        @ConfigOrder(90)
        override val textLocation: Property<TextLocationOption> = Property.of(TextLocationOption.RIGHT)

        @Expose
        @ConfigOption(name = "Center Target", desc = "What equipped pet text should center around.")
        @ConfigEditorDropdown
        @ConfigOrder(100)
        val centerTarget: Property<CenterTarget> = Property.of(CenterTarget.ALL_PET_VISUALS)

        enum class CenterTarget(private val displayName: String) {
            EQUIPPED_PET_VISUALS("Equipped Pet Visuals"),
            ALL_PET_VISUALS("All Pet Visuals"),
            ;

            override fun toString(): String = displayName
        }

        @Expose
        @ConfigOption(name = "Vertical Alignment", desc = "How text elements will align vertically.")
        @ConfigEditorDropdown
        @ConfigOrder(110)
        override val verticalAlign: Property<GuiAlignment.VerticalAlignment> =
            Property.of(GuiAlignment.VerticalAlignment.CENTER)

        @Expose
        @ConfigOption(name = "Horizontal Alignment", desc = "How text elements will align horizontally.")
        @ConfigEditorDropdown
        @ConfigOrder(120)
        override val horizontalAlign: Property<GuiAlignment.HorizontalAlignment> =
            Property.of(GuiAlignment.HorizontalAlignment.LEFT)
    }

    @Expose
    @ConfigOption(name = "Exp-Share Pet Text", desc = "")
    @Accordion
    @ConfigOrder(20)
    val expSharePets: ExpSharePetTextConfig = ExpSharePetTextConfig()

    class ExpSharePetTextConfig : PetTextDisplaySettings {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Render details for pets receiving shared experience.")
        @ConfigEditorBoolean
        @ConfigOrder(10)
        val enabled: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Text Mode", desc = "Attach shared-pet details to their icons or the main text block.")
        @ConfigEditorDropdown
        @ConfigOrder(20)
        val textMode: Property<TextMode> = Property.of(TextMode.BUNDLED_WITH_MAIN)

        enum class TextMode(private val displayName: String) {
            BUNDLED_WITH_MAIN("Bundled"),
            ATTACHED_TO_ICONS("Attached"),
            ;

            override fun toString(): String = displayName
        }

        @Expose
        @ConfigOption(name = "Bundled Location", desc = "Where bundled Exp-Share pet text should be placed around the main pet text.")
        @ConfigEditorDropdown
        @ConfigOrder(30)
        val bundledLocation: Property<BundledTextLocation> = Property.of(BundledTextLocation.BELOW)

        enum class BundledTextLocation(private val displayName: String) {
            ABOVE("Above Main Text"),
            BELOW("Below Main Text"),
            SPLIT("Split Around Main Text"),
            ;

            override fun toString(): String = displayName
        }

        @Expose
        @ConfigOption(name = "Bundled Spacing", desc = "Gap between main-pet and shared-pet text groups.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 20f, minStep = 1f)
        @ConfigOrder(40)
        val bundledSpacing: Property<Int> = Property.of(9)

        @Expose
        @ConfigOption(name = "Text Scale", desc = "Scale applied to shared-pet text.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 2.0f, minStep = 0.05f)
        @ConfigOrder(50)
        override val textScale: Property<Float> = Property.of(1.0f)

        @Expose
        @ConfigOption(
            name = "Enabled Text",
            desc = "Show text relating to your Exp-Share pets.\n§eItems that are gray are dependent on the items in red.",
        )
        @ConfigEditorDraggableList
        @ConfigOrder(60)
        override val enabledTexts: Property<MutableList<TextElement>> =
            Property.of(mutableListOf(TextElement.PET_NAME, TextElement.NEXT_LEVEL, TextElement.TOTAL_XP))

        @Expose
        @ConfigOption(name = "Text Labels", desc = "Show labels before each text line explaining what data it is.")
        @ConfigEditorBoolean
        @ConfigOrder(70)
        override val textLabels: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Pet Level", desc = "Show pet level in the pet name text.\n§ePet Name must be enabled above.")
        @ConfigEditorBoolean
        @ConfigOrder(80)
        override val nameLevel: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Skin Symbol", desc = "Show a symbol for pet skin in the pet name text.\n§ePet Name must be enabled above.")
        @ConfigEditorBoolean
        @ConfigOrder(90)
        override val nameSkinSymbol: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Next Level %", desc = "Show a percentage after your exp progress.\n§eNext Level must be enabled above.")
        @ConfigEditorBoolean
        @ConfigOrder(100)
        override val nextLevelPercent: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(
            name = "XP Format",
            desc = "Either show default, formatted, or unformatted numbers.\n§eDefault: §72,240/2.2k\n" +
                "§eFormatted: §72.2k/2.2k\n§eUnformatted: §72,240/2,200",
        )
        @ConfigEditorDropdown
        @ConfigOrder(110)
        override val xpFormat: Property<NumberFormatEntry> = Property.of(NumberFormatEntry.DEFAULT)

        @Expose
        @ConfigOption(name = "Text Location", desc = "Where attached text will be placed, relative to each Exp-Share pet icon.")
        @ConfigEditorDropdown
        @ConfigOrder(120)
        override val textLocation: Property<TextLocationOption> = Property.of(TextLocationOption.RIGHT)

        @Expose
        @ConfigOption(name = "Vertical Alignment", desc = "How text elements will align vertically.")
        @ConfigEditorDropdown
        @ConfigOrder(130)
        override val verticalAlign: Property<GuiAlignment.VerticalAlignment> =
            Property.of(GuiAlignment.VerticalAlignment.CENTER)

        @Expose
        @ConfigOption(name = "Horizontal Alignment", desc = "How text elements will align horizontally.")
        @ConfigEditorDropdown
        @ConfigOrder(140)
        override val horizontalAlign: Property<GuiAlignment.HorizontalAlignment> =
            Property.of(GuiAlignment.HorizontalAlignment.LEFT)
    }
}
