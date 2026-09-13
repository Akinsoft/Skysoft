package com.skysoft.features.waypoints

import java.io.ByteArrayInputStream
import java.io.DataInputStream

internal object WaypointSoopyImport {
    fun decode(bytes: ByteArray): WaypointImport {
        val context = WaypointImportContext("Soopy V1")
        val areas = linkedMapOf<String, MutableList<WaypointPoint>>()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUnsignedByte() == 1) { "Unsupported Soopy waypoint version." }
            val count = input.readInt()
            require(count in 1..MAX_WAYPOINT_POINTS) { "Invalid Soopy waypoint count." }
            repeat(count) {
                val id = input.readUTF()
                val x = input.readFloat().toDouble()
                val y = input.readFloat().toDouble()
                val z = input.readFloat().toDouble()
                val color = (channel(input) shl RED_SHIFT) or (channel(input) shl GREEN_SHIFT) or channel(input)
                val area = input.readUTF()
                val name = context.name(input.readUTF().ifBlank { id })
                areas.getOrPut(area) { mutableListOf() } += WaypointPoint(name = name, x = x, y = y, z = z, color = color)
            }
            require(input.available() == 0) { "Soopy share contains trailing data." }
        }
        return context.finish(
            areas.map { (area, points) ->
                context.group("Soopy ${area.ifBlank { "waypoints" }}", area, points, ordered = false)
            }
        )
    }

    private fun channel(input: DataInputStream): Int = input.readByte().toInt().also {
        require(it >= 0) { "Invalid Soopy color channel." }
    } * 2

    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
