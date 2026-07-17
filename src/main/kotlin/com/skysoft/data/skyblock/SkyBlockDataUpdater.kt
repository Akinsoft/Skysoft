package com.skysoft.data.skyblock

import com.google.gson.JsonParser
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.net.SkysoftHttp
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture

internal object SkyBlockDataUpdater {
    private val cacheDirectory by lazy { SkysoftConfigFiles.directory.resolve("item-list-data") }
    private val activeRevisionFile by lazy { cacheDirectory.resolve(ACTIVE_REVISION_FILE) }
    private val lastCheckFile by lazy { cacheDirectory.resolve("last-check.txt") }
    private var activeRequest: CompletableFuture<*>? = null

    fun loadCached(cacheRoot: Path = cacheDirectory): CachedCatalog? {
        val activeFile = cacheRoot.resolve(ACTIVE_REVISION_FILE)
        if (!Files.isRegularFile(activeFile)) return null
        val revision = Files.readString(activeFile).trim()
        if (!revision.matches(revisionPattern)) return null
        val directory = cacheRoot.resolve(revision)
        return runCatching {
            val items = Files.readString(directory.resolve(CatalogFiles.ITEMS))
            val recipes = Files.readString(directory.resolve(CatalogFiles.RECIPES))
            val wiki = Files.readString(directory.resolve(CatalogFiles.WIKI))
            val mobs = Files.readString(directory.resolve(CatalogFiles.MOBS))
            val pets = Files.readString(directory.resolve(CatalogFiles.PETS))
            val archive = Base64.getDecoder().decode(Files.readString(directory.resolve(REPO_ARCHIVE_FILE)))
            val snapshot = SkyBlockDataLoader.loadJson(items, recipes, wiki, mobs, pets)
            CachedCatalog(revision, SkyBlockRepoArchive.apply(snapshot, archive))
        }.onFailure { error ->
            SkysoftMod.LOGGER.warn("Skysoft Item List cached data is invalid", error)
        }.getOrNull()
    }

