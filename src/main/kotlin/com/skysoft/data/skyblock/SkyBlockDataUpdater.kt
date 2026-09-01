package com.skysoft.data.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.CancellableRequestGroup
import com.skysoft.utils.net.SkysoftHttp
import com.skysoft.utils.net.isCancellationFailure
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft

internal object SkyBlockDataUpdater {
    private val cacheDirectory by lazy { SkysoftConfigFiles.directory.resolve("item-list-data") }
    private val activeRevisionFile by lazy { cacheDirectory.resolve(ACTIVE_REVISION_FILE) }
    private val lastCheckFile by lazy { cacheDirectory.resolve("last-check.txt") }
    private val requestSlot = AsyncRequestSlot<CachedCatalog?>(completionExecutor = Minecraft.getInstance())

    fun loadCached(cacheRoot: Path = cacheDirectory): CachedCatalog? {
        val result = runCatching {
            val revision = readActiveRevision(cacheRoot) ?: return@runCatching null
            val directory = cacheRoot.resolve(revision)
            val items = Files.readString(directory.resolve(CatalogFiles.ITEMS))
            val recipes = Files.readString(directory.resolve(CatalogFiles.RECIPES))
            val wiki = Files.readString(directory.resolve(CatalogFiles.WIKI))
            val mobs = Files.readString(directory.resolve(CatalogFiles.MOBS))
            val npcs = Files.readString(directory.resolve(CatalogFiles.NPCS))
            val pets = Files.readString(directory.resolve(CatalogFiles.PETS))
            val snapshot = SkyBlockDataLoader.loadJson(items, recipes, wiki, mobs, npcs, pets)
            CachedCatalog(revision, snapshot)
        }
        result.onFailure { error ->
            SkysoftMod.LOGGER.warn("Skysoft Item List cached data is invalid", error)
            runCatching { Files.deleteIfExists(cacheRoot.resolve(ACTIVE_REVISION_FILE)) }
                .onFailure { SkysoftMod.LOGGER.warn("Could not invalidate Skysoft Item List cached data", it) }
        }
        return result.getOrNull()
    }

