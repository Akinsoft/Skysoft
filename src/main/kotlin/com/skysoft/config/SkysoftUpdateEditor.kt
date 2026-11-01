package com.skysoft.config

import com.skysoft.features.misc.update.DownloadOpenResult
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.misc.update.UpdateStatus
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

class SkysoftUpdateEditor(option: ProcessedOption) :
    SkysoftStatusButtonEditor(option, "update download version modrinth") {
    override fun statusText(): String = ModUpdateChecker.statusText()

    override fun buttonText(): String = ModUpdateChecker.buttonText()

    override fun click() {
        if (
            ModUpdateChecker.status is UpdateStatus.Available &&
            ModUpdateChecker.openDownload() == DownloadOpenResult.OPENED
        ) return
        ModUpdateChecker.check(force = true)
    }

    override fun statusColor(): Int =
        when (ModUpdateChecker.status) {
            UpdateStatus.NotChecked -> 0xFFBDEFFF.toInt()
            UpdateStatus.Checking -> 0xFFFFFF55.toInt()
            UpdateStatus.Current -> 0xFF55FF55.toInt()
            is UpdateStatus.Available -> 0xFFFFAA00.toInt()
            UpdateStatus.Failed -> 0xFFFF5555.toInt()
        }
}
