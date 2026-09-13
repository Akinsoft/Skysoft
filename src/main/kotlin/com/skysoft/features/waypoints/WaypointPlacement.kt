package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult

internal object WaypointPlacement {
    private var target: WorldVec? = null
    private var targetLevel: ClientLevel? = null
    val crosshair: WorldVec? get() = target.takeIf { targetLevel === Minecraft.getInstance().level }
    val feet: WorldVec? get() = Minecraft.getInstance().player?.position()?.toWorldVec()?.let(::feetAt)

    fun feetAt(position: WorldVec): WorldVec = position.down(FEET_EPSILON).roundToBlock()

    fun capture(minecraft: Minecraft) {
        if (MinecraftClient.screen(minecraft) == null) captureAim(minecraft)
    }

    fun captureAim(minecraft: Minecraft = Minecraft.getInstance()) {
        val player = minecraft.player ?: return clear()
        val level = minecraft.level ?: return clear()
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(PLACEMENT_RANGE))
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        target = hit.blockPos.toWorldVec().takeIf { hit.type == HitResult.Type.BLOCK }
        targetLevel = level
    }

    fun clear() {
        target = null
        targetLevel = null
    }

    private const val PLACEMENT_RANGE = 128.0
    private const val FEET_EPSILON = 0.01
}
