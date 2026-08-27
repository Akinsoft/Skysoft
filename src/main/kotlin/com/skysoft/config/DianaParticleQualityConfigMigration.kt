package com.skysoft.config

import com.google.gson.JsonObject

internal fun migrateDianaParticleQualitySetup(json: JsonObject, migrationVersion: Int) {
    if (migrationVersion >= PARTICLE_QUALITY_SETUP_VERSION) return
    val events = json.get("events")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
    val diana = events.get("diana")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
    val burrowHelper = diana.get("burrowHelper")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
    val enabled = burrowHelper.get("enabled")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean == true
    if (!enabled) return
    val particleQuality = diana.get("particleQuality")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?: JsonObject().also { diana.add("particleQuality", it) }
    particleQuality.addProperty(
        "automaticMigrationAttemptsRemaining",
        DianaParticleQualityConfig.MAX_AUTOMATIC_MIGRATION_ATTEMPTS,
    )
}

private const val PARTICLE_QUALITY_SETUP_VERSION = 20
