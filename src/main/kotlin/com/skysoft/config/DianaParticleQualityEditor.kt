package com.skysoft.config

import com.skysoft.features.event.diana.DianaParticleQuality
import com.skysoft.features.event.diana.DianaParticleQualityStatus
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigEditorParticleQuality

class DianaParticleQualityEditor(option: ProcessedOption) :
    SkysoftStatusButtonEditor(option, "particle quality pq extreme burrow") {
    override fun buttonText(): String = "Do it"

    override fun click() {
        DianaParticleQuality.setExtreme()
    }

    override fun statusText(): String =
        when (DianaParticleQuality.status()) {
            DianaParticleQualityStatus.UNKNOWN -> "Unknown"
            DianaParticleQualityStatus.CHECKING -> "Checking..."
            DianaParticleQualityStatus.GOOD_TO_GO -> "Good to go"
            DianaParticleQualityStatus.NOT_SET -> "Not set"
        }

    override fun statusColor(): Int =
        when (DianaParticleQuality.status()) {
            DianaParticleQualityStatus.UNKNOWN,
            DianaParticleQualityStatus.CHECKING,
            -> 0xFFFFFF55.toInt()

            DianaParticleQualityStatus.GOOD_TO_GO -> 0xFF55FF55.toInt()
            DianaParticleQualityStatus.NOT_SET -> 0xFFFF5555.toInt()
        }
}
