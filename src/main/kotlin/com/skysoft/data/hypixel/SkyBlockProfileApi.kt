package com.skysoft.data.hypixel

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.client.Minecraft
import java.util.Locale

object SkyBlockProfileApi {
    private val profileChangeListeners = mutableListOf<ProfileChangeListener>()
    private val consumers = ActiveConsumerRegistry()
    private var ticks = 0
    private var wasActive = false

    var currentProfileName: String? = null
        private set

    val currentProfileKey: String?
        get() = currentProfileName?.normalizeProfileName()

    val currentProfileId: SkyBlockProfileId?
        get() = SkyBlockProfileId.fromExactKeys(currentPlayerKeyOrNull(), currentProfileKey)

    fun currentPlayerKeyOrNull(): String? {
        val minecraft: Minecraft? = Minecraft.getInstance()
        return minecraft?.player?.uuid?.toString()
    }

    fun register() {
        TabListApi.registerConsumer("SkyBlock Profile API") { consumers.hasActiveConsumers }
        ChatEvents.onVisibleMessage(
            "SkyBlock Profile chat",
            isActive = { consumers.hasActiveConsumers },
        ) { message ->
            handleChat(message.plainText)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "SkyBlock Profile update",
            isActive = { consumers.hasActiveConsumers || wasActive },
        ) {
            val isActive = consumers.hasActiveConsumers
            if (!isActive) {
                if (wasActive) setProfile(null)
                wasActive = false
                ticks = 0
                return@onEndTick
            }
            wasActive = true
            if (!HypixelLocationState.inSkyBlock) {
                setProfile(null)
                ticks = 0
                return@onEndTick
            }
            if (++ticks % TAB_PROFILE_READ_INTERVAL_TICKS == 0) readTabProfile()
        }
        SkysoftClientEvents.onDisconnect("SkyBlock Profile reset") {
            setProfile(null)
            ticks = 0
            wasActive = false
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    internal val hasActiveConsumers: Boolean
        get() = consumers.hasActiveConsumers

    fun onProfileChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (String?) -> Unit,
    ) {
        profileChangeListeners += ProfileChangeListener(boundary, isActive, listener)
    }

    private fun handleChat(message: String) {
        val clean = message.cleanSkyBlockText().lowercase(Locale.US)
        val profile = when {
            clean.startsWith("your profile was changed to:") ->
                clean.removePrefix("your profile was changed to:")

            clean.startsWith("you are playing on profile:") ->
                clean.removePrefix("you are playing on profile:")

            else -> return
        }
        setProfile(profile.removeSuffix("(co-op)").trim())
    }

    private fun readTabProfile() {
        for (component in TabListApi.lines) {
            val line = component.cleanSkyBlockText()
            val profile = profileTabPattern.matchEntire(line)?.groupValues?.get(1) ?: continue
            setProfile(profile)
            return
        }
    }

    private fun setProfile(profileName: String?) {
        val normalized = profileName?.normalizeProfileName()?.takeIf { it.isNotBlank() }
        if (currentProfileName == normalized) return
        currentProfileName = normalized
        profileChangeListeners.forEach { listener ->
            if (listener.isActive()) {
                SkysoftErrorBoundary.run(listener.boundary) { listener.callback(normalized) }
            }
        }
    }

    private fun String.normalizeProfileName(): String =
        trim().lowercase(Locale.US)

    private val profileTabPattern = Regex("""Profile: ([\w\s]+)(?:[ ♲Ⓑ☀]+)?""")
    private const val TAB_PROFILE_READ_INTERVAL_TICKS = 20
}

private data class ProfileChangeListener(
    val boundary: String,
    val isActive: () -> Boolean,
    val callback: (String?) -> Unit,
)

data class SkyBlockProfileId(
    val playerKey: String,
    val profileKey: String,
) {
    companion object {
        internal fun fromExactKeys(playerKey: String?, profileKey: String?): SkyBlockProfileId? {
            if (playerKey == null || profileKey == null) return null
            return SkyBlockProfileId(playerKey, profileKey)
        }
    }
}
