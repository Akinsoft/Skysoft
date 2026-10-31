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
import net.minecraft.util.Util

internal object SkyBlockDataUpdater {
    private val cacheDirectory by lazy { SkysoftConfigFiles.directory.resolve("item-list-data") }
    private val activeRevisionFile by lazy { cacheDirectory.resolve("active-revision.txt") }
    private val lastCheckFile by lazy { cacheDirectory.resolve("last-check.txt") }
    private val cacheLock = Any()
    private val requestSlot = AsyncRequestSlot<CheckResult>(completionExecutor = Minecraft.getInstance())

    fun loadCached(): CachedCatalog? = synchronized(cacheLock) {
        val result = runCatching {
            val revision = readActiveRevision() ?: return@runCatching null
            val directory = cacheDirectory.resolve(revision)
            val files = CatalogFiles.required.associateWith { Files.readString(directory.resolve(it)) }
            CachedCatalog(revision, loadSnapshot(files))
        }
        result.onFailure { error ->
            SkysoftMod.LOGGER.warn("Skysoft Item List cached data is invalid", error)
            runCatching { Files.deleteIfExists(activeRevisionFile) }
                .onFailure { SkysoftMod.LOGGER.warn("Could not invalidate Skysoft Item List cached data", it) }
        }
        result.getOrNull()
    }

    fun check(force: Boolean = false) {
        requestSlot.startIfIdle(
            requestFactory = {
                val requests = CancellableRequestGroup()
                val scheduled = requests.track(
                    CompletableFuture.supplyAsync({
                        (force || isCheckDue()).also { due -> if (due) recordCheckAttempt() }
                    }, Util.ioPool()),
                )
                val operation = scheduled.thenComposeAsync({ due ->
                    if (!due) {
                        CompletableFuture.completedFuture<CheckResult>(CheckResult.Skipped)
                    } else {
                        SkyBlockDataRepository.markUpdateChecking()
                        requests.track(download(requests)).thenApply { cached ->
                            if (cached == null) CheckResult.Current else CheckResult.Updated(cached)
                        }
                    }
                }, Minecraft.getInstance())
                requests.result(operation)
            },
        ) { result, error ->
            SkysoftErrorBoundary.run("Item List update async completion") {
                if (error?.isCancellationFailure() == true) return@run
                if (error != null) {
                    SkyBlockDataRepository.markUpdateFailed(error.cause?.message ?: error.message ?: "Update failed")
                    SkysoftMod.LOGGER.warn("Skysoft Item List update failed", error)
                    return@run
                }
                when (val completed = checkNotNull(result)) {
                    is CheckResult.Updated -> SkyBlockDataRepository.applyUpdated(
                        completed.catalog.snapshot,
                        completed.catalog.revision,
                    )
                    CheckResult.Current -> SkyBlockDataRepository.markUpdateCurrent()
                    CheckResult.Skipped -> Unit
                }
            }
        }
    }

    private fun download(requests: CancellableRequestGroup): CompletableFuture<CachedCatalog?> {
        return requests.track(SkysoftHttp.getString(SHAS_URL, REQUEST_TIMEOUT))
            .thenApply(::parseRevision)
            .thenComposeAsync({ revision ->
                if (revision == readActiveRevision()) {
                    return@thenComposeAsync CompletableFuture.completedFuture<DownloadedCatalog?>(null)
                }
                val downloads = CatalogFiles.downloaded.associateWith { name ->
                    requests.track(SkysoftHttp.getString("$DATA_BASE/$name", DOWNLOAD_TIMEOUT))
                }
                CompletableFuture.allOf(*downloads.values.toTypedArray()).thenApply {
                    val (mobs, npcs) = splitEntities(downloads.getValue(CatalogFiles.MOBS).join())
                    val files = CatalogFiles.required.associateWith { name ->
                        when (name) {
                            CatalogFiles.MOBS -> mobs
                            CatalogFiles.NPCS -> npcs
                            else -> downloads.getValue(name).join()
                        }
                    }
                    DownloadedCatalog(revision, files)
                }
            }, Util.ioPool())
            .thenApplyAsync({ downloaded -> downloaded?.let(::validateAndStore) }, Util.ioPool())
    }

