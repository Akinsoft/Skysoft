package com.skysoft.config

import com.skysoft.features.misc.update.DownloadOpenResult
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.misc.update.UpdateStatus
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

class SkysoftUpdateEditor(option: ProcessedOption) : ComponentEditor(option) {
    private val component = wrapComponent(
        SkysoftStatusButtonComponent(
            statusText = { ModUpdateChecker.statusText() },
            statusColor = ::statusColor,
            buttonText = { ModUpdateChecker.buttonText() },
            onClick = ::click,
        ),
    )

    override fun getDelegate(): GuiComponent = component

    override fun fulfillsSearch(word: String): Boolean =
        super.fulfillsSearch(word) || word in "update download version modrinth"

    private fun click() {
        if (
            ModUpdateChecker.status is UpdateStatus.Available &&
            ModUpdateChecker.openDownload() == DownloadOpenResult.OPENED
        ) return
        ModUpdateChecker.check(force = true)
    }

    private fun statusColor(): Int =
        when (ModUpdateChecker.status) {
            UpdateStatus.NotChecked -> 0xFFBDEFFF.toInt()
            UpdateStatus.Checking -> 0xFFFFFF55.toInt()
            UpdateStatus.Current -> 0xFF55FF55.toInt()
            is UpdateStatus.Available -> 0xFFFFAA00.toInt()
            UpdateStatus.Failed -> 0xFFFF5555.toInt()
        }
}
