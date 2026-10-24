package com.skysoft.utils.render.shader

import com.skysoft.utils.render.gpu.VertexFormatElement

interface VertexMemoryAccess {
    fun skysoftAttributeAddress(element: VertexFormatElement, byteOffset: Int): Long
}
