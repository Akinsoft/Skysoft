package com.skysoft.data.skyblock

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

object SkyBlockCatalogCacheFiles {
    fun resolve(fileName: String): Path =
        FabricLoader.getInstance().gameDir.resolve(CACHE_DIRECTORY).resolve(fileName)

    private const val CACHE_DIRECTORY = "skyblock-data-cache"
}
