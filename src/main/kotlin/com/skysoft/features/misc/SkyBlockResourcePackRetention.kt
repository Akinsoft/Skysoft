package com.skysoft.features.misc

import com.google.common.hash.HashCode
import com.skysoft.config.SkysoftConfigGui
import java.net.URL

object SkyBlockResourcePackRetention {
    @JvmStatic
    fun isEnabled(): Boolean = SkysoftConfigGui.config().world.keepSkyBlockResourcePack

    @JvmStatic
    fun isOfficialPackUrl(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.equals("resourcepacks.hypixel.net", ignoreCase = true) &&
            url.path.startsWith("/SkyBlock/")

    @JvmStatic
    fun canRetain(url: URL, hash: HashCode?): Boolean = hash != null && isOfficialPackUrl(url)
}
