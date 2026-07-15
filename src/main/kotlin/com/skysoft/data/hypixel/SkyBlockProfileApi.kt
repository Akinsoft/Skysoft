package com.skysoft.data.hypixel

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import java.util.Locale

object SkyBlockProfileApi {
    private val profileChangeListeners = mutableListOf<(String?) -> Unit>()
    private var ticks = 0

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
        ChatEvents.onVisibleMessage { message ->
            handleChat(message.plainText)
            ChatMessageVisibility.SHOW
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!HypixelLocationState.inSkyBlock) {
                setProfile(null)
                ticks = 0
                return@register
            }
            if (++ticks % TAB_PROFILE_READ_INTERVAL_TICKS == 0) readTabProfile()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            setProfile(null)
            ticks = 0
        }
    }

    fun onProfileChange(listener: (String?) -> Unit) {
        profileChangeListeners += listener
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
        profileChangeListeners.forEach { it(normalized) }
    }

    private fun String.normalizeProfileName(): String =
        trim().lowercase(Locale.US)

    private val profileTabPattern = Regex("""Profile: ([\w\s]+)(?:[ ♲Ⓑ☀]+)?""")
    private const val TAB_PROFILE_READ_INTERVAL_TICKS = 20
}

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
