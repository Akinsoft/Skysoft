package com.skysoft.config

import com.skysoft.features.event.diana.DianaParticleQuality
import com.skysoft.features.event.diana.DianaParticleQualityStatus
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigEditorParticleQuality

class DianaParticleQualityEditor(option: ProcessedOption) : ComponentEditor(option) {
    private val component = wrapComponent(
        SkysoftStatusButtonComponent(
            statusText = ::statusText,
            statusColor = ::statusColor,
            buttonText = { "Do it" },
            onClick = DianaParticleQuality::setExtreme,
        ),
    )

    override fun getDelegate(): GuiComponent = component

    override fun fulfillsSearch(word: String): Boolean =
        super.fulfillsSearch(word) || word in "particle quality pq extreme burrow"

    private fun statusText(): String =
        when (DianaParticleQuality.status()) {
            DianaParticleQualityStatus.UNKNOWN -> "Unknown"
            DianaParticleQualityStatus.CHECKING -> "Checking..."
            DianaParticleQualityStatus.GOOD_TO_GO -> "Good to go"
            DianaParticleQualityStatus.NOT_SET -> "Not set"
        }

    private fun statusColor(): Int =
        when (DianaParticleQuality.status()) {
            DianaParticleQualityStatus.UNKNOWN,
            DianaParticleQualityStatus.CHECKING,
            -> 0xFFFFFF55.toInt()

            DianaParticleQualityStatus.GOOD_TO_GO -> 0xFF55FF55.toInt()
            DianaParticleQualityStatus.NOT_SET -> 0xFFFF5555.toInt()
        }
}
