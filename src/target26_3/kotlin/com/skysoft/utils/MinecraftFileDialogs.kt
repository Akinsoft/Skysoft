package com.skysoft.utils

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import org.lwjgl.sdl.SDLDialog
import org.lwjgl.sdl.SDLError
import org.lwjgl.sdl.SDLProperties
import org.lwjgl.sdl.SDL_DialogFileCallback
import org.lwjgl.sdl.SDL_DialogFileFilter
import org.lwjgl.system.MemoryUtil

object MinecraftFileDialogs {
    fun openFile(title: String, filter: FileDialogFilter): CompletableFuture<Path?> =
        show(SDLDialog.SDL_FILEDIALOG_OPENFILE, title, null, filter)

    fun saveFile(title: String, defaultPath: String, filter: FileDialogFilter): CompletableFuture<Path?> =
        show(SDLDialog.SDL_FILEDIALOG_SAVEFILE, title, defaultPath, filter)

    private fun show(type: Int, title: String, location: String?, filter: FileDialogFilter): CompletableFuture<Path?> {
        val selection = CompletableFuture<String?>()
        val name = MemoryUtil.memUTF8(filter.description)
        val pattern = MemoryUtil.memUTF8(filter.extensions.joinToString(";"))
        val filters = SDL_DialogFileFilter.calloc(1).name(name).pattern(pattern)
        val properties = SDLProperties.SDL_CreateProperties()
        lateinit var callback: SDL_DialogFileCallback
        callback = SDL_DialogFileCallback.create { _, fileList, _ ->
            if (fileList == MemoryUtil.NULL) {
                selection.completeExceptionally(IllegalStateException(SDLError.SDL_GetError()))
            } else {
                val firstFile = MemoryUtil.memGetAddress(fileList)
                selection.complete(if (firstFile == MemoryUtil.NULL) null else MemoryUtil.memUTF8(firstFile))
            }
            Minecraft.getInstance().execute {
                callback.free()
                filters.free()
                MemoryUtil.memFree(name)
                MemoryUtil.memFree(pattern)
                SDLProperties.SDL_DestroyProperties(properties)
            }
        }
        SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title)
        SDLProperties.SDL_SetPointerProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address())
        SDLProperties.SDL_SetNumberProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1)
        SDLProperties.SDL_SetPointerProperty(
            properties,
            SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,
            Minecraft.getInstance().window.handle(),
        )
        location?.let { SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, it) }
        SDLDialog.SDL_ShowFileDialogWithProperties(type, callback, MemoryUtil.NULL, properties)
        return selection.thenApply { it?.let(Path::of) }
    }
}
