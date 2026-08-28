package com.skysoft.features.event.diana

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.skysoft.config.SkysoftConfigFiles
import java.nio.file.Files
import java.nio.file.Path

internal class MythologicalRitualTrackerStore(
    private val path: Path,
    private val fileIo: MythologicalRitualTrackerFileIo = SkysoftMythologicalRitualTrackerFileIo,
) {
    private val gson: Gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()

    fun load(): MythologicalRitualTrackerData {
        if (!fileIo.hasFileOrBackup(path)) return MythologicalRitualTrackerData()
        return fileIo.readWithBackup(path) { source ->
            val data = gson.fromJson(Files.readString(source), MythologicalRitualTrackerData::class.java)
                ?: error("Mythological Ritual tracker storage is empty: $source")
            data.repairLoadedValues()
            data
        }
    }

    fun save(data: MythologicalRitualTrackerData) {
        saveSerialized(serialize(data))
    }

    fun serialize(data: MythologicalRitualTrackerData): String {
        data.repairLoadedValues()
        return gson.toJson(data)
    }

    fun saveSerialized(json: String) {
        fileIo.writeStringSafely(path, json)
    }
}

internal interface MythologicalRitualTrackerFileIo {
    fun hasFileOrBackup(path: Path): Boolean
    fun writeStringSafely(path: Path, text: String)
    fun <T> readWithBackup(path: Path, reader: (Path) -> T): T
}

private object SkysoftMythologicalRitualTrackerFileIo : MythologicalRitualTrackerFileIo {
    override fun hasFileOrBackup(path: Path): Boolean =
        SkysoftConfigFiles.hasFileOrBackup(path)

    override fun writeStringSafely(path: Path, text: String) {
        SkysoftConfigFiles.writeStringSafely(path, text)
    }

    override fun <T> readWithBackup(path: Path, reader: (Path) -> T): T =
        SkysoftConfigFiles.readWithBackup(path, reader)
}
