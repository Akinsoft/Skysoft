package com.skysoft.config

import com.skysoft.features.misc.update.DownloadOpenResult
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.misc.update.UpdateState
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
            ModUpdateChecker.status.state == UpdateState.AVAILABLE &&
            ModUpdateChecker.openDownload() == DownloadOpenResult.OPENED
        ) return
        ModUpdateChecker.check(force = true)
    }

    private fun statusColor(): Int =
        when (ModUpdateChecker.status.state) {
            UpdateState.NOT_CHECKED -> 0xFFBDEFFF.toInt()
            UpdateState.CHECKING -> 0xFFFFFF55.toInt()
            UpdateState.CURRENT -> 0xFF55FF55.toInt()
            UpdateState.AVAILABLE -> 0xFFFFAA00.toInt()
            UpdateState.FAILED -> 0xFFFF5555.toInt()
        }
}
