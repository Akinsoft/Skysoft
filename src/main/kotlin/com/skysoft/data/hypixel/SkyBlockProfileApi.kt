package com.skysoft.data.hypixel

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.client.Minecraft
import java.util.Locale

object SkyBlockProfileApi {
    private val profileChangeListeners = ActiveListenerRegistry<(String?) -> Unit>()
    private val consumers = ActiveConsumerRegistry()

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
        SkysoftClientEvents.onEndTick(
            "SkyBlock Profile update",
            isActive = { consumers.isActiveOrDeactivating },
        ) {
            when (consumers.activity()) {
                ConsumerActivity.INACTIVE -> return@onEndTick
                ConsumerActivity.DEACTIVATED -> {
                    setProfile(null)
                    return@onEndTick
                }
                ConsumerActivity.ACTIVATED,
                ConsumerActivity.ACTIVE,
                -> Unit
            }
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
        for (component in TabListApi.skyBlockLines) {
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
        profileChangeListeners.forEachActive { listener -> listener(normalized) }
    }

    private fun String.normalizeProfileName(): String =
        trim().lowercase(Locale.US)

    private val profileTabPattern = Regex("""Profile: ([\w\s]+)(?:[ ♲Ⓑ☀]+)?""")
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
