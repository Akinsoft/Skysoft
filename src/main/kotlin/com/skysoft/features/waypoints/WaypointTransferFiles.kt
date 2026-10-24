package com.skysoft.features.waypoints

import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.utils.FileDialogFilter
import com.skysoft.utils.MinecraftFileDialogs
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.util.Util

internal object WaypointTransferFiles {
    private val importFilter = FileDialogFilter("Waypoint JSON or share text", listOf("json", "txt"))
    private val exportFilter = FileDialogFilter("Waypoint JSON", listOf("json"))

    fun read(): CompletableFuture<String?> =
        MinecraftFileDialogs.openFile("Import Waypoints", importFilter).thenApplyAsync({ path ->
            path?.let { WaypointImportCodec.utf8(WaypointImportCodec.bounded(Files.newInputStream(it))) }
        }, Util.ioPool())

    fun save(json: () -> String): CompletableFuture<Path?> =
        CompletableFuture.supplyAsync({
            json().also {
                require(it.toByteArray(Charsets.UTF_8).size <= WaypointJson.MAX_JSON_BYTES) { "Waypoint export exceeds 8 MB." }
            }
        }, Util.ioPool()).thenComposeAsync({ content ->
            MinecraftFileDialogs.saveFile("Export Waypoints", "Skysoft-waypoints.json", exportFilter)
                .thenApplyAsync({ path -> path?.also { SkysoftConfigFiles.writeStringSafely(it, content) } }, Util.ioPool())
        }, Minecraft.getInstance())
}
