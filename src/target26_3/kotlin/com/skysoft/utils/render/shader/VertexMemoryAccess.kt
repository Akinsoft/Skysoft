package com.skysoft.utils.render.shader

import com.mojang.renderpearl.api.vertex.VertexFormatElement

interface VertexMemoryAccess {
    fun skysoftAttributeAddress(element: VertexFormatElement, byteOffset: Int): Long
}
