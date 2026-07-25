package com.skysoft.features.screenshot

import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal object ScreenshotEditedImage {
    fun render(sourcePath: Path, snapshot: ScreenshotEditSnapshot): BufferedImage {
        val source = requireNotNull(ImageIO.read(sourcePath.toFile())) { "Unsupported screenshot image: $sourcePath" }
        val bounds = cropBounds(source, snapshot.crop)
        val output = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = output.createGraphics()
        try {
            graphics.drawImage(
                source,
                0,
                0,
                bounds.width,
                bounds.height,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                null,
            )
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            snapshot.strokes.forEach { drawStroke(graphics, it, source.width, source.height, bounds) }
        } finally {
            graphics.dispose()
        }
        return output
    }

    fun write(sourcePath: Path, snapshot: ScreenshotEditSnapshot, destination: Path) {
        val output = render(sourcePath, snapshot)
        val absoluteDestination = destination.toAbsolutePath().normalize()
        val directory = requireNotNull(absoluteDestination.parent) { "Screenshot destination has no parent" }
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".skysoft-screenshot-", ".png")
        try {
            check(ImageIO.write(output, "png", temporary.toFile())) { "PNG writer is unavailable" }
            Files.move(temporary, absoluteDestination, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun drawStroke(
        graphics: java.awt.Graphics2D,
        stroke: ScreenshotStroke,
        imageWidth: Int,
        imageHeight: Int,
        crop: PixelCrop,
    ) {
        val first = stroke.points.firstOrNull() ?: return
        val width = (stroke.normalizedWidth * minOf(imageWidth, imageHeight)).toFloat().coerceAtLeast(1f)
        graphics.color = java.awt.Color(stroke.color.argb, true)
        graphics.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (stroke.points.size == 1) {
            val radius = width / 2f
            val x = first.x * imageWidth - crop.left
            val y = first.y * imageHeight - crop.top
            graphics.fillOval(
                (x - radius).roundToInt(),
                (y - radius).roundToInt(),
                width.roundToInt().coerceAtLeast(1),
                width.roundToInt().coerceAtLeast(1),
            )
            return
        }
        val path = Path2D.Double()
        path.moveTo(first.x * imageWidth - crop.left, first.y * imageHeight - crop.top)
        stroke.points.drop(1).forEach {
            path.lineTo(it.x * imageWidth - crop.left, it.y * imageHeight - crop.top)
        }
        graphics.draw(path)
    }

    private fun cropBounds(image: BufferedImage, crop: ScreenshotCrop): PixelCrop {
        val left = floor(crop.left * image.width).toInt().coerceIn(0, image.width - 1)
        val top = floor(crop.top * image.height).toInt().coerceIn(0, image.height - 1)
        val right = ceil(crop.right * image.width).toInt().coerceIn(left + 1, image.width)
        val bottom = ceil(crop.bottom * image.height).toInt().coerceIn(top + 1, image.height)
        return PixelCrop(left, top, right, bottom)
    }

    private data class PixelCrop(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }
}
