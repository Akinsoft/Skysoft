package com.skysoft.utils

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import net.minecraft.util.Util
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs

object MinecraftFileDialogs {
    fun openFile(title: String, filter: FileDialogFilter): CompletableFuture<Path?> = choose { stack ->
        TinyFileDialogs.tinyfd_openFileDialog(title, "", filter.patterns(stack), filter.description, false)
    }

    fun saveFile(title: String, defaultPath: String, filter: FileDialogFilter): CompletableFuture<Path?> = choose { stack ->
        TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, filter.patterns(stack), filter.description)
    }

    private fun choose(dialog: (MemoryStack) -> String?): CompletableFuture<Path?> =
        CompletableFuture.supplyAsync({ MemoryStack.stackPush().use(dialog)?.let(Path::of) }, Util.ioPool())

    private fun FileDialogFilter.patterns(stack: MemoryStack): PointerBuffer {
        val patterns = stack.mallocPointer(extensions.size)
        extensions.forEach { extension -> patterns.put(stack.UTF8("*.$extension")) }
        return patterns.flip()
    }
}
