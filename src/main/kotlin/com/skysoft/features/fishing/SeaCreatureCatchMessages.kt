package com.skysoft.features.fishing

import com.google.gson.Gson
import com.skysoft.config.DoubleHookMessagePosition
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.ChromaTextRendering
import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object SeaCreatureCatchMessages {
    private val config get() = SkysoftConfigGui.config().fishing.catchMessages
    private val creaturesByMessage = loadCreatures()
    private var doubleHookPending = false
    private var doubleHookAt = ElapsedTimeMark.farPast()

    fun register() {
        ChatEvents.onVisibleMessage(
            "Sea Creature Double Hook",
            isActive = { config.enabled && HypixelLocationState.inSkyBlock },
        ) { message ->
            if (message.isSystemLike && DOUBLE_HOOK_PATTERN.matches(message.cleanText.trim())) {
                doubleHookPending = true
                doubleHookAt = ElapsedTimeMark.now()
                ChatMessageVisibility.HIDE
            } else {
                ChatMessageVisibility.SHOW
            }
        }
        ChatEvents.onVisibleGameMessageModify(
            "Sea Creature catch messages",
            isActive = { config.enabled && HypixelLocationState.inSkyBlock },
            modifier = ::replaceCatchMessage,
        )
        SkysoftClientEvents.onDisconnect("Sea Creature catch message reset", ::clear)
    }

    private fun replaceCatchMessage(message: ChatMessage): Component {
        if (!message.isSystemLike) return message.component
        val creature = creaturesByMessage[message.cleanText.trim()] ?: return message.component
        val catchMessage = catchMessage(creature)
        val doubleHook = doubleHookPending && doubleHookAt.passedSince() <= DOUBLE_HOOK_TIMEOUT
        clear()
        if (!doubleHook) return catchMessage

        val hook = styledText(
            "DOUBLE HOOK!",
            config.details.doubleHookColor.get(),
            config.details.doubleHookBold,
        )
        return when (config.settings.doubleHookPosition) {
            DoubleHookMessagePosition.BEFORE -> Component.empty().append(hook).append(" ").append(catchMessage)
            DoubleHookMessagePosition.AFTER -> catchMessage.append(" ").append(hook)
        }
    }

    private fun catchMessage(creature: SeaCreatureCatch): MutableComponent {
        val details = config.details
        val article = if (creature.name.firstOrNull()?.lowercaseChar() in VOWELS) "an" else "a"
        val lead = Component.literal("You caught $article ").withStyle { it.withBold(details.catchTextBold) }
        val message = SkysoftChat.gradient(
            lead,
            details.catchGradientStart.get().getEffectiveColourRGB(),
            details.catchGradientEnd.get().getEffectiveColourRGB(),
        )
        val color = if (creature.hotspot) details.hotspotColor.get() else details.seaCreatureColor.get()
        val bold = if (creature.hotspot) details.hotspotBold else details.seaCreatureBold
        return message.append(styledText("${creature.name}!", color, bold))
    }

    private fun styledText(text: String, color: ChromaColour, bold: Boolean): MutableComponent =
        Component.literal(text).withStyle { style -> ChromaTextRendering.apply(style.withBold(bold), color) }

    private fun clear() {
        doubleHookPending = false
        doubleHookAt = ElapsedTimeMark.farPast()
    }

    private fun loadCreatures(): Map<String, SeaCreatureCatch> {
        val catalog = requireNotNull(javaClass.getResourceAsStream(CATCHES_RESOURCE)) {
            "Missing Sea Creature catch data"
        }.bufferedReader().use { reader -> Gson().fromJson(reader, SeaCreatureCatchCatalog::class.java) }
        require(catalog.schemaVersion == CATCHES_SCHEMA_VERSION) { "Unsupported Sea Creature catch data" }
        require(catalog.creatures.map(SeaCreatureCatch::message).distinct().size == catalog.creatures.size) {
            "Sea Creature catch data contains duplicate messages"
        }
        return catalog.creatures.associateBy(SeaCreatureCatch::message)
    }

    private val DOUBLE_HOOK_PATTERN = Regex("^It's a Double Hook!(?: Woot woot!)?$")
    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    private val DOUBLE_HOOK_TIMEOUT = 10.seconds
    private const val CATCHES_RESOURCE = "/assets/skysoft/data/sea_creature_catches.json"
    private const val CATCHES_SCHEMA_VERSION = 1
}

private data class SeaCreatureCatchCatalog(
    val schemaVersion: Int,
    val creatures: List<SeaCreatureCatch>,
)

private data class SeaCreatureCatch(
    val name: String,
    val message: String,
    val hotspot: Boolean = false,
)
