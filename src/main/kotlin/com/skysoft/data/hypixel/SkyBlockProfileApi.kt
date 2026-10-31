package com.skysoft.data.hypixel

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import java.util.Locale

object SkyBlockProfileApi {
    private val profileChangeListeners = ActiveListenerRegistry<(String?) -> Unit>()
    private val consumers = ActiveConsumerRegistry()

    var currentProfileKey: String? = null
        private set

    val currentProfileId: SkyBlockProfileId?
        get() {
            val playerKey = currentPlayerKeyOrNull() ?: return null
            val profileKey = currentProfileKey ?: return null
            return SkyBlockProfileId(playerKey, profileKey)
        }

    fun currentPlayerKeyOrNull(): String? {
        val minecraft: Minecraft? = Minecraft.getInstance()
        return minecraft?.player?.uuid?.toString()
    }

    fun register() {
        TabListApi.onChange(
            "SkyBlock Profile API",
            isActive = { consumers.hasActiveConsumers },
            listener = ::readTabProfile,
        )
        ChatEvents.onVisibleMessage(
            "SkyBlock Profile chat",
            isActive = { consumers.hasActiveConsumers },
        ) { message ->
            handleChat(message.plainText)
            ChatMessageVisibility.SHOW
        }
        consumers.onEndTick("SkyBlock Profile update", onDeactivated = { setProfile(null) }) {
            if (!HypixelLocationState.inSkyBlock) setProfile(null)
        }
        SkysoftClientEvents.onDisconnect("SkyBlock Profile reset") {
            setProfile(null)
            consumers.resetActivity()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    fun onProfileChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (String?) -> Unit,
    ) {
        profileChangeListeners.register(boundary, isActive, listener)
    }

    private fun handleChat(message: String) {
        val clean = message.cleanSkyBlockText().lowercase(Locale.US)
        val prefix = profileMessagePrefixes.firstOrNull(clean::startsWith) ?: return
        setProfile(clean.removePrefix(prefix).removeSuffix("(co-op)").trim())
    }

    private fun readTabProfile() {
        TabListApi.skyBlockLines
            .firstNotNullOfOrNull { profileTabPattern.matchEntire(it.cleanSkyBlockText())?.groupValues?.get(1) }
            ?.let(::setProfile)
    }

    private fun setProfile(profileName: String?) {
        val normalized = profileName?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
        if (currentProfileKey == normalized) return
        currentProfileKey = normalized
        profileChangeListeners.forEachActive { listener -> listener(normalized) }
    }

    private val profileMessagePrefixes = listOf("your profile was changed to:", "you are playing on profile:")
    private val profileTabPattern = Regex("""Profile: ([\w\s]+)(?:[ ♲Ⓑ☀]+)?""")
}

data class SkyBlockProfileId(
    val playerKey: String,
    val profileKey: String,
)

internal data class SkyBlockSessionContext(
    val locationVersion: Long,
    val playerId: String,
    val profileKey: String?,
    val level: Any?,
) {
    companion object {
        fun of(minecraft: Minecraft, player: Player) = SkyBlockSessionContext(
            HypixelLocationState.locationVersion,
            player.uuid.toString(),
            SkyBlockProfileApi.currentProfileKey,
            minecraft.level,
        )
    }
}