    fun check(force: Boolean = false) {
        if (requestSlot.isPending) return
        if (!force && !isCheckDue()) return
        recordCheckAttempt()
        SkyBlockDataRepository.markUpdateChecking()
        val requests = CancellableRequestGroup()
        val operation = requests.track(SkysoftHttp.getString(SHAS_URL, REQUEST_TIMEOUT))
            .thenApply(::parseRevision)
            .thenCompose { revision ->
                if (revision == readActiveRevision(cacheDirectory)) {
                    return@thenCompose CompletableFuture.completedFuture<DownloadedCatalog?>(null)
                }
                val items = requests.track(
                    SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.ITEMS}", DOWNLOAD_TIMEOUT),
                )
                val recipes = requests.track(
                    SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.RECIPES}", DOWNLOAD_TIMEOUT),
                )
                val wiki = requests.track(
                    SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.WIKI}", DOWNLOAD_TIMEOUT),
                )
                val entities = requests.track(
                    SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.MOBS}", DOWNLOAD_TIMEOUT),
                )
                val pets = requests.track(
                    SkysoftHttp.getString("$DATA_BASE/${CatalogFiles.PETS}", DOWNLOAD_TIMEOUT),
                )
                CompletableFuture.allOf(items, recipes, wiki, entities, pets).thenApply {
                    val (mobs, npcs) = splitEntities(entities.join())
                    DownloadedCatalog(
                        revision,
                        items.join(),
                        recipes.join(),
                        wiki.join(),
                        mobs,
                        npcs,
                        pets.join(),
                    )
                }
            }
            .thenApply { downloaded -> downloaded?.let(::validateAndStore) }
        requestSlot.startIfIdle(
            requestFactory = { requests.result(operation) },
        ) { cached, error ->
            SkysoftErrorBoundary.run("Item List update async completion") {
                if (error?.isCancellationFailure() == true) return@run
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

    fun cancel() {
        requestSlot.cancel()
    }

    private fun validateAndStore(downloaded: DownloadedCatalog): CachedCatalog {
        val compactSnapshot = SkyBlockDataLoader.loadJson(
            downloaded.items,
            downloaded.recipes,
            downloaded.wiki,
            downloaded.mobs,
            downloaded.npcs,
            downloaded.pets,
        )
        val directory = cacheDirectory.resolve(downloaded.revision)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.ITEMS), downloaded.items)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.RECIPES), downloaded.recipes)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.WIKI), downloaded.wiki)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.MOBS), downloaded.mobs)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.NPCS), downloaded.npcs)
        SkysoftConfigFiles.writeStringSafely(directory.resolve(CatalogFiles.PETS), downloaded.pets)
        SkysoftConfigFiles.writeStringSafely(activeRevisionFile, downloaded.revision)
        runCatching { pruneInactiveRevisions(cacheDirectory, downloaded.revision) }
            .onFailure { SkysoftMod.LOGGER.warn("Could not prune old Skysoft Item List data", it) }
        return CachedCatalog(downloaded.revision, compactSnapshot)
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

    private fun readActiveRevision(cacheRoot: Path): String? {
        val activeFile = cacheRoot.resolve(ACTIVE_REVISION_FILE)
        if (!Files.isRegularFile(activeFile)) return null
        val revision = Files.readString(activeFile).trim()
        if (!revision.matches(revisionPattern)) return null
        val directory = cacheRoot.resolve(revision)
        return revision.takeIf { CatalogFiles.required.all { Files.isRegularFile(directory.resolve(it)) } }
    }

    private fun splitEntities(json: String): Pair<String, String> {
        val entities = JsonParser.parseString(json).asJsonObject
        val mobs = JsonObject()
        val npcs = JsonObject()
        entities.entrySet().forEach { (id, entity) ->
            val type = entity.asJsonObject.get("type")?.asString.orEmpty()
            (if (type.isNpcEntityType()) npcs else mobs).add(id, entity)
        }
        return mobs.toString() to npcs.toString()
    }

    private fun isCheckDue(): Boolean = runCatching {
        if (!Files.isRegularFile(lastCheckFile)) return@runCatching true
        val lastCheck = Files.readString(lastCheckFile).trim().toLongOrNull() ?: return@runCatching true
        val now = System.currentTimeMillis()
        lastCheck > now || now - lastCheck >= UPDATE_INTERVAL.toMillis()
    }.onFailure {
        SkysoftMod.LOGGER.warn("Could not read the Skysoft Item List update schedule", it)
    }.getOrDefault(true)

    private fun recordCheckAttempt() {
        runCatching { SkysoftConfigFiles.writeStringSafely(lastCheckFile, System.currentTimeMillis().toString()) }
            .onFailure { SkysoftMod.LOGGER.warn("Could not record Skysoft Item List update check", it) }
    }

    internal fun pruneInactiveRevisions(cacheRoot: Path, activeRevision: String) {
        val staleDirectories = Files.list(cacheRoot).use { paths ->
            paths.filter { path ->
                Files.isDirectory(path) && path.fileName.toString().matches(revisionPattern) &&
                    path.fileName.toString() != activeRevision
            }.toList()
        }
        staleDirectories.forEach(::deleteDirectory)
    }

    private fun deleteDirectory(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    data class CachedCatalog(val revision: String, val snapshot: SkyBlockDataSnapshot)

    private data class DownloadedCatalog(
        val revision: String,
        val items: String,
        val recipes: String,
        val wiki: String,
        val mobs: String,
        val npcs: String,
        val pets: String,
    )

    private const val DATA_VERSION = "1_21_5"
    private const val DATA_BASE = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/$DATA_VERSION"
    private const val SHAS_URL = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/shas.json"
    private const val ACTIVE_REVISION_FILE = "active-revision.txt"
    private const val REVISION_PART_LENGTH = 12
    private val UPDATE_INTERVAL = Duration.ofHours(24)
    private val REQUEST_TIMEOUT = Duration.ofSeconds(15)
    private val DOWNLOAD_TIMEOUT = Duration.ofSeconds(90)
    private val shaPattern = Regex("[a-f0-9]{40}")
    private val revisionPattern = Regex("[a-f0-9]{12}(?:-[a-f0-9]{12}){4}")
}

private object CatalogFiles {
    val required = listOf(ITEMS, RECIPES, WIKI, MOBS, NPCS, PETS)
    const val ITEMS = "items.min.json"
    const val RECIPES = "recipes.min.json"
    const val WIKI = "id_overlays.min.json"
    const val MOBS = "mobs.min.json"
    const val NPCS = "npcs.json"
    const val PETS = "pets.min.json"
}
