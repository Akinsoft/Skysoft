package com.skysoft.features.profit

import com.google.gson.Gson
import com.skysoft.config.CustomProfitTrackerConfig
import com.skysoft.config.CustomProfitTrackerLocation
import com.skysoft.config.CustomProfitTrackerLocations
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.ProfitTrackerQuantityPosition
import com.skysoft.config.ProfitTrackerSummaryLine
import com.skysoft.config.normalizedCustomTrackerName
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object CustomProfitTrackerSharing {
    private val gson = Gson()

    fun encode(tracker: CustomProfitTrackerConfig): String {
        val shared = SharedCustomProfitTracker(
            name = normalizedCustomTrackerName(tracker.name),
            anyIsland = tracker.locations.anyIsland,
            locations = tracker.locations.entries.map { SharedLocation(it.island, it.areas.toList()) },
            items = tracker.items.toList(),
            priceSources = tracker.priceSources.toMap(),
            trackCoins = tracker.trackCoins,
            priceSource = tracker.config.settings.priceSource.name,
            pauseAfter = tracker.config.settings.pauseAfter,
            pauseAfterSeconds = tracker.config.settings.pauseAfterSeconds,
            maximumItems = tracker.config.settings.maximumItems,
            showItemIcons = tracker.config.details.showItemIcons,
            quantityPosition = tracker.config.details.quantityPosition.name,
            highlightChanges = tracker.config.details.highlightChanges,
            summaryLines = tracker.config.details.summaryLines.get().map(ProfitTrackerSummaryLine::name),
            showBackground = tracker.config.details.showBackground,
        )
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(gson.toJson(shared).toByteArray(StandardCharsets.UTF_8))
        return "$SHARE_PREFIX$encoded"
    }

    fun decode(code: String): CustomProfitTrackerConfig? = runCatching {
        val encoded = code.trim().takeIf { it.startsWith(SHARE_PREFIX) }?.removePrefix(SHARE_PREFIX) ?: return null
        if (encoded.length > MAXIMUM_SHARE_CODE_LENGTH) return null
        val json = String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        val shared = gson.fromJson(json, SharedCustomProfitTracker::class.java) ?: return null
        if (shared.version != SHARE_VERSION || shared.items.size > MAXIMUM_SHARED_ITEMS ||
            shared.locations.size > MAXIMUM_SHARED_LOCATIONS
        ) return null
        val source = ProfitTrackerPriceSource.entries.firstOrNull { it.name == shared.priceSource } ?: return null
        val quantity = ProfitTrackerQuantityPosition.entries.firstOrNull { it.name == shared.quantityPosition } ?: return null
        val summary = shared.summaryLines.map { name ->
            ProfitTrackerSummaryLine.entries.firstOrNull { it.name == name } ?: return null
        }
        val locations = shared.locations.map { location ->
            CustomProfitTrackerLocation(location.island, location.areas.toMutableList())
        }
        val tracker = CustomProfitTrackerConfig(
            name = shared.name,
            locations = CustomProfitTrackerLocations(shared.anyIsland, locations.toMutableList()),
            items = shared.items.toMutableList(),
            priceSources = shared.priceSources.toMutableMap(),
            trackCoins = shared.trackCoins,
        )
        with(tracker.config.settings) {
            priceSource = source
            pauseAfter = shared.pauseAfter
            pauseAfterSeconds = shared.pauseAfterSeconds.coerceIn(MINIMUM_PAUSE_AFTER_SECONDS, MAXIMUM_PAUSE_AFTER_SECONDS)
            maximumItems = shared.maximumItems.coerceIn(MINIMUM_ITEMS, MAXIMUM_ITEMS)
        }
        with(tracker.config.details) {
            showItemIcons = shared.showItemIcons
            quantityPosition = quantity
            highlightChanges = shared.highlightChanges
            summaryLines.set(summary.toMutableList())
            showBackground = shared.showBackground
        }
        tracker
    }.getOrNull()

    private data class SharedCustomProfitTracker(
        val version: Int = SHARE_VERSION,
        val name: String = "Custom Tracker",
        val anyIsland: Boolean = false,
        val locations: List<SharedLocation> = emptyList(),
        val items: List<String> = emptyList(),
        val priceSources: Map<String, String> = emptyMap(),
        val trackCoins: Boolean = false,
        val priceSource: String = ProfitTrackerPriceSource.INSTANT_SELL.name,
        val pauseAfter: Boolean = true,
        val pauseAfterSeconds: Int = 60,
        val maximumItems: Int = 8,
        val showItemIcons: Boolean = true,
        val quantityPosition: String = ProfitTrackerQuantityPosition.RIGHT.name,
        val highlightChanges: Boolean = true,
        val summaryLines: List<String> = emptyList(),
        val showBackground: Boolean = false,
    )

    private data class SharedLocation(
        val island: String = "",
        val areas: List<String> = emptyList(),
    )
}

private const val SHARE_VERSION = 1
private const val SHARE_PREFIX = "SSCT1:"
private const val MAXIMUM_SHARE_CODE_LENGTH = 1_000_000
private const val MAXIMUM_SHARED_ITEMS = 1_000
private const val MAXIMUM_SHARED_LOCATIONS = 64
private const val MINIMUM_PAUSE_AFTER_SECONDS = 15
private const val MAXIMUM_PAUSE_AFTER_SECONDS = 900
private const val MINIMUM_ITEMS = 1
private const val MAXIMUM_ITEMS = 15
