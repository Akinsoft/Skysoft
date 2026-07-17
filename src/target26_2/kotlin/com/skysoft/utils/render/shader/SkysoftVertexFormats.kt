// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.shader

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.VertexFormat
import org.lwjgl.system.MemoryUtil

object SkysoftVertexFormats {
    enum class VertexElement(private val attributeName: String) {
        ROUNDED_PARAMS_0("RoundedParams0"),
        ROUNDED_PARAMS_1("RoundedParams1"),
        CONTOUR_UV_BOUNDS("ContourUvBounds"),
        ;

        val element
            get() = checkNotNull(vertexFormat.getElement(attributeName)) {
                "Vertex format is missing $attributeName"
            }

        val offset: Int
            get() = element.offset()

        private val vertexFormat: VertexFormat
            get() = when (this) {
                ROUNDED_PARAMS_0, ROUNDED_PARAMS_1 -> POSITION_COLOR_ROUNDED
                CONTOUR_UV_BOUNDS -> POSITION_TEX_COLOR_CONTOUR
            }
    }

    val POSITION_COLOR_ROUNDED: VertexFormat by lazy {
        VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("RoundedParams0", GpuFormat.RGBA32_FLOAT)
            .addAttribute("RoundedParams1", GpuFormat.RGBA32_FLOAT)
            .build()
    }

    val POSITION_TEX_COLOR_CONTOUR: VertexFormat by lazy {
        VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("ContourUvBounds", GpuFormat.RGBA32_FLOAT)
            .build()
    }

    fun BufferBuilder.writeParams(
        x: Float,
        y: Float,
        z: Float,
        w: Float,
        format: VertexElement,
    ) {
        val ptr = (this@writeParams as VertexMemoryAccess).skysoftAttributeAddress(
            format.element,
            format.offset,
        )
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
    private const val Y_COMPONENT_BYTE_OFFSET = FLOAT_BYTE_SIZE
    private const val Z_COMPONENT_BYTE_OFFSET = FLOAT_BYTE_SIZE * 2
    private const val W_COMPONENT_BYTE_OFFSET = FLOAT_BYTE_SIZE * 3
}
