package com.skysoft.config

import com.skysoft.SkysoftMod
import net.fabricmc.loader.api.FabricLoader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

object SkysoftConfigFiles {
    private const val CONFIG_DIRECTORY_PROPERTY = "skysoft.configDirectory"
    private val rootDirectory: Path = FabricLoader.getInstance().configDir
    val directory: Path = rootDirectory.resolve(configDirectoryName())
    val config: Path = directory.resolve("skysoft.json")
    val featureDiscovery: Path = directory.resolve("feature-discovery.json")
    val chatHistory: Path = directory.resolve("chat-history.json")
    val profileStorage: Path = directory.resolve("skysoft-storage.json")
    val spotifyAuthentication: Path = directory.resolve("spotify-auth.json")
    val learnedPetAnimations: Path = directory.resolve("pet-animations.json")
    val dianaHubSurfaceCache: Path = directory.resolve("diana-hub-surface-cache.json")
    val mythologicalRitualTracker: Path = directory
        .resolve("trackers")
        .resolve("mythological-ritual")
        .resolve("tracker.json")
    val legacyConfig: Path = rootDirectory.resolve("skysoft.json")
    val legacyProfileStorage: Path = rootDirectory.resolve("skysoft-storage.json")

    fun migrateConfig(): MigrationResult = copyLegacyFile(legacyConfig, config)

    fun migrateProfileStorage(): MigrationResult = copyLegacyFile(legacyProfileStorage, profileStorage)

    private fun configDirectoryName(): String {
        val configuredName = System.getProperty(CONFIG_DIRECTORY_PROPERTY)?.trim()
        val directoryName = configuredName?.takeIf { it.isNotEmpty() }
            ?: SkysoftMod.MOD_ID
        require(directoryName.none { it == '/' || it == '\\' }) {
            "Skysoft config directory must be a directory name, not a path: $directoryName"
        }
        return directoryName
    }

    fun hasFileOrBackup(path: Path): Boolean =
        SkysoftConfigFileIo.hasFileOrBackup(path)

    fun writeStringSafely(path: Path, text: String) {
        SkysoftConfigFileIo.writeStringSafely(path, text)
    }

    fun deleteWithBackups(path: Path) {
        SkysoftConfigFileIo.deleteWithBackups(path)
    }

    fun <T> readWithBackup(path: Path, reader: (Path) -> T): T {
        return SkysoftConfigFileIo.readWithBackup(path, reader)
    }

    private fun copyLegacyFile(source: Path, target: Path): MigrationResult =
        SkysoftConfigFileIo.copyLegacyFile(source, target)
}

enum class MigrationResult {
    READY,
    FAILED,
}

internal object SkysoftConfigFileIo {
    private const val MAX_WRITE_ATTEMPTS = 5
    private const val WRITE_RETRY_DELAY_MS = 50L
    private const val BACKUP_COUNT = 3
    private val nonAtomicMoveWarnings = mutableSetOf<Path>()

    fun hasFileOrBackup(path: Path): Boolean =
        Files.isRegularFile(path) || backupPaths(path).any(Files::isRegularFile)

    fun writeStringSafely(path: Path, text: String) {
        retryAccessDenied {
            writeStringSafelyOnce(path, text)
        }
    }

    fun <T> readWithBackup(path: Path, reader: (Path) -> T): T {
        val primaryException = try {
            return reader(path)
        } catch (e: Exception) {
            e
        }
        val failures = mutableListOf(primaryException)

        for (backupPath in backupPaths(path)) {
            if (!Files.isRegularFile(backupPath)) continue

            SkysoftMod.LOGGER.warn("Failed to read {}, trying backup {}", path, backupPath, failures.last())
            try {
                return reader(backupPath).also {
                    try {
                        restoreBackup(path, backupPath)
                    } catch (restoreException: Exception) {
                        SkysoftMod.LOGGER.warn(
                            "Loaded Skysoft backup {} but could not restore it to {}",
                            backupPath,
                            path,
                            restoreException,
                        )
                    }
                }
            } catch (backupException: Exception) {
                failures += backupException
            }
        }

        failures.drop(1).forEach(primaryException::addSuppressed)
        throw primaryException
    }

