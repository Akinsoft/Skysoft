package com.skysoft.features.screenshot

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.EventQueue
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import net.minecraft.util.Util

internal object ScreenshotClipboard {
    private var retainedAwtContents: Transferable? = null

    fun copyAsync(path: Path): CompletableFuture<Void> =
        CompletableFuture.runAsync({ copy(path) }, Util.ioPool())

    private fun copy(path: Path) {
        val image = requireNotNull(ImageIO.read(path.toFile())) { "Unsupported screenshot image: $path" }
        if (Platform.isWindows()) {
            WindowsImageClipboard.copy(image)
        } else {
            copyWithAwt(image)
        }
    }

    private fun copyWithAwt(image: BufferedImage) {
        val contents = ImageTransferable(image)
        val setContents = {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(contents, null)
            retainedAwtContents = contents
        }
        if (EventQueue.isDispatchThread()) {
            setContents()
        } else {
            EventQueue.invokeAndWait(setContents)
        }
    }
}

private object WindowsImageClipboard {
    fun copy(image: BufferedImage) {
        val dib = createDeviceIndependentBitmap(image)
        val memory = requireNotNull(WINDOWS_MEMORY.globalAlloc(GLOBAL_MEMORY_FLAGS, BaseTSD.SIZE_T(dib.size.toLong()))) {
            "Windows could not allocate clipboard memory"
        }
        var isMemoryTransferred = false
        try {
            val destination = requireNotNull(WINDOWS_MEMORY.globalLock(memory)) {
                "Windows could not lock clipboard memory"
            }
            destination.write(0L, dib, 0, dib.size)
            WINDOWS_MEMORY.globalUnlock(memory)
            check(WINDOWS_CLIPBOARD.openClipboard(null) != 0) { "Windows could not open the clipboard" }
            try {
                check(WINDOWS_CLIPBOARD.emptyClipboard() != 0) { "Windows could not clear the clipboard" }
                check(WINDOWS_CLIPBOARD.setClipboardData(CLIPBOARD_FORMAT_DIB, memory) != null) {
                    "Windows could not copy the screenshot"
                }
                isMemoryTransferred = true
            } finally {
                WINDOWS_CLIPBOARD.closeClipboard()
            }
        } finally {
            if (!isMemoryTransferred) WINDOWS_MEMORY.globalFree(memory)
        }
    }

    private fun createDeviceIndependentBitmap(image: BufferedImage): ByteArray {
        val rowPixelBytes = Math.multiplyExact(image.width, BYTES_PER_PIXEL)
        val rowStride = (rowPixelBytes + ROW_ALIGNMENT - 1) / ROW_ALIGNMENT * ROW_ALIGNMENT
        val imageBytes = Math.multiplyExact(rowStride, image.height)
        val buffer = ByteBuffer.allocate(Math.addExact(BITMAP_HEADER_BYTES, imageBytes)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(BITMAP_HEADER_BYTES)
        buffer.putInt(image.width)
        buffer.putInt(image.height)
        buffer.putShort(BITMAP_PLANES)
        buffer.putShort(BITMAP_BITS_PER_PIXEL)
        buffer.putInt(BITMAP_COMPRESSION_RGB)
        buffer.putInt(imageBytes)
        repeat(BITMAP_REMAINING_INTEGER_FIELDS) { buffer.putInt(0) }
        for (y in image.height - 1 downTo 0) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                buffer.put((pixel and COLOR_CHANNEL_MASK).toByte())
                buffer.put((pixel shr GREEN_SHIFT and COLOR_CHANNEL_MASK).toByte())
                buffer.put((pixel shr RED_SHIFT and COLOR_CHANNEL_MASK).toByte())
            }
            repeat(rowStride - rowPixelBytes) { buffer.put(0) }
        }
        return buffer.array()
    }

    private const val GLOBAL_MEMORY_FLAGS = 0x0042
    private const val CLIPBOARD_FORMAT_DIB = 8
    private const val BITMAP_HEADER_BYTES = 40
    private const val BITMAP_PLANES: Short = 1
    private const val BITMAP_BITS_PER_PIXEL: Short = 24
    private const val BITMAP_COMPRESSION_RGB = 0
    private const val BITMAP_REMAINING_INTEGER_FIELDS = 4
    private const val BYTES_PER_PIXEL = 3
    private const val ROW_ALIGNMENT = 4
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val COLOR_CHANNEL_MASK = 0xFF
    private val WINDOWS_API_OPTIONS = HashMap(W32APIOptions.DEFAULT_OPTIONS).apply {
        put(
            Library.OPTION_FUNCTION_MAPPER,
            FunctionMapper { _, method -> method.name.replaceFirstChar(Char::uppercaseChar) },
        )
    }
    private val WINDOWS_CLIPBOARD = Native.load("user32", WindowsClipboardApi::class.java, WINDOWS_API_OPTIONS)
    private val WINDOWS_MEMORY = Native.load("kernel32", WindowsMemoryApi::class.java, WINDOWS_API_OPTIONS)
}

private interface WindowsClipboardApi : StdCallLibrary {
    fun openClipboard(window: Pointer?): Int

    fun emptyClipboard(): Int

    fun setClipboardData(format: Int, memory: Pointer): Pointer?

    fun closeClipboard(): Int
}

private interface WindowsMemoryApi : StdCallLibrary {
    fun globalAlloc(flags: Int, bytes: BaseTSD.SIZE_T): Pointer?

    fun globalLock(memory: Pointer): Pointer?

    fun globalUnlock(memory: Pointer): Int

    fun globalFree(memory: Pointer): Pointer?
}

private class ImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Image {
        require(isDataFlavorSupported(flavor)) { "Unsupported clipboard flavor: $flavor" }
        return image
    }
}
