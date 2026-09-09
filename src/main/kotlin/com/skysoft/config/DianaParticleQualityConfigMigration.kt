package com.skysoft.config

import com.google.gson.JsonObject

internal fun migrateDianaParticleQualitySetup(json: JsonObject, migrationVersion: Int) {
    if (migrationVersion >= PARTICLE_QUALITY_SETUP_VERSION) return
    val events = json.getObjectOrNull("events") ?: return
    val diana = events.getObjectOrNull("diana") ?: return
    val burrowHelper = diana.getObjectOrNull("burrowHelper") ?: return
    val enabled = burrowHelper.get("enabled")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean == true
    if (!enabled) return
    val particleQuality = diana.getOrCreateObject("particleQuality")
    particleQuality.addProperty(
        "automaticMigrationAttemptsRemaining",
        DianaParticleQualityConfig.MAX_AUTOMATIC_MIGRATION_ATTEMPTS,
    )
}

private const val PARTICLE_QUALITY_SETUP_VERSION = 20