    fun cancel() {
        requestSlot.cancel()
    }

    private fun validateAndStore(downloaded: DownloadedCatalog): CachedCatalog {
        val compactSnapshot = loadSnapshot(downloaded.files)
        synchronized(cacheLock) {
            val directory = cacheDirectory.resolve(downloaded.revision)
            downloaded.files.forEach { (name, json) -> SkysoftConfigFiles.writeStringSafely(directory.resolve(name), json) }
            SkysoftConfigFiles.writeStringSafely(activeRevisionFile, downloaded.revision)
            runCatching { pruneInactiveRevisions(downloaded.revision) }
                .onFailure { SkysoftMod.LOGGER.warn("Could not prune old Skysoft Item List data", it) }
        }
        return CachedCatalog(downloaded.revision, compactSnapshot)
    }

    private fun loadSnapshot(files: Map<String, String>): SkyBlockDataSnapshot = SkyBlockDataLoader.loadJson(
        itemsJson = files.getValue(CatalogFiles.ITEMS),
        recipesJson = files.getValue(CatalogFiles.RECIPES),
        wikiJson = files.getValue(CatalogFiles.WIKI),
        mobsJson = files.getValue(CatalogFiles.MOBS),
        npcsJson = files.getValue(CatalogFiles.NPCS),
        petsJson = files.getValue(CatalogFiles.PETS),
    )

    private fun parseRevision(json: String): String {
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.getAsJsonObject(DATA_VERSION) ?: error("Item List update has no $DATA_VERSION data")
        return listOf("items", "recipes", "id_overlays", "mobs", "pets").joinToString("-") { name ->
            val sha = version.get(name)?.asString.orEmpty()
            require(sha.matches(shaPattern)) { "Item List update has an invalid $name revision" }
            sha.take(REVISION_PART_LENGTH)
        }
    }

    private fun readActiveRevision(): String? = synchronized(cacheLock) {
        if (!Files.isRegularFile(activeRevisionFile)) return@synchronized null
        val revision = Files.readString(activeRevisionFile).trim()
        if (!revision.matches(revisionPattern)) return@synchronized null
        val directory = cacheDirectory.resolve(revision)
        revision.takeIf { CatalogFiles.required.all { Files.isRegularFile(directory.resolve(it)) } }
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

    private fun pruneInactiveRevisions(activeRevision: String) {
        val staleDirectories = Files.list(cacheDirectory).use { paths ->
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

    private sealed interface CheckResult {
        data object Skipped : CheckResult
        data object Current : CheckResult
        data class Updated(val catalog: CachedCatalog) : CheckResult
    }

    private data class DownloadedCatalog(val revision: String, val files: Map<String, String>)

    private const val DATA_VERSION = "1_21_5"
    private const val DATA_BASE = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/$DATA_VERSION"
    private const val SHAS_URL = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/shas.json"
    private const val REVISION_PART_LENGTH = 12
    private val UPDATE_INTERVAL = Duration.ofHours(24)
    private val REQUEST_TIMEOUT = Duration.ofSeconds(15)
    private val DOWNLOAD_TIMEOUT = Duration.ofSeconds(90)
    private val shaPattern = Regex("[a-f0-9]{40}")
    private val revisionPattern = Regex("[a-f0-9]{12}(?:-[a-f0-9]{12}){4}")
}

private object CatalogFiles {
    val required = listOf(ITEMS, RECIPES, WIKI, MOBS, NPCS, PETS)
    val downloaded = required - NPCS
    const val ITEMS = "items.min.json"
    const val RECIPES = "recipes.min.json"
    const val WIKI = "id_overlays.min.json"
    const val MOBS = "mobs.min.json"
    const val NPCS = "npcs.json"
    const val PETS = "pets.min.json"
}
