package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.Minecraft

object DianaParticleQuality {
    private val config get() = SkysoftConfigGui.config().events.diana.particleQuality

    private var checkingUntilMillis = 0L
    private var automaticAttemptedThisConnection = false

    fun register() {
        ClientSendMessageEvents.COMMAND.register { command ->
            SkysoftErrorBoundary.run("Particle quality outgoing command") { handleOutgoingCommand(command) }
        }
        ChatEvents.onVisibleMessage("Particle quality response", { HypixelLocationState.onHypixel }) { message ->
            handleResponse(message.cleanText)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Particle quality setup",
            isActive = {
                checkingUntilMillis != 0L ||
                    (!automaticAttemptedThisConnection && config.automaticMigrationAttemptsRemaining > 0)
            },
        ) { update() }
        SkysoftClientEvents.onDisconnect("Particle quality disconnect reset") {
            checkingUntilMillis = 0L
            automaticAttemptedThisConnection = false
        }
    }

    fun status(): DianaParticleQualityStatus {
        if (System.currentTimeMillis() < checkingUntilMillis) return DianaParticleQualityStatus.CHECKING
        return when (config.maximumParticlesPerTick) {
            null -> DianaParticleQualityStatus.UNKNOWN
            EXTREME_PARTICLES_PER_TICK -> DianaParticleQualityStatus.GOOD_TO_GO
            else -> DianaParticleQualityStatus.NOT_SET
        }
    }

    fun setExtreme() {
        val connection = Minecraft.getInstance().connection
        if (!HypixelLocationState.onHypixel || connection == null) {
            SkysoftChat.error("Join Hypixel before setting particle quality.")
            return
        }
        beginChecking()
        connection.sendCommand(EXTREME_COMMAND)
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (checkingUntilMillis != 0L && now >= checkingUntilMillis) checkingUntilMillis = 0L
        if (
            checkingUntilMillis != 0L ||
            automaticAttemptedThisConnection ||
            config.automaticMigrationAttemptsRemaining <= 0 ||
            !HypixelLocationState.onHypixel
        ) return
        val connection = Minecraft.getInstance().connection ?: return
        automaticAttemptedThisConnection = true
        config.automaticMigrationAttemptsRemaining--
        beginChecking()
        connection.sendCommand(EXTREME_COMMAND)
    }

    private fun handleOutgoingCommand(command: String) {
        if (HypixelLocationState.onHypixel && PARTICLE_QUALITY_COMMAND.matches(command.trim())) beginChecking()
    }

    private fun handleResponse(message: String) {
        val particlesPerTick = PARTICLE_QUALITY_RESPONSE.matchEntire(message.trim())
            ?.groups
            ?.get("count")
            ?.value
            ?.toInt()
            ?: return
        checkingUntilMillis = 0L
        config.maximumParticlesPerTick = particlesPerTick
        if (particlesPerTick == EXTREME_PARTICLES_PER_TICK) {
            config.automaticMigrationAttemptsRemaining = 0
        }
        SkysoftConfigGui.config().saveNow()
    }

    private fun beginChecking() {
        config.maximumParticlesPerTick = null
        checkingUntilMillis = System.currentTimeMillis() + RESPONSE_TIMEOUT_MILLIS
        SkysoftConfigGui.config().saveNow()
    }

    private const val EXTREME_COMMAND = "pq extreme"
    private const val EXTREME_PARTICLES_PER_TICK = 50
    private const val RESPONSE_TIMEOUT_MILLIS = 5_000L
    private val PARTICLE_QUALITY_COMMAND = Regex(
        """^(?:pq|particlequality)\s+(?:low|medium|high|extreme)$""",
        RegexOption.IGNORE_CASE,
    )
    private val PARTICLE_QUALITY_RESPONSE = Regex(
        """^Maximum Particles per Tick now: (?<count>5|15|30|50)$""",
    )
}

enum class DianaParticleQualityStatus {
    UNKNOWN,
    CHECKING,
    GOOD_TO_GO,
    NOT_SET,
}
