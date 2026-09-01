package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.SkysoftMod
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.PendingHttpRequests
import com.skysoft.utils.net.RefreshSchedule
import com.skysoft.utils.net.isCancellationFailure
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary

object MayorPerkApi {
    private const val ELECTION_URL = "https://api.hypixel.net/v2/resources/skyblock/election"
    private const val SHARING_IS_CARING = "Sharing is Caring"
    private const val PET_XP_BUFF = "Pet XP Buff"
    private const val MYTHOLOGICAL_RITUAL = "Mythological Ritual"
    private const val CHIVALROUS_CARNIVAL = "Chivalrous Carnival"
    private const val FISHING_FESTIVAL = "Fishing Festival"
    private const val MINING_FIESTA = "Mining Fiesta"
    private const val REFRESH_CHECK_INTERVAL_TICKS = 40

    private val gson = Gson()
    private val consumers = ActiveConsumerRegistry()
    private val requests = PendingHttpRequests()
    private val requestSlot = AsyncRequestSlot<ElectionResponse>()
    private val refreshSchedule = RefreshSchedule()
    private var ticks = 0

    @Volatile
    var currentMinister: CurrentMinister? = null
        private set

    @Volatile
    var sharingIsCaringActive: Boolean = false
        private set

    @Volatile
    var petXpBuffActive: Boolean = false
        private set

    @Volatile
    var mythologicalRitualActive: Boolean = false
        private set

    @Volatile
    var carnivalActive: Boolean = false
        private set

    @Volatile
    var fishingFestivalActive: Boolean = false
        private set

    @Volatile
    var miningFiestaActive: Boolean = false
        private set

    @Volatile
    var mythologicalRitualEventKey: String? = null
        private set

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Mayor Perk refresh",
            isActive = { consumers.isActiveOrDeactivating },
        ) tick@{
            when (consumers.activity()) {
                ConsumerActivity.INACTIVE -> return@tick
                ConsumerActivity.DEACTIVATED -> {
                    reset()
                    return@tick
                }
                ConsumerActivity.ACTIVATED -> {
                    refresh()
                    return@tick
                }
                ConsumerActivity.ACTIVE -> Unit
            }
            if (++ticks % REFRESH_CHECK_INTERVAL_TICKS != 0) return@tick
            if (refreshSchedule.isDue(System.currentTimeMillis())) refresh()
        }
        SkysoftClientEvents.onClientStopping("Mayor Perk request cancellation") {
            requestSlot.cancel()
            requests.cancelAll()
        }
    }

    private fun refresh() {
        requestSlot.startIfIdle(
            requestFactory = {
                requests.getString(ELECTION_URL)
                    .thenApply { response -> gson.fromJson(response, ElectionResponse::class.java) }
            },
        ) { response, error ->
            SkysoftErrorBoundary.run("Mayor Perk async completion") {
                val now = System.currentTimeMillis()
                if (error == null && response != null) {
                    currentMinister = response.currentMinister()
                    sharingIsCaringActive = response.hasPerk(SHARING_IS_CARING)
                    petXpBuffActive = response.hasPerk(PET_XP_BUFF)
                    mythologicalRitualActive = response.hasPerk(MYTHOLOGICAL_RITUAL)
                    carnivalActive = response.hasPerk(CHIVALROUS_CARNIVAL)
                    fishingFestivalActive = response.hasPerk(FISHING_FESTIVAL)
                    miningFiestaActive = response.hasPerk(MINING_FIESTA)
                    mythologicalRitualEventKey = response.mythologicalRitualEventKey()
                    refreshSchedule.schedule(now, REFRESH_INTERVAL_MILLIS)
                } else if (error?.isCancellationFailure() != true) {
                    refreshSchedule.schedule(now, FAILURE_RETRY_MILLIS)
                    SkysoftMod.LOGGER.warn("Failed to refresh mayor perks", error)
                }
            }
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    private fun reset() {
        requestSlot.cancel()
        requests.cancelAll()
        refreshSchedule.reset()
        ticks = 0
        currentMinister = null
        sharingIsCaringActive = false
        petXpBuffActive = false
        mythologicalRitualActive = false
        carnivalActive = false
        fishingFestivalActive = false
        miningFiestaActive = false
        mythologicalRitualEventKey = null
    }

    private fun ElectionResponse.currentMinister(): CurrentMinister? {
        val minister = mayor?.minister ?: return null
        val perk = minister.perk ?: return null
        return CurrentMinister(
            name = minister.name?.takeIf { it.isNotBlank() } ?: return null,
            perk = CurrentMinisterPerk(
                name = perk.name?.takeIf { it.isNotBlank() } ?: return null,
                description = perk.description?.takeIf { it.isNotBlank() } ?: return null,
            ),
        )
    }

    private fun ElectionResponse.hasPerk(perkName: String): Boolean =
        mayor.hasPerk(perkName) || mayor?.minister?.perk?.name == perkName

    private fun MayorEntry?.hasPerk(perkName: String): Boolean =
        this?.perks?.any { it.name == perkName } == true

    private fun ElectionResponse.mythologicalRitualEventKey(): String? {
        val mayorEntry = mayor ?: return null
        val electedYear = mayorEntry.election?.year?.takeIf { year -> year > 0 } ?: return null
        val minister = mayorEntry.minister
        return when {
            mayorEntry.hasPerk(MYTHOLOGICAL_RITUAL) ->
                "year-$electedYear:mayor:${mayorEntry.stableName()}"
            minister?.perk?.name == MYTHOLOGICAL_RITUAL ->
                "year-$electedYear:minister:${minister.stableName()}"
            else -> null
        }
    }

    private fun MayorEntry.stableName(): String =
        key?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun Minister.stableName(): String =
        key?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "unknown"

    private const val REFRESH_INTERVAL_MILLIS = 20L * 60L * 1_000L
    private const val FAILURE_RETRY_MILLIS = 60_000L

    private data class ElectionResponse(
        val mayor: MayorEntry?,
    )

    private data class MayorEntry(
        val key: String?,
        val name: String?,
        val perks: List<MayorPerk>?,
        val minister: Minister?,
        val election: Election?,
    )

    private data class Minister(
        val key: String?,
        val name: String?,
        val perk: MayorPerk?,
    )

    private data class MayorPerk(
        val name: String?,
        val description: String?,
    )

    private data class Election(
        val year: Int?,
    )
}

data class CurrentMinister(
    val name: String,
    val perk: CurrentMinisterPerk,
)

data class CurrentMinisterPerk(
    val name: String,
    val description: String,
)
