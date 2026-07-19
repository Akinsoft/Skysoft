package com.skysoft.features.inventory

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import com.skysoft.utils.render.GuiRenderStateAccess
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc

internal fun drawSlotGridBackground(
    context: GuiGraphicsExtractor,
    x: Int,
    y: Int,
    columns: Int,
    rows: Int,
    separatorColor: Int = StorageColors.SLOT_BACKGROUND,
    scissorArea: ScreenRectangle? = null,
) {
    if (columns <= 0 || rows <= 0) return
    GuiRenderStateAccess.get(context).addGuiElement(
        StorageSlotGridRenderState(
            Matrix3x2f(context.pose()),
            x,
            y,
            columns,
            rows,
            separatorColor,
            scissorArea,
        ),
    )
}

private class StorageSlotGridRenderState(
    private val pose: Matrix3x2fc,
    private val x: Int,
    private val y: Int,
    private val columns: Int,
    private val rows: Int,
    private val separatorColor: Int,
    private val scissorArea: ScreenRectangle?,
) : GuiElementRenderState {
    private val left = x - StorageSlots.BORDER
    private val top = y - StorageSlots.BORDER
    private val right = x + columns * StorageSlots.SIZE - StorageSlots.BORDER
    private val bottom = y + rows * StorageSlots.SIZE - StorageSlots.BORDER
    private val bounds = ScreenRectangle(left, top, right - left, bottom - top)
        .transformMaxBounds(pose)
        .let { scissorArea?.intersection(it) ?: it }

    override fun pipeline(): RenderPipeline = RenderPipelines.GUI

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = scissorArea

    override fun bounds(): ScreenRectangle = bounds

    override fun buildVertices(consumer: VertexConsumer) {
        addRectangle(consumer, left, top, right, bottom, StorageColors.SELECTOR_SLOT)
        addRectangle(consumer, left, top, x, bottom, separatorColor)
        for (column in 1 until columns) {
            val separatorX = x + column * StorageSlots.SIZE - StorageSlots.BORDER * 2
            addRectangle(
                consumer,
                separatorX,
                top,
                separatorX + StorageSlots.BORDER * 2,
                bottom,
                separatorColor,
            )
        }
        addRectangle(consumer, right - StorageSlots.BORDER, top, right, bottom, separatorColor)
        addRectangle(consumer, left, top, right, y, separatorColor)
        for (row in 1 until rows) {
            val separatorY = y + row * StorageSlots.SIZE - StorageSlots.BORDER * 2
            addRectangle(
                consumer,
                left,
                separatorY,
                right,
                separatorY + StorageSlots.BORDER * 2,
                separatorColor,
            )
        }
        addRectangle(consumer, left, bottom - StorageSlots.BORDER, right, bottom, separatorColor)
    }

    private fun addRectangle(
        consumer: VertexConsumer,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int,
    ) {
        consumer.addVertexWith2DPose(pose, left.toFloat(), top.toFloat()).setColor(color)
        consumer.addVertexWith2DPose(pose, left.toFloat(), bottom.toFloat()).setColor(color)
        consumer.addVertexWith2DPose(pose, right.toFloat(), bottom.toFloat()).setColor(color)
        consumer.addVertexWith2DPose(pose, right.toFloat(), top.toFloat()).setColor(color)
    }
}
