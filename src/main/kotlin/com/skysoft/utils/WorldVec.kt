package com.skysoft.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.sqrt

data class WorldVec(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: WorldVec): WorldVec = WorldVec(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: WorldVec): WorldVec = WorldVec(x - other.x, y - other.y, z - other.z)
    operator fun times(other: Number): WorldVec = WorldVec(x * other.toDouble(), y * other.toDouble(), z * other.toDouble())

    fun dot(other: WorldVec): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: WorldVec): WorldVec = WorldVec(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun length(): Double = sqrt(lengthSq())

    fun lengthSq(): Double = x * x + y * y + z * z

    fun distance(other: WorldVec): Double = sqrt(distanceSq(other))

    fun distanceSq(other: WorldVec): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun horizontalDistanceSq(other: WorldVec): Double {
        val dx = x - other.x
        val dz = z - other.z
        return dx * dx + dz * dz
    }

    fun blockCenter(): WorldVec = WorldVec(
        floor(x) + BLOCK_CENTER_OFFSET,
        floor(y) + BLOCK_CENTER_OFFSET,
        floor(z) + BLOCK_CENTER_OFFSET,
    )

    fun roundToBlock(): WorldVec = WorldVec(floor(x), floor(y), floor(z))

    fun blockKey(): String = roundToBlock().let { "${it.x.toInt()}:${it.y.toInt()}:${it.z.toInt()}" }

    fun down(amount: Double = 1.0): WorldVec = WorldVec(x, y - amount, z)

    fun up(amount: Double = 1.0): WorldVec = WorldVec(x, y + amount, z)

    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    fun normalize(): WorldVec {
        val length = length()
        return if (length == 0.0) this else WorldVec(x / length, y / length, z / length)
    }
}

private const val BLOCK_CENTER_OFFSET = 0.5

fun Vec3.toWorldVec(): WorldVec = WorldVec(x, y, z)

fun BlockPos.toWorldVec(): WorldVec = WorldVec(x.toDouble(), y.toDouble(), z.toDouble())

fun WorldVec.toBlockPos(): BlockPos = BlockPos(x.toInt(), y.toInt(), z.toInt())

val Entity.location: WorldVec get() = position().toWorldVec()
