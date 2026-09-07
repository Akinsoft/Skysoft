package com.skysoft.data.hypixel

import com.skysoft.data.ProfileStorageApi
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object SkyBlockCookieBuffApi {
    private val consumers = ActiveConsumerRegistry()
    private var ticks = 0

    var status = CookieBuffStatus(CookieBuffState.LOADING)
        private set

    fun register() {
        ProfileStorageApi.registerConsumer("Cookie Buff API") { consumers.hasActiveConsumers }
        TabListApi.onChange(
            "Cookie Buff API",
            isActive = { consumers.hasActiveConsumers },
            listener = { updateStatusFromTab() },
        )
        ChatEvents.onVisibleMessage(
            "Cookie Buff chat",
            isActive = { consumers.hasActiveConsumers },
        ) { message ->
            if (message.isSystemLike && isBoosterCookieConsumedMessage(message.cleanText.trim())) {
                recordConsumedCookie()
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Cookie Buff update",
            isActive = { consumers.isActiveOrDeactivating },
        ) {
            when (consumers.activity()) {
                ConsumerActivity.INACTIVE -> return@onEndTick
                ConsumerActivity.DEACTIVATED -> {
                    reset()
                    return@onEndTick
                }
                ConsumerActivity.ACTIVATED,
                ConsumerActivity.ACTIVE,
                -> Unit
            }
            if (++ticks % STATUS_INTERVAL_TICKS == 0) updateElapsedStatus()
        }
        SkysoftClientEvents.onDisconnect("Cookie Buff reset", ::reset)
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    private fun updateStatusFromTab(now: Long = System.currentTimeMillis()) {
        val tabStatus = parseCookieBuffStatus(
            TabListApi.isSkyBlockDataLoaded,
            TabListApi.skyBlockLines,
            TabListApi.skyBlockFooter,
        )
        when (tabStatus.state) {
            CookieBuffState.ACTIVE -> updateExpiryFromTab(tabStatus, now)
            CookieBuffState.INACTIVE -> clearRememberedExpiry()
            CookieBuffState.LOADING,
            CookieBuffState.UNKNOWN,
            -> status = rememberedStatus(now).takeIf { it.state == CookieBuffState.ACTIVE } ?: tabStatus
        }
    }

    private fun updateElapsedStatus(now: Long = System.currentTimeMillis()) {
        val remembered = rememberedStatus(now)
        if (status.state == CookieBuffState.ACTIVE || remembered.state == CookieBuffState.ACTIVE) {
            status = remembered
        }
    }

    private fun updateExpiryFromTab(tabStatus: CookieBuffStatus, now: Long) {
        val expiry = tabStatus.remaining?.let { cookieBuffExpiryFromDuration(it, now) }
        if (expiry == null) {
            status = CookieBuffStatus(CookieBuffState.UNKNOWN)
            return
        }
        val storage = ProfileStorageApi.playerStorage
        if (kotlin.math.abs(storage.cookieBuffExpiresAtMillis - expiry) >= EXPIRY_SAVE_THRESHOLD_MILLIS) {
            ProfileStorageApi.updatePlayer { it.cookieBuffExpiresAtMillis = expiry }
        }
        status = tabStatus
    }

    private fun recordConsumedCookie(now: Long = System.currentTimeMillis()) {
        ProfileStorageApi.updatePlayer { storage ->
            storage.cookieBuffExpiresAtMillis = maxOf(now, storage.cookieBuffExpiresAtMillis) + COOKIE_DURATION_MILLIS
        }
        status = rememberedStatus(now)
    }

    private fun clearRememberedExpiry() {
        val storage = ProfileStorageApi.playerStorage
        if (storage.cookieBuffExpiresAtMillis != 0L) {
            ProfileStorageApi.updatePlayer { it.cookieBuffExpiresAtMillis = 0L }
        }
        status = CookieBuffStatus(CookieBuffState.INACTIVE)
    }

    private fun rememberedStatus(now: Long): CookieBuffStatus {
        val expiry = ProfileStorageApi.playerStorage.cookieBuffExpiresAtMillis
        return if (expiry > now) {
            CookieBuffStatus(CookieBuffState.ACTIVE, formatRememberedDuration(expiry - now))
        } else {
            CookieBuffStatus(CookieBuffState.LOADING)
        }
    }

    private fun reset() {
        ticks = 0
        status = CookieBuffStatus(CookieBuffState.LOADING)
        consumers.resetActivity()
    }

    private const val STATUS_INTERVAL_TICKS = 20
    private const val COOKIE_DURATION_MILLIS = 4L * 24L * 60L * 60L * 1_000L
    private const val EXPIRY_SAVE_THRESHOLD_MILLIS = 60_000L
}
