package com.skysoft.features.inventory

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import net.fabricmc.loader.api.FabricLoader

internal fun discoverInventoryButtonImportSources(): List<InventoryButtonImportSource> =
    findInventoryButtonImportSources(defaultInventoryButtonImportRoots())

internal fun discoverInventoryButtonImportSources(path: Path): List<InventoryButtonImportSource> {
    val normalized = path.toAbsolutePath().normalize()
    if (Files.isRegularFile(normalized)) return sourceFromImportFile(normalized)?.let(::listOf).orEmpty()
    return findInventoryButtonImportSources(candidateRoots(normalized))
}

internal fun findInventoryButtonImportSources(roots: Iterable<Path>): List<InventoryButtonImportSource> {
    val sources = linkedMapOf<Path, InventoryButtonImportSource>()
    roots.asSequence()
        .map { it.toAbsolutePath().normalize() }
        .distinct()
        .forEach { root ->
            sourceCandidates(root).forEach { source ->
                if (Files.isRegularFile(source.path)) {
                    sources.putIfAbsent(source.path.toAbsolutePath().normalize(), source)
                }
            }
        }
    return sources.values.sortedWith(
        compareByDescending<InventoryButtonImportSource> { Files.getLastModifiedTime(it.path).toMillis() }
            .thenBy { it.kind.ordinal }
            .thenBy { it.path.toString() },
    )
}

private fun defaultInventoryButtonImportRoots(): Set<Path> {
    val roots = linkedSetOf<Path>()
    val gameDir = FabricLoader.getInstance().gameDir.toAbsolutePath().normalize()
    roots += candidateRoots(gameDir)
    val appData = System.getenv("APPDATA")?.takeIf(String::isNotBlank)?.let(Path::of)
    val home = System.getProperty("user.home")?.takeIf(String::isNotBlank)?.let(Path::of)
    appData?.resolve(".minecraft")?.let(roots::add)
    listOfNotNull(
        appData?.resolve("com.modrinth.theseus/profiles"),
        appData?.resolve("ModrinthApp/profiles"),
        appData?.resolve("PrismLauncher/instances"),
        appData?.resolve("gdlauncher_next/instances"),
        home?.resolve("curseforge/minecraft/Instances"),
    ).forEach { roots += profileRoots(it) }
    return roots
}

private fun candidateRoots(path: Path): Set<Path> = buildSet {
    add(path)
    add(path.resolve(".minecraft"))
    if (path.fileName?.toString()?.equals("config", ignoreCase = true) == true) path.parent?.let(::add)
    if (path.fileName?.toString()?.equals(".minecraft", ignoreCase = true) == true) path.parent?.let(::add)
    val parent = if (path.fileName?.toString()?.equals(".minecraft", ignoreCase = true) == true) {
        path.parent?.parent
    } else {
        path.parent
    }
    parent?.takeIf { it.fileName?.toString()?.lowercase(Locale.ROOT) in PROFILE_CONTAINER_NAMES }?.let {
        addAll(profileRoots(it))
    }
}

private fun profileRoots(container: Path): Set<Path> {
    if (!Files.isDirectory(container)) return emptySet()
    val roots = linkedSetOf<Path>()
    Files.list(container).use { entries ->
        entries.filter(Files::isDirectory)
            .limit(MAX_DISCOVERY_PROFILES.toLong())
            .forEach { profile ->
                roots.add(profile)
                roots.add(profile.resolve(".minecraft"))
            }
    }
    return roots
}

private fun sourceCandidates(root: Path): List<InventoryButtonImportSource> {
    val profileName = if (root.fileName?.toString() == ".minecraft") {
        root.parent?.fileName?.toString()
    } else {
        root.fileName?.toString()
    }.orEmpty().ifBlank { root.toString() }
    val neu = listOf(
        root.resolve("config/notenoughupdates/configNew.json"),
        root.resolve("notenoughupdates/configNew.json"),
        root.resolve("configNew.json"),
    ).firstOrNull(Files::isRegularFile)
    val firmamentDirectory = listOf(
        root.resolve("config/firmament"),
        root.resolve("firmament"),
        root,
    ).firstOrNull { directory ->
        Files.isRegularFile(directory.resolve("storage/inventory-buttons.json")) ||
            Files.isRegularFile(directory.resolve("inventory-buttons.json"))
    }
        ?: root.resolve("config/firmament")
    val firmament = listOf(
        firmamentDirectory.resolve("storage/inventory-buttons.json"),
        firmamentDirectory.resolve("inventory-buttons.json"),
    ).firstOrNull(Files::isRegularFile)
    return buildList {
        neu?.let { add(InventoryButtonImportSource(InventoryButtonImportKind.NEU, it, profileName = profileName)) }
        firmament?.let {
            add(
                InventoryButtonImportSource(
                    InventoryButtonImportKind.FIRMAMENT,
                    it,
                    firmamentDirectory.resolve("inventory-buttons-config.json").takeIf(Files::isRegularFile),
                    profileName,
                ),
            )
        }
    }
}

private fun sourceFromImportFile(path: Path): InventoryButtonImportSource? {
    val profileName = path.parent?.fileName?.toString().orEmpty().ifBlank { path.toString() }
    return when {
        path.fileName.toString().equals("configNew.json", ignoreCase = true) ->
            InventoryButtonImportSource(InventoryButtonImportKind.NEU, path, profileName = profileName)
        path.fileName.toString().equals("inventory-buttons.json", ignoreCase = true) -> {
            val firmamentDirectory = if (path.parent?.fileName?.toString() == "storage") {
                path.parent?.parent
            } else {
                path.parent
            }
            InventoryButtonImportSource(
                InventoryButtonImportKind.FIRMAMENT,
                path,
                firmamentDirectory?.resolve("inventory-buttons-config.json")?.takeIf(Files::isRegularFile),
                profileName,
            )
        }
        else -> null
    }
}

private const val MAX_DISCOVERY_PROFILES = 256
private val PROFILE_CONTAINER_NAMES = setOf("profiles", "instances")
