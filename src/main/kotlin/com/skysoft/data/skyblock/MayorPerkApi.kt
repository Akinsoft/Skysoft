package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.net.PeriodicRefresh

object MayorPerkApi {
    private const val ELECTION_URL = "https://api.hypixel.net/v2/resources/skyblock/election"
    private const val SHARING_IS_CARING = "Sharing is Caring"
    private const val PET_XP_BUFF = "Pet XP Buff"
    private const val MYTHOLOGICAL_RITUAL = "Mythological Ritual"
    private const val CHIVALROUS_CARNIVAL = "Chivalrous Carnival"
    private const val FISHING_FESTIVAL = "Fishing Festival"
    private const val MINING_FIESTA = "Mining Fiesta"
    private const val GRAND_FEAST = "Grand Feast"
    private const val REFRESH_CHECK_INTERVAL_TICKS = 40
    private const val REFRESH_INTERVAL_MILLIS = 20L * 60L * 1_000L
    private const val FAILURE_RETRY_MILLIS = 60_000L

    private val gson = Gson()
    private val consumers = ActiveConsumerRegistry()
    private val election = PeriodicRefresh(
        name = "Mayor Perk",
        description = "mayor perks",
        url = ELECTION_URL,
        refreshIntervalMillis = REFRESH_INTERVAL_MILLIS,
        failureRetryMillis = FAILURE_RETRY_MILLIS,
        parse = { gson.fromJson(it, ElectionResponse::class.java) },
        onSuccess = { currentPerks = it.perks() },
    )
    private var ticks = 0

    @Volatile
    internal var currentPerks = MayorPerks()
        private set

    val currentMinister: CurrentMinister? get() = currentPerks.currentMinister
    val sharingIsCaringActive: Boolean get() = currentPerks.sharingIsCaringActive
    val petXpBuffActive: Boolean get() = currentPerks.petXpBuffActive
    val mythologicalRitualActive: Boolean get() = currentPerks.mythologicalRitualActive
    val grandFeastActive: Boolean get() = currentPerks.grandFeastActive
    val mythologicalRitualEventKey: String? get() = currentPerks.mythologicalRitualEventKey

    fun register() {
        consumers.onEndTick(
            "Mayor Perk refresh",
            onDeactivated = ::reset,
            onActivated = election::refresh,
        ) {
            if (++ticks % REFRESH_CHECK_INTERVAL_TICKS == 0) election.refreshIfDue()
        }
        election.register()
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    private fun reset() {
        election.reset()
        ticks = 0
        currentPerks = MayorPerks()
    }

    private fun ElectionResponse.perks(): MayorPerks = MayorPerks(
        currentMinister = currentMinister(),
        sharingIsCaringActive = hasPerk(SHARING_IS_CARING),
        petXpBuffActive = hasPerk(PET_XP_BUFF),
        mythologicalRitualActive = hasPerk(MYTHOLOGICAL_RITUAL),
        carnivalActive = hasPerk(CHIVALROUS_CARNIVAL),
        fishingFestivalActive = hasPerk(FISHING_FESTIVAL),
        miningFiestaActive = hasPerk(MINING_FIESTA),
        grandFeastActive = hasPerk(GRAND_FEAST),
        mythologicalRitualEventKey = mythologicalRitualEventKey(),
    )

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
                "year-$electedYear:mayor:${stableName(mayorEntry.key, mayorEntry.name)}"
            minister?.perk?.name == MYTHOLOGICAL_RITUAL ->
                "year-$electedYear:minister:${stableName(minister.key, minister.name)}"
            else -> null
        }
    }

    private fun stableName(key: String?, name: String?): String =
        key?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "unknown"

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

internal data class MayorPerks(
    val currentMinister: CurrentMinister? = null,
    val sharingIsCaringActive: Boolean = false,
    val petXpBuffActive: Boolean = false,
    val mythologicalRitualActive: Boolean = false,
    val carnivalActive: Boolean = false,
    val fishingFestivalActive: Boolean = false,
    val miningFiestaActive: Boolean = false,
    val grandFeastActive: Boolean = false,
    val mythologicalRitualEventKey: String? = null,
)

data class CurrentMinister(
    val name: String,
    val perk: CurrentMinisterPerk,
)

data class CurrentMinisterPerk(
    val name: String,
    val description: String,
)
