package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import kotlin.time.Duration.Companion.seconds

object ActivePetTracker {
    private val storage get() = ProfileStorageApi.storage
    private val changeListeners = ActiveListenerRegistry<(StoredPetData?) -> Unit>()
    private val lastAssertion = mutableMapOf<PetDataAssertionSource, ElapsedTimeMark>()
    private var currentPetSnapshot: StoredPetData? = null
    private var pendingTabPetData: PendingTabPetData? = null
    private var ticks = 0

    private val chatSummonPattern = Regex("""§aYou summoned your §r§(?<rarity>.)(?<pet>[^§]+)(?:§r(?<skin>§. ✦))?§r§a!""")

    val currentPet: StoredPetData?
        get() {
            val storedPet = storage.currentPetUuid?.let(PetStorageService::storedPet)
            val snapshot = currentPetSnapshot
            return when {
                snapshot?.uuid == null -> snapshot ?: storedPet
                else -> storedPet ?: snapshot
            }
        }

    fun register() {
        SkyBlockProfileApi.onProfileChange("Active Pet profile reset", PetFeatureDemand::isActive) {
            resetProfileState()
        }
        SkysoftClientEvents.onEndTick(
            "Active Pet tab assertion",
            isActive = PetFeatureDemand::isActive,
        ) {
            if (++ticks % TAB_ASSERTION_INTERVAL_TICKS != 0) return@onEndTick
            val pending = pendingTabPetData ?: return@onEndTick
            if (shouldDelayTabAssertion()) return@onEndTick
            val trackedExp = pending.petData.trackedPet()?.exp
            val petData = if (trackedExp != pending.trackedExp) pending.petData.copy(exp = trackedExp) else pending.petData
            pendingTabPetData = null
            assertFoundCurrentData(petData, PetDataAssertionSource.TAB)
        }
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (StoredPetData?) -> Unit,
    ) {
        changeListeners.register(boundary, isActive, listener)
    }

    fun assertFoundCurrentData(petData: StoredPetData, source: PetDataAssertionSource) {
        if (source == PetDataAssertionSource.TAB && shouldDelayTabAssertion()) {
            pendingTabPetData = PendingTabPetData(petData, petData.trackedPet()?.exp)
            return
        }
        pendingTabPetData = null
        lastAssertion[source] = ElapsedTimeMark.now()

        val mergedPetData = petData.withStoredDataWhenMissing(source)
        currentPetSnapshot = mergedPetData
        ProfileStorageApi.updateProfile { profile ->
            profile.currentPetUuid = mergedPetData.uuid
            mergedPetData.uuid?.let { uuid ->
                profile.pets.addOrReplace(mergedPetData) { it.uuid == uuid }
            }
        }
        notifyChange(mergedPetData)
    }

    fun updateCurrentPetExp(exp: Double) {
        val currentPet = currentPet ?: return
        if (exp <= (currentPet.exp ?: 0.0)) return

        val updatedPet = currentPet.copy(exp = exp)
        currentPetSnapshot = updatedPet
        PetStorageService.storePet(updatedPet)
        notifyChange(updatedPet)
    }

    fun clearCurrentPet() {
        val hadStoredCurrentPet = storage.currentPetUuid != null
        resetProfileState()
        if (hadStoredCurrentPet) ProfileStorageApi.updateProfile { it.currentPetUuid = null }
    }

    fun assertNoCurrentPetFromTab() {
        if (currentPet == null || shouldDelayTabAssertion()) return
        clearCurrentPet()
    }

    private fun resetProfileState() {
        currentPetSnapshot = null
        pendingTabPetData = null
        notifyChange(null)
    }

    private fun notifyChange(petData: StoredPetData?) {
        changeListeners.forEachActive { listener -> listener(petData) }
    }

    fun handleChat(message: String) {
        val match = chatSummonPattern.matchEntire(message) ?: return
        val resolvedPet = PetStorageService.resolvePetDataOrNull(
            name = match.group("pet"),
            rarity = PetStoragePetItems.rarityOrNull(match) ?: return,
            skinTag = PetStoragePetItems.skinTagOrNull(match),
            skinTagKnown = true,
        ) ?: return
        assertFoundCurrentData(resolvedPet, PetDataAssertionSource.CHAT)
    }

    private fun shouldDelayTabAssertion(): Boolean =
        TAB_DELAY_SOURCES.any(::isAssertionRecent)

    private fun isAssertionRecent(source: PetDataAssertionSource): Boolean {
        val assertedAt = lastAssertion[source] ?: return false
        return assertedAt.passedSince() <= 5.seconds
    }

    private fun StoredPetData.withStoredDataWhenMissing(source: PetDataAssertionSource): StoredPetData {
        if (source != PetDataAssertionSource.TAB) return this
        val storedPet = uuid?.let(PetStorageService::storedPet) ?: return this
        return copy(
            skinInternalName = skinInternalName ?: storedPet.skinInternalName,
            heldItemInternalName = heldItemInternalName ?: storedPet.heldItemInternalName,
            exp = exp ?: storedPet.exp,
            displayIconTexture = displayIconTexture ?: storedPet.displayIconTexture,
        )
    }

    private fun StoredPetData.trackedPet(): StoredPetData? {
        val petUuid = uuid ?: return currentPet?.takeIf { it.uuid == null && it.petInternalName == petInternalName }
        return PetStorageService.storedPet(petUuid)
    }

    private data class PendingTabPetData(val petData: StoredPetData, val trackedExp: Double?)

    enum class PetDataAssertionSource {
        CHAT,
        TAB,
        AUTOPET,
        MENU,
    }

    private const val TAB_ASSERTION_INTERVAL_TICKS = 20
}

private val TAB_DELAY_SOURCES = setOf(
    ActivePetTracker.PetDataAssertionSource.AUTOPET,
    ActivePetTracker.PetDataAssertionSource.MENU,
)
