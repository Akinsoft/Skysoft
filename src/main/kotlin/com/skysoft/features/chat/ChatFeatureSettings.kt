package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.VANILLA_CHAT_HISTORY_LIMIT

object ChatFeatureSettings {
    private val config
        get() = SkysoftConfigGui.config().chat

    fun historyLimit(): Int = config.longerHistory
        .takeIf { it.enabled }
        ?.messageLimit
        ?: VANILLA_CHAT_HISTORY_LIMIT

    fun isHistoryRetained(): Boolean = config.retainHistory

    fun isCompactingEnabled(): Boolean = config.compacting.enabled

    fun compactDurationMillis(): Long = config.compacting.durationSeconds * MILLIS_PER_SECOND

    fun areBlankLinesHidden(): Boolean = config.compacting.enabled && config.compacting.noBlankLines
}

private const val MILLIS_PER_SECOND = 1_000L
