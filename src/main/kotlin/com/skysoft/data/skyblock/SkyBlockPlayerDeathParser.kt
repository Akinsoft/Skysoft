package com.skysoft.data.skyblock

object SkyBlockPlayerDeathParser {
    fun isLocalDeath(message: String): Boolean =
        message.startsWith("You died") ||
            message.startsWith("You were killed by ") ||
            message.startsWith("☠ You died") ||
            message.startsWith("☠ You were killed by ")
}