    fun check(force: Boolean = false) {
        if (activeRequest?.isDone == false) return
        if (!force && !isCheckDue()) return
        recordCheckAttempt()
        SkyBlockDataRepository.markUpdateChecking()
        val compactRevision = SkysoftHttp.getString(SHAS_URL, REQUEST_TIMEOUT)
        val repoRevision = SkysoftHttp.getString(REPO_COMMIT_URL, REQUEST_TIMEOUT)
        activeRequest = compactRevision.thenCombine(repoRevision) { shas, commit ->
            "${parseRevision(shas)}-${parseRepoRevision(commit)}"
        }.thenCompose { revision ->
            if (revision == activeRevision()) {
                return@thenCompose CompletableFuture.completedFuture<DownloadedCatalog?>(null)
            }
            val items = SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.ITEMS}", DOWNLOAD_TIMEOUT)
            val recipes = SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.RECIPES}", DOWNLOAD_TIMEOUT)
            val wiki = SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.WIKI}", DOWNLOAD_TIMEOUT)
            val mobs = SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.MOBS}", DOWNLOAD_TIMEOUT)
            val pets = SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.PETS}", DOWNLOAD_TIMEOUT)
            val archive = SkysoftHttp.getBytes(
                skyBlockRepoArchiveUrl(revision.takeLast(REVISION_PART_LENGTH)),
                DOWNLOAD_TIMEOUT,
            )
            CompletableFuture.allOf(items, recipes, wiki, mobs, pets, archive).thenApply {
                DownloadedCatalog(
                    revision,
                    items.join(),
                    recipes.join(),
                    wiki.join(),
                    mobs.join(),
                    pets.join(),
                    archive.join(),
                )
            }
        }.thenApply { downloaded ->
            downloaded?.let(::validateAndStore)
        }.whenComplete { cached, error ->
            SkysoftErrorBoundary.onClientThread("Item List update async completion") {
                when {
                    error != null -> {
                        SkyBlockDataRepository.markUpdateFailed(error.cause?.message ?: error.message ?: "Update failed")
                        SkysoftMod.LOGGER.warn("Skysoft Item List update failed", error)
                    }
                    cached != null -> SkyBlockDataRepository.applyUpdated(cached.snapshot, cached.revision)
                    else -> SkyBlockDataRepository.markUpdateCurrent()
                }
            }
        }
    }

    private fun validateAndStore(downloaded: DownloadedCatalog): CachedCatalog {
        val compactSnapshot = SkyBlockDataLoader.loadJson(
            downloaded.items,
            downloaded.recipes,
            downloaded.wiki,
            downloaded.mobs,
            downloaded.pets,
        )
        val snapshot = SkyBlockRepoArchive.apply(compactSnapshot, downloaded.repoArchive)
        val directory = cacheDirectory.resolve(downloaded.revision)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.ITEMS), downloaded.items)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.RECIPES), downloaded.recipes)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.WIKI), downloaded.wiki)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.MOBS), downloaded.mobs)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.PETS), downloaded.pets)
        SkysoftConfigFiles.writeStringSafely(
            directory.resolve(REPO_ARCHIVE_FILE),
            Base64.getEncoder().encodeToString(downloaded.repoArchive),
        )
        SkysoftConfigFiles.writeStringSafely(activeRevisionFile, downloaded.revision)
        return CachedCatalog(downloaded.revision, snapshot)
    }

    private fun parseRevision(json: String): String {
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.getAsJsonObject(DATA_VERSION) ?: error("Item List update has no $DATA_VERSION data")
        return listOf("items", "recipes", "id_overlays", "mobs", "pets").joinToString("-") { name ->
            val sha = version.get(name)?.asString.orEmpty()
            require(sha.matches(shaPattern)) { "Item List update has an invalid $name revision" }
            sha.take(REVISION_PART_LENGTH)
        }
    }

    private fun parseRepoRevision(json: String): String {
        val sha = JsonParser.parseString(json).asJsonObject.get("sha")?.asString.orEmpty()
        require(sha.matches(shaPattern)) { "SkyblockRepo update has an invalid revision" }
        return sha.take(REVISION_PART_LENGTH)
    }

    private fun activeRevision(): String =
        if (Files.isRegularFile(activeRevisionFile)) Files.readString(activeRevisionFile).trim() else ""

    private fun isCheckDue(): Boolean {
        if (!Files.isRegularFile(lastCheckFile)) return true
        val lastCheck = Files.readString(lastCheckFile).trim().toLongOrNull() ?: return true
        return System.currentTimeMillis() - lastCheck >= UPDATE_INTERVAL.toMillis()
    }

    private fun recordCheckAttempt() {
        runCatching { SkysoftConfigFiles.writeStringSafely(lastCheckFile, System.currentTimeMillis().toString()) }
            .onFailure { SkysoftMod.LOGGER.warn("Could not record Skysoft Item List update check", it) }
    }

    data class CachedCatalog(val revision: String, val snapshot: SkyBlockDataSnapshot)

    private data class DownloadedCatalog(
        val revision: String,
        val items: String,
        val recipes: String,
        val wiki: String,
        val mobs: String,
        val pets: String,
        val repoArchive: ByteArray,
    )

    private const val DATA_VERSION = "1_21_5"
    private const val DATA_BASE = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/$DATA_VERSION"
    private const val SHAS_URL = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/shas.json"
    private const val REPO_ARCHIVE_FILE = "skyblock-repo.zip.b64"
    private const val ACTIVE_REVISION_FILE = "active-revision.txt"
    private const val REPO_COMMIT_URL = "https://api.github.com/repos/SkyblockRepo/Repo/commits/main"
    private const val REVISION_PART_LENGTH = 12
    private val UPDATE_INTERVAL = Duration.ofHours(24)
    private val REQUEST_TIMEOUT = Duration.ofSeconds(15)
    private val DOWNLOAD_TIMEOUT = Duration.ofSeconds(90)
    private val shaPattern = Regex("[a-f0-9]{40}")
    private val revisionPattern = Regex("[a-f0-9]{12}(?:-[a-f0-9]{12}){5}")
}

private object CatalogFiles {
    const val ITEMS = "items.min.json"
    const val RECIPES = "recipes.min.json"
    const val WIKI = "id_overlays.min.json"
    const val MOBS = "mobs.min.json"
    const val PETS = "pets.min.json"
}

internal fun skyBlockRepoArchiveUrl(revision: String): String =
    "https://codeload.github.com/SkyblockRepo/Repo/zip/$revision"
