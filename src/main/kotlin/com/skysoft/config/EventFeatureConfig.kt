package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
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

class EventFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Diana", desc = "Diana Event Features")
    val diana = SkysoftDianaConfig()

    override fun repairLoadedValues() {
        diana.repairLoadedValues()
    }
}

class SkysoftDianaConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Burrow Helper", desc = "Find and display Diana burrows.")
    @field:Accordion
    val burrowHelper = DianaBurrowHelperConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rare Mob Sharing", desc = "Share and display selected rare mobs.")
    @field:Accordion
    val rareMobSharing = DianaRareMobSharingConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lootshare", desc = "Configure lootshare messages and indicators.")
    @field:Accordion
    val lootshare = DianaLootshareConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Party Commands", desc = "Respond to selected Diana commands in party chat.")
    @field:Accordion
    val partyCommands = DianaPartyCommandsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lobby Compromised", desc = "Alert when too many non-party players join the lobby.")
    @field:Accordion
    val lobbyCompromised = DianaLobbyCompromisedConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Sphinx Helper", desc = "Highlight correct Sphinx answers.")
    @field:Accordion
    val sphinxHelper = DianaSphinxHelperConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Quick Warps", desc = "Suggest quick warps to get to burrows faster.")
    @field:Accordion
    val quickWarps = DianaQuickWarpsConfig()

    fun isAnyFeatureEnabled(): Boolean =
        burrowHelper.enabled ||
            rareMobSharing.enabled ||
            (partyCommands.enabled && partyCommands.settings.commands.get().isNotEmpty()) ||
            lobbyCompromised.enabled ||
            sphinxHelper.enabled ||
            quickWarps.enabled

    fun repairLoadedValues() {
        lobbyCompromised.settings.repairLoadedValues()
    }
}

class DianaBurrowHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Find and display Diana burrows.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Burrow Helper settings.")
    @field:Accordion
    val settings = DianaBurrowSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Burrow Helper appearance.")
    @field:Accordion
    val details = DianaBurrowDetailsConfig()
}

class DianaBurrowSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to the next burrow or shared rare mob.")
    @field:ConfigEditorBoolean
    var crosshairLine = true

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
}

class DianaBurrowDetailsConfig {
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
    @field:ConfigOption(name = "Hide Guess Arrows", desc = "Hide burrow arrow particles.")
    @field:ConfigEditorBoolean
    var hideGuessArrows = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Burrow Box Color", desc = "Match burrow text colors or choose one custom box color.")
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
    @field:ConfigOption(name = "Start Text Color", desc = "Color used for Start burrow text.")
    @field:ConfigEditorColour
    val startTextColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mob Text Color", desc = "Color used for Mob burrow text.")
    @field:ConfigEditorColour
    val mobTextColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Treasure Text Color", desc = "Color used for Treasure burrow text.")
    @field:ConfigEditorColour
    val treasureTextColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 170, 0, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Guess Text Color", desc = "Color used for Guess burrow text.")
    @field:ConfigEditorColour
    val guessTextColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}

class DianaRareMobSharingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Share and display selected rare mobs.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Rare Mob Sharing settings.")
    @field:Accordion
    val settings = DianaRareMobSharingSettingsConfig()
}

class DianaRareMobSharingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Share Mobs", desc = "Share selected rare mobs in party chat.")
    @field:ConfigEditorBoolean
    var shareMobs = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Shared Mobs", desc = "Rare mobs Skysoft should share.")
    @field:ConfigVisibleIf("shareMobs")
    @field:ConfigEditorDraggableList
    val sharedRareMobs: Property<MutableList<DianaRareMobOption>> = Property.of(defaultDianaRareMobs())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Received Mobs", desc = "Rare mob pings Skysoft should show.")
    @field:ConfigEditorDraggableList
    val receivedRareMobs: Property<MutableList<DianaRareMobOption>> = Property.of(defaultDianaRareMobs())
}

class DianaLootshareConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Lootshare settings.")
    @field:Accordion
    val settings = DianaLootshareSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Lootshare appearance.")
    @field:Accordion
    val details = DianaLootshareDetailsConfig()
}

class DianaLootshareSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Share Secured Message",
        desc = "Send a party chat message after dealing enough damage to secure lootshare.",
    )
    @field:ConfigEditorBoolean
    var shareSecuredMessage = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Party Checkmarks", desc = "Show a checkmark above party members who have secured lootshare.")
    @field:ConfigEditorBoolean
    var partyCheckmarks = true
}

class DianaLootshareDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lootshare Radius", desc = "Draw a 30 block lootshare radius.")
    @field:ConfigEditorBoolean
    var lootshareRadius = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Lootshare Missing",
        desc = "Color of the Lootshare text above the mob's head when you haven't dealt enough damage to secure lootshare yet.",
    )
    @field:ConfigEditorColour
    val lootshareMissingColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 85, 0, 230))

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Lootshare Ready",
        desc = "Color of the Lootshare text above the mob's head when you've dealt enough damage to secure lootshare.",
    )
    @field:ConfigEditorColour
    val lootshareReadyColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 255, 0, 230))
}

class DianaPartyCommandsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Respond to selected Diana commands in party chat.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Party Commands settings.")
    @field:Accordion
    val settings = DianaPartyCommandsSettingsConfig()
}

class DianaPartyCommandsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Commands", desc = "Diana commands Skysoft should answer in party chat.")
    @field:ConfigEditorDraggableList
    val commands: Property<MutableList<DianaPartyCommand>> = Property.of(DianaPartyCommand.entries.toMutableList())
}

class DianaLobbyCompromisedConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Alert when too many non-party players join the lobby.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Lobby Compromised settings.")
    @field:Accordion
    val settings = DianaLobbyCompromisedSettingsConfig()
}

class DianaLobbyCompromisedSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Stranger Limit", desc = "Non-party players before alerting.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 6f, minStep = 1f)
    var strangerLimit = DEFAULT_LOBBY_COMPROMISED_STRANGER_LIMIT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lobby Alerts", desc = "Lobby compromised alerts to show.")
    @field:ConfigEditorDraggableList
    val alerts: Property<MutableList<DianaLobbyCompromisedAlert>> =
        Property.of(mutableListOf(DianaLobbyCompromisedAlert.TITLE_ALERT))

    fun repairLoadedValues() {
        strangerLimit = strangerLimit.coerceIn(
            MIN_LOBBY_COMPROMISED_STRANGER_LIMIT,
            MAX_LOBBY_COMPROMISED_STRANGER_LIMIT,
        )
    }
}

class DianaSphinxHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight correct Sphinx answers.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false
}

class DianaQuickWarpsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Suggest quick warps to get to burrows faster.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Quick Warps settings.")
    @field:Accordion
    val settings = DianaQuickWarpsSettingsConfig()
}

class DianaQuickWarpsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Warp Key", desc = "Press this key to use the suggested warp.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var warpKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Minimum Savings", desc = "Minimum blocks saved before suggesting a warp.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 80f, minStep = 1f)
    var minWarpSavings = 10
}

enum class DianaPartyCommand(private val command: String) {
    CHIMERA("!chim"),
    INQUISITOR_LOOTSHARE("!inqsls"),
    INQUISITOR("!inq"),
    KING_MINOS("!king"),
    KING_MINOS_LOOTSHARE("!kingls"),
    BURROWS("!burrows"),
    RELICS("!relic"),
    CHIMERA_LOOTSHARE("!chimls"),
    MANTI_CORE("!core"),
    MANTI_CORE_LOOTSHARE("!corels"),
    FATEFUL_STINGER("!stinger"),
    FATEFUL_STINGER_LOOTSHARE("!stingerls"),
    SHIMMERING_WOOL("!wool"),
    SHIMMERING_WOOL_LOOTSHARE("!woolls"),
    BRAIN_FOOD("!food"),
    BRAIN_FOOD_LOOTSHARE("!foodls"),
    KING_SHARDS("!kingshard"),
    SPHINX_SHARDS("!sphinxshard"),
    MINOTAUR_SHARDS("!minotaurshard"),
    CRETAN_SHARDS("!cretanshard"),
    MYTHOS_FRAGMENTS("!mythofrag"),
    CRETAN_URNS("!urns"),
    HILT_OF_REVELATIONS("!hilt"),
    DAEDALUS_STICKS("!sticks"),
    GRIFFIN_FEATHERS("!feathers"),
    COINS("!coins"),
    MOBS("!mobs"),
    MAGIC_FIND("!mf"),
    PLAYTIME("!playtime"),
    PROFITS("!profits"),
    STATS("!stats"),
    TOTAL_STATS("!totalstats"),
    SESSION_STATS("!sessionstats"),
    SINCE("!since"),
    ;

    override fun toString(): String = command
}

enum class DianaClickCounterPosition(private val displayName: String) {
    RIGHT("Right"),
    BELOW("Below"),
    ;

    override fun toString(): String = displayName
}

enum class DianaBurrowBoxColorMode(private val displayName: String) {
    DEFAULT("Match Text"),
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
