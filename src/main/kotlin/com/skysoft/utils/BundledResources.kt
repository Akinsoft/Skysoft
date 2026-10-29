package com.skysoft.utils

import java.io.Reader

internal object BundledResources {
    fun <T> read(path: String, description: String, read: (Reader) -> T): T =
        requireNotNull(BundledResources::class.java.getResourceAsStream(path)) { "Missing bundled $description" }
            .bufferedReader()
            .use(read)
}
