package com.skysoft.utils.image

import com.mojang.blaze3d.platform.NativeImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

object ScaledImageDecoder {
    fun decode(bytes: ByteArray, maximumWidth: Int, maximumHeight: Int): NativeImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            requireNotNull(input) { "Could not read image data" }
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Unsupported image format" }
            val reader = readers.next()
            try {
                reader.input = input
                val sourceWidth = reader.getWidth(0)
                val sourceHeight = reader.getHeight(0)
                require(sourceWidth in 1..MAXIMUM_SOURCE_DIMENSION && sourceHeight in 1..MAXIMUM_SOURCE_DIMENSION) {
                    "Image dimensions are too large"
                }
                require(sourceWidth.toLong() * sourceHeight <= MAXIMUM_SOURCE_PIXELS) {
                    "Image dimensions are too large"
                }
                val source = reader.read(0)
                val scale = min(min(maximumWidth.toDouble() / sourceWidth, maximumHeight.toDouble() / sourceHeight), 1.0)
                val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
                val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                return NativeImage(width, height, false).also { image ->
                    try {
                        for (y in 0 until height) {
                            val sourceY = y * sourceHeight / height
                            for (x in 0 until width) {
                                image.setPixel(x, y, source.getRGB(x * sourceWidth / width, sourceY))
                            }
                        }
                    } catch (failure: Throwable) {
                        image.close()
                        throw failure
                    }
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private const val MAXIMUM_SOURCE_DIMENSION = 8192
    private const val MAXIMUM_SOURCE_PIXELS = 16_000_000L
}
