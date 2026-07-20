package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.observer.Property
import org.lwjgl.glfw.GLFW

class EventFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Diana", desc = "Diana event helpers.")
    val diana = SkysoftDianaConfig()

    fun repairLoadedValues() {
        diana.repairLoadedValues()
    }
}

class SkysoftDianaConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show Diana burrow helpers.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Burrow and warp settings.")
    @field:Accordion
    val settings = DianaSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Detailed visual settings.")
    @field:Accordion
    val details = DianaDetailsConfig()

    fun repairLoadedValues() {
        settings.repairLoadedValues()
    }
}

class DianaSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to the next burrow.")
    @field:ConfigEditorBoolean
    var crosshairLine = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rare Mob Sharing", desc = "Share selected rare mobs in party chat.")
    @field:ConfigEditorBoolean
    var rareMobSharing = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Shared Mobs", desc = "Rare mobs Skysoft should share.")
    @field:ConfigVisibleIf("rareMobSharing")
    @field:ConfigEditorDraggableList
    val sharedRareMobs: Property<MutableList<DianaRareMobOption>> = Property.of(defaultDianaRareMobs())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Received Mobs", desc = "Rare mob pings Skysoft should show.")
    @field:ConfigEditorDraggableList
    val receivedRareMobs: Property<MutableList<DianaRareMobOption>> = Property.of(defaultDianaRareMobs())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lobby Compromised", desc = "Alert when too many non-party players join.")
    @field:ConfigEditorBoolean
    var lobbyCompromised = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Stranger Limit", desc = "Non-party players before alerting.")
    @field:ConfigVisibleIf("lobbyCompromised")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 6f, minStep = 1f)
    var lobbyCompromisedStrangerLimit = DEFAULT_LOBBY_COMPROMISED_STRANGER_LIMIT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lobby Alerts", desc = "Lobby compromised alerts to show.")
    @field:ConfigVisibleIf("lobbyCompromised")
    @field:ConfigEditorDraggableList
    val lobbyCompromisedAlerts: Property<MutableList<DianaLobbyCompromisedAlert>> =
        Property.of(mutableListOf(DianaLobbyCompromisedAlert.TITLE_ALERT))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lootshare Radius", desc = "Draw a 30 block lootshare radius.")
    @field:ConfigEditorBoolean
    var lootshareRadius = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Sphinx Answers", desc = "Highlight correct Sphinx answers.")
    @field:ConfigEditorBoolean
    var sphinxAnswers = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Progress", desc = "Show burrow click progress.")
    @field:ConfigEditorBoolean
    var clickCounter = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Progress Position", desc = "Where to show burrow click progress.")
    @field:ConfigVisibleIf("clickCounter")
    @field:ConfigEditorDropdown
    var clickCounterPosition = DianaClickCounterPosition.RIGHT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Warp Hint", desc = "Show a useful warp title.")
    @field:ConfigEditorBoolean
    var warpHint = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Warp Key", desc = "Press this key to use the suggested warp.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var warpKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Min Warp Savings", desc = "Minimum blocks saved before suggesting a warp.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 80f, minStep = 1f)
    var minWarpSavings = 10

    fun repairLoadedValues() {
        lobbyCompromisedStrangerLimit = lobbyCompromisedStrangerLimit.coerceIn(
            MIN_LOBBY_COMPROMISED_STRANGER_LIMIT,
            MAX_LOBBY_COMPROMISED_STRANGER_LIMIT,
        )
    }
}

class DianaDetailsConfig {
    val customBurrowBoxColorVisible: Property<Boolean> = Property.wrap(object : GetSetter<Boolean> {
        override fun get(): Boolean = burrowBoxColorMode == DianaBurrowBoxColorMode.CUSTOM

        override fun set(value: Boolean) = Unit
    })

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bold Text", desc = "Use bold burrow labels.")
    @field:ConfigEditorBoolean
    var boldText = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Guess Arrows", desc = "Hide Diana arrow particles.")
    @field:ConfigEditorBoolean
    var hideGuessArrows = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Burrow Box Color", desc = "Choose default type colors or one custom box color.")
    @field:ConfigEditorDropdown
    var burrowBoxColorMode = DianaBurrowBoxColorMode.DEFAULT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Custom Box Color", desc = "Color used for burrow boxes in custom mode.")
    @field:ConfigVisibleIf("customBurrowBoxColorVisible")
    @field:ConfigEditorColour
    val burrowBoxColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(170, 85, 255, 0, 230))

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Burrow Label Format",
        desc = """Examples:
GUESS
guess
Guess""",
    )
    @field:ConfigEditorDropdown
    var labelFormat = WaypointLabelFormat.CAPS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lootshare Missing", desc = "Color before you deal enough damage.")
    @field:ConfigEditorColour
    val lootshareMissingColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 85, 0, 230))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lootshare Ready", desc = "Color after you deal enough damage.")
    @field:ConfigEditorColour
    val lootshareReadyColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 255, 0, 230))
}

enum class DianaClickCounterPosition(private val displayName: String) {
    RIGHT("Right"),
    BELOW("Below"),
    ;

    override fun toString(): String = displayName
}

enum class DianaBurrowBoxColorMode(private val displayName: String) {
    DEFAULT("Default"),
    CUSTOM("Custom"),
    ;

    override fun toString(): String = displayName
}

enum class DianaLobbyCompromisedAlert(private val displayName: String) {
    TITLE_ALERT("Title Alert"),
    CHAT_ALERT("Chat Alert"),
    ;

    override fun toString(): String = displayName
}

enum class DianaRareMobOption(
    private val displayName: String,
    private val article: String = "a",
    private vararg val aliases: String,
) {
    MINOS_HUNTER("Minos Hunter"),
    SIAMESE_LYNXES("Siamese Lynxes", "", "Siamese Lynx", "Bagheera", "Azrael"),
    STRANDED_NYMPH("Stranded Nymph"),
    CRETAN_BULL("Cretan Bull"),
    HARPY("Harpy"),
    GAIA_CONSTRUCT("Gaia Construct"),
    MINOTAUR("Minotaur"),
    MINOS_CHAMPION("Minos Champion"),
    SPHINX("Sphinx"),
    MINOS_INQUISITOR("Minos Inquisitor"),
    MANTICORE("Manticore"),
    KING_MINOS("King Minos"),
    ;

    val label: String get() = displayName
    val matchLabels: Set<String> get() = setOf(displayName) + aliases
    val shareMarker: String get() = "Found ${if (article.isEmpty()) "" else "$article "}$displayName!"

    override fun toString(): String = displayName

    companion object {
        fun fromLabel(label: String): DianaRareMobOption? =
            entries.firstOrNull { option ->
                option.matchLabels.any { it.equals(label, ignoreCase = true) }
            }

        fun fromMobName(name: String): DianaRareMobOption? {
            val cleanName = name.trim()
            return entries.firstOrNull { option ->
                option.matchLabels.any { label ->
                    cleanName.contains(label, ignoreCase = true)
                }
            }
        }
    }
}

private fun defaultDianaRareMobs(): MutableList<DianaRareMobOption> =
    mutableListOf(DianaRareMobOption.MINOS_INQUISITOR, DianaRareMobOption.KING_MINOS)

const val MIN_LOBBY_COMPROMISED_STRANGER_LIMIT = 1
const val MAX_LOBBY_COMPROMISED_STRANGER_LIMIT = 6
const val DEFAULT_LOBBY_COMPROMISED_STRANGER_LIMIT = 3
