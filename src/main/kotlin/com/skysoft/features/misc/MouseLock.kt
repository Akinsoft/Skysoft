package com.skysoft.features.misc

import com.mojang.brigadier.Command
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object MouseLock {
    private var locked = false

    fun register() {
        SkysoftClientEvents.onDisconnect("Mouse Lock reset") { locked = false }
    }

    fun toggle(source: FabricClientCommandSource): Int {
        locked = !locked
        SkysoftChat.feedback(
            source,
            if (locked) "Mouse rotation locked. Run /ss mouselock again to unlock it."
            else "Mouse rotation unlocked.",
        )
        return Command.SINGLE_SUCCESS
    }

    @JvmStatic
    fun apply(delta: Double): Double = if (locked) 0.0 else delta
}
