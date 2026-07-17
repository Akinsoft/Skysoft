// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.shader

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import org.lwjgl.system.MemoryUtil

object SkysoftVertexFormats {
    private val lastRegisteredId by lazy {
        (0 until VertexFormatElement.MAX_COUNT).filter { VertexFormatElement.byId(it) != null }.max()
    }

    enum class VertexElement {
        ROUNDED_PARAMS_0,
        ROUNDED_PARAMS_1,
        CONTOUR_UV_BOUNDS,
        ;

        private val registrationId: Int by lazy { lastRegisteredId + ordinal + 1 }
        val element: VertexFormatElement by lazy {
            safeRegister(registrationId)
        }
    }

    val POSITION_COLOR_ROUNDED: VertexFormat by lazy {
        VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("RoundedParams0", VertexElement.ROUNDED_PARAMS_0.element)
            .add("RoundedParams1", VertexElement.ROUNDED_PARAMS_1.element)
            .build()
    }

    val POSITION_TEX_COLOR_CONTOUR: VertexFormat by lazy {
        VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("ContourUvBounds", VertexElement.CONTOUR_UV_BOUNDS.element)
            .build()
    }

    private fun safeRegister(desiredId: Int): VertexFormatElement {
        val id = (desiredId until VertexFormatElement.MAX_COUNT).first { VertexFormatElement.byId(it) == null }
        return VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, false, PARAMETER_COMPONENT_COUNT)
    }

    fun BufferBuilder.writeParams(
        x: Float,
        y: Float,
        z: Float,
        w: Float,
        format: VertexElement,
    ) {
        val ptr = (this@writeParams as VertexMemoryAccess).skysoftAttributeAddress(format.element, 0)
        writeParams(ptr, x, y, z, w, format.name)
    }

    private fun writeParams(ptr: Long, x: Float, y: Float, z: Float, w: Float, name: String) {
        check(ptr != -1L) { "Vertex format is missing $name" }
        MemoryUtil.memPutFloat(ptr, x)
        MemoryUtil.memPutFloat(ptr + Y_COMPONENT_BYTE_OFFSET, y)
        MemoryUtil.memPutFloat(ptr + Z_COMPONENT_BYTE_OFFSET, z)
        MemoryUtil.memPutFloat(ptr + W_COMPONENT_BYTE_OFFSET, w)
    }

    private const val FLOAT_BYTE_SIZE = 4L
    private const val PARAMETER_COMPONENT_COUNT = 4
    private const val Y_COMPONENT_BYTE_OFFSET = FLOAT_BYTE_SIZE
    private const val Z_COMPONENT_BYTE_OFFSET = FLOAT_BYTE_SIZE * 2
    private const val W_COMPONENT_BYTE_OFFSET = FLOAT_BYTE_SIZE * 3
}