    fun deleteWithBackups(path: Path) {
        Files.deleteIfExists(path)
        backupPaths(path).forEach(Files::deleteIfExists)
    }

    fun copyLegacyFile(source: Path, target: Path): MigrationResult {
        if (!Files.isRegularFile(source) || Files.exists(target) || hasBackup(target)) return MigrationResult.READY

        return try {
            Files.createDirectories(target.parent)
            copyFileSafely(source, target)
            SkysoftMod.LOGGER.info("Copied legacy Skysoft config file from {} to {}", source, target)
            MigrationResult.READY
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to copy legacy Skysoft config file from $source to $target", e)
            MigrationResult.FAILED
        }
    }

    private fun writeStringSafelyOnce(path: Path, text: String) {
        val backupPath = backupPath(path)
        val tempPath = createTempSibling(path)
        var createdBackup = false
        var replacedTarget = false
        try {
            writeStringDurably(tempPath, text)
            if (Files.exists(path)) {
                rotateBackups(path)
                copyFileSafely(path, backupPath)
                createdBackup = true
            }
            moveReplacing(tempPath, path)
            replacedTarget = true
        } catch (e: Exception) {
            Files.deleteIfExists(tempPath)
            if (createdBackup && !replacedTarget) {
                try {
                    restoreBackup(path, backupPath)
                } catch (restoreException: Exception) {
                    e.addSuppressed(restoreException)
                    SkysoftMod.LOGGER.error("Failed to restore Skysoft backup {} to {}", backupPath, path, restoreException)
                }
            }
            throw e
        }
    }

    private fun restoreBackup(path: Path, backupPath: Path) {
        copyFileSafely(backupPath, path)
        SkysoftMod.LOGGER.warn("Restored Skysoft file {} from backup {}", path, backupPath)
    }

    private fun rotateBackups(path: Path) {
        val backups = backupPaths(path)
        for (index in backups.lastIndex downTo 0) {
            val backupPath = backups[index]
            if (!Files.exists(backupPath)) continue

            if (index == backups.lastIndex) {
                Files.deleteIfExists(backupPath)
            } else {
                moveReplacing(backupPath, backups[index + 1])
            }
        }
    }

    private fun copyFileSafely(source: Path, target: Path) {
        val tempPath = createTempSibling(target, ".copy")
        try {
            Files.copy(source, tempPath, StandardCopyOption.REPLACE_EXISTING)
            forceFile(tempPath)
            moveReplacing(tempPath, target)
        } finally {
            Files.deleteIfExists(tempPath)
        }
    }

    private fun createTempSibling(path: Path, nameSuffix: String = ""): Path {
        Files.createDirectories(path.parent)
        return Files.createTempFile(path.parent, "${path.fileName}$nameSuffix.", ".tmp")
    }

    private fun writeStringDurably(path: Path, text: String) {
        val buffer = ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8))
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }

    private fun forceFile(path: Path) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { channel ->
            channel.force(true)
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            if (nonAtomicMoveWarnings.add(target)) {
                SkysoftMod.LOGGER.warn("Atomic move is not supported for {}, using replace move instead", target)
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun <T> retryAccessDenied(action: () -> T): T {
        repeat(MAX_WRITE_ATTEMPTS - 1) { attempt ->
            try {
                return action()
            } catch (e: AccessDeniedException) {
                val retryAttempt = attempt + RETRY_ATTEMPT_DISPLAY_OFFSET
                SkysoftMod.LOGGER.warn(
                    "Access denied while saving Skysoft config, retrying attempt {}",
                    retryAttempt,
                    e,
                )
                Thread.sleep(WRITE_RETRY_DELAY_MS)
            }
        }
        return action()
    }

    private fun backupPath(path: Path): Path = path.resolveSibling("${path.fileName}.bak")

    private fun backupPaths(path: Path): List<Path> =
        (0 until BACKUP_COUNT).map { index ->
            if (index == 0) backupPath(path) else path.resolveSibling("${path.fileName}.bak.$index")
        }

    private fun hasBackup(path: Path): Boolean =
        backupPaths(path).any(Files::isRegularFile)
}

private const val RETRY_ATTEMPT_DISPLAY_OFFSET = 2
