package com.skysoft.features.bazaar

import com.skysoft.config.BazaarTrackerSound
import com.skysoft.data.ProfileStorageView
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

internal object BazaarTrackerAlerts {
    private var statusAlertTick = 0
    private val lastAlertStatuses = mutableMapOf<String, OrderStatus>()
    private val lastOutbidAlertMillis = mutableMapOf<String, Long>()

    fun reset() {
        statusAlertTick = 0
        lastAlertStatuses.clear()
        lastOutbidAlertMillis.clear()
    }

    fun forgetOrder(orderId: String) {
        lastAlertStatuses.remove(orderId)
        lastOutbidAlertMillis.remove(orderId)
    }

    fun tick() {
        if (statusAlertTick++ % STATUS_ALERT_INTERVAL_TICKS != 0) return
        val activeIds = storage.activeOrders.mapTo(mutableSetOf()) { it.id }
        lastAlertStatuses.keys.retainAll(activeIds)
        lastOutbidAlertMillis.keys.retainAll(activeIds)
        BazaarTrackingState.marketProofMillis.keys.retainAll(activeIds)

        val now = System.currentTimeMillis()
        for (order in storage.activeOrders) {
            val status = statusFor(order)
            val previous = lastAlertStatuses.put(order.id, status) ?: continue
            if (!status.isWarning || previous.isWarning) continue
            val lastAlert = lastOutbidAlertMillis[order.id] ?: 0L
            if (now - lastAlert < OUTBID_SOUND_COOLDOWN_MILLIS) continue
            lastOutbidAlertMillis[order.id] = now
            playAlertSound(BazaarTrackerSound.OUTBID_UNDERCUT)
        }
    }

    fun initializeOrderAlertState(order: ProfileStorageView.BazaarOrderData) {
        lastAlertStatuses[order.id] = statusFor(order)
    }

    fun playProgressAlert(order: ProfileStorageView.BazaarOrderData, previousFilledAmount: Long) {
        if (order.filledAmount <= previousFilledAmount) return
        if (order.amountOrdered > 0 && order.filledAmount >= order.maximumAmount()) {
            playAlertSound(BazaarTrackerSound.FILLED)
        } else if (order.filledAmount > order.claimedAmount) {
            playAlertSound(BazaarTrackerSound.PARTIAL)
        }
        lastAlertStatuses[order.id] = statusFor(order)
    }

    fun showEstimatedFillProgress(
        order: ProfileStorageView.BazaarOrderData,
        previousFilledAmount: Long,
        filledAmount: Long,
    ) {
        if (filledAmount <= previousFilledAmount) return
        markFillHighlight(order, filledAmount)
        if (isPartialFill(order, filledAmount)) {
            playAlertSound(BazaarTrackerSound.PARTIAL)
        }
        lastAlertStatuses[order.id] = statusFor(order)
    }

    private fun playAlertSound(sound: BazaarTrackerSound) {
        if (sound !in config.settings.sounds.get()) return
        val minecraft = Minecraft.getInstance()
        val instance = when (sound) {
            BazaarTrackerSound.FILLED ->
                SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_PLING.value(),
                    FILLED_SOUND_VOLUME,
                    FILLED_SOUND_PITCH,
                )
            BazaarTrackerSound.PARTIAL ->
                SimpleSoundInstance.forUI(
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    PARTIAL_SOUND_VOLUME,
                    PARTIAL_SOUND_PITCH,
                )
            BazaarTrackerSound.OUTBID_UNDERCUT ->
                SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    OUTBID_SOUND_VOLUME,
                    OUTBID_SOUND_PITCH,
                )
        }
        minecraft.soundManager.play(instance)
    }

    private const val STATUS_ALERT_INTERVAL_TICKS = 20
    private const val OUTBID_SOUND_COOLDOWN_MILLIS = 60_000L
    private const val FILLED_SOUND_VOLUME = 1.6f
    private const val FILLED_SOUND_PITCH = 0.8f
    private const val PARTIAL_SOUND_VOLUME = 1.25f
    private const val PARTIAL_SOUND_PITCH = 0.35f
    private const val OUTBID_SOUND_VOLUME = 0.65f
    private const val OUTBID_SOUND_PITCH = 0.7f
}
