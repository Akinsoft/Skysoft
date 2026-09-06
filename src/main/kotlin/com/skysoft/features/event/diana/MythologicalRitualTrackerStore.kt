package com.skysoft.features.event.diana

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.skysoft.config.SkysoftConfigFiles
import java.nio.file.Files
import java.nio.file.Path

internal class MythologicalRitualTrackerStore(
    private val path: Path,
) {
    private val gson: Gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()

    fun load(): MythologicalRitualTrackerData {
        if (!SkysoftConfigFiles.hasFileOrBackup(path)) return MythologicalRitualTrackerData()
        return SkysoftConfigFiles.readWithBackup(path) { source ->
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
        SkysoftConfigFiles.writeStringSafely(path, json)
    }
}
