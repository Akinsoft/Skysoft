package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import kotlin.time.Duration.Companion.seconds

object ActivePetTracker {
    private val storage get() = ProfileStorageApi.storage
    private val changeListeners = mutableListOf<PetChangeListener>()
    private val lastAssertion = mutableMapOf<PetDataAssertionSource, ElapsedTimeMark>()
    private var currentPetSnapshot: StoredPetData? = null
    private var pendingTabPetData: StoredPetData? = null
    private var ticks = 0

    private val chatSummonPattern = Regex("""§aYou summoned your §r§(?<rarity>.)(?<pet>[^§]+)(?:§r(?<skin>§. ✦))?§r§a!""")

    val currentPet: StoredPetData?
        get() {
            val storedPet = storage.currentPetUuid?.let { uuid ->
                storage.pets.firstOrNull { it.uuid == uuid }
            }
            val snapshot = currentPetSnapshot
            return when {
                snapshot?.uuid == null -> snapshot ?: storedPet
                else -> storedPet ?: snapshot
            }
        }

    fun register() {
        SkyBlockProfileApi.onProfileChange("Active Pet profile reset") {
            resetProfileState()
        }
        SkysoftClientEvents.onEndTick("Active Pet tab assertion") {
            if (++ticks % TAB_ASSERTION_INTERVAL_TICKS != 0) return@onEndTick
            val petData = pendingTabPetData ?: return@onEndTick
            if (shouldDelayTabAssertion()) return@onEndTick
            pendingTabPetData = null
            assertFoundCurrentData(petData, PetDataAssertionSource.TAB)
        }
    }

    fun onChange(boundary: String, listener: (StoredPetData?) -> Unit) {
        changeListeners += PetChangeListener(boundary, listener)
    }

    fun assertFoundCurrentData(petData: StoredPetData, source: PetDataAssertionSource) {
        if (source == PetDataAssertionSource.TAB && shouldDelayTabAssertion()) {
            pendingTabPetData = petData
            return
        }
        pendingTabPetData = null
        lastAssertion[source] = ElapsedTimeMark.now()

        val mergedPetData = petData.withStoredDataWhenMissing(source)
        currentPetSnapshot = mergedPetData
        storage.currentPetUuid = mergedPetData.uuid
        mergedPetData.uuid?.let { uuid ->
            storage.pets.addOrReplace(mergedPetData) { it.uuid == uuid }
        }
        ProfileStorageApi.markDirty()
        notifyChange(mergedPetData)
    }

    fun updateCurrentPetExp(exp: Double): StoredPetData? {
        val currentPet = currentPet ?: return null
        val currentExp = currentPet.exp ?: 0.0
        if (exp <= currentExp) return null

        currentPet.exp = exp
        currentPetSnapshot = currentPet
        currentPet.uuid?.let { uuid ->
            storage.pets.addOrReplace(currentPet) { it.uuid == uuid }
        }
        ProfileStorageApi.markDirty()
        notifyChange(currentPet)
        return currentPet
    }

    fun clearCurrentPet() {
        val hadStoredCurrentPet = storage.currentPetUuid != null
        resetProfileState()
        storage.currentPetUuid = null
        if (hadStoredCurrentPet) ProfileStorageApi.markDirty()
    }

    private fun resetProfileState() {
        currentPetSnapshot = null
        pendingTabPetData = null
        notifyChange(null)
    }

    private fun notifyChange(petData: StoredPetData?) {
        changeListeners.forEach { listener ->
            SkysoftErrorBoundary.run(listener.boundary) { listener.callback(petData) }
        }
    }

    fun handleChat(message: String) {
        val match = chatSummonPattern.matchEntire(message) ?: return
        val resolvedPet = petFromSummonMessage(match) ?: return
        assertFoundCurrentData(resolvedPet, PetDataAssertionSource.CHAT)
    }

    private fun petFromSummonMessage(match: MatchResult): StoredPetData? {
        val petName = match.groups["pet"]?.value ?: return null
        val rarityCode = match.groups["rarity"]?.value?.firstOrNull() ?: return null
        val rarity = SkyBlockRarity.getByColorCode(rarityCode) ?: return null
        val skinTag = match.groups["skin"]?.value?.replace(" ", "")
        return PetStorageService.resolvePetDataOrNull(
            name = petName,
            rarity = rarity,
            skinTag = skinTag,
            skinTagKnown = true,
        )
    }

    private fun shouldDelayTabAssertion(): Boolean =
        TAB_DELAY_SOURCES.any(::isAssertionRecent)

    private fun isAssertionRecent(source: PetDataAssertionSource): Boolean {
        val assertedAt = lastAssertion[source] ?: return false
        return assertedAt.passedSince() <= 5.seconds
    }

    private fun StoredPetData.withStoredDataWhenMissing(source: PetDataAssertionSource): StoredPetData {
        if (source != PetDataAssertionSource.TAB) return this
        val storedPet = uuid?.let { petUuid -> storage.pets.firstOrNull { it.uuid == petUuid } } ?: return this
        return copy(
            skinInternalName = skinInternalName ?: storedPet.skinInternalName,
            heldItemInternalName = heldItemInternalName ?: storedPet.heldItemInternalName,
            exp = exp ?: storedPet.exp,
            displayIconTexture = displayIconTexture ?: storedPet.displayIconTexture,
        )
    }

    enum class PetDataAssertionSource {
        CHAT,
        TAB,
        AUTOPET,
        MENU,
    }

    private const val TAB_ASSERTION_INTERVAL_TICKS = 20
}

private data class PetChangeListener(
    val boundary: String,
    val callback: (StoredPetData?) -> Unit,
)

private val TAB_DELAY_SOURCES = setOf(
    ActivePetTracker.PetDataAssertionSource.AUTOPET,
    ActivePetTracker.PetDataAssertionSource.MENU,
)
