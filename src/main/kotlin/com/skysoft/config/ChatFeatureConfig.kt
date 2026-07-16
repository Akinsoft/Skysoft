package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class ChatFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Smooth Chat", desc = "Chat animation and visual settings.")
    val smoothChat = SmoothChatConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Longer History", desc = "Keep more messages available when scrolling through chat.")
    val longerHistory = LongerHistoryConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Retain History", desc = "Keep recent chat after disconnecting or restarting Minecraft.")
    @field:ConfigEditorBoolean
    var retainHistory = false

    @JvmField
    @field:Expose
    @field:Category(name = "Chat Compacting", desc = "Combine repeated chat messages.")
    val compacting = ChatCompactingConfig()

    fun repairLoadedValues() {
        smoothChat.repairLoadedValues()
        longerHistory.repairLoadedValues()
        compacting.repairLoadedValues()
    }

    class SmoothChatConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Animate Messages", desc = "Slide new chat messages into place.")
        @field:ConfigEditorBoolean
        var animateMessages = true

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Animate Chat Open", desc = "Slide the chat input bar into place when chat opens.")
        @field:ConfigEditorBoolean
        var animateChatOpen = true

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Chat animation timing settings.")
        @field:Accordion
        val settings = SmoothChatSettingsConfig()

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Details", desc = "Additional chat visual settings.")
        @field:Accordion
        val details = SmoothChatDetailsConfig()

        fun repairLoadedValues() {
            settings.repairLoadedValues()
        }
    }

    class SmoothChatSettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Message Animation Duration", desc = "Duration of the new-message animation in milliseconds.")
        @field:ConfigEditorSlider(minValue = 10f, maxValue = 300f, minStep = 1f)
        var messageAnimationDuration = DEFAULT_MESSAGE_ANIMATION_DURATION

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Chat Open Animation Duration", desc = "Duration of the chat-open animation in milliseconds.")
        @field:ConfigEditorSlider(minValue = 10f, maxValue = 700f, minStep = 1f)
        var chatOpenAnimationDuration = DEFAULT_CHAT_OPEN_ANIMATION_DURATION

        fun repairLoadedValues() {
            messageAnimationDuration = messageAnimationDuration.coerceIn(
                MIN_MESSAGE_ANIMATION_DURATION,
                MAX_MESSAGE_ANIMATION_DURATION,
            )
            chatOpenAnimationDuration = chatOpenAnimationDuration.coerceIn(
                MIN_CHAT_OPEN_ANIMATION_DURATION,
                MAX_CHAT_OPEN_ANIMATION_DURATION,
            )
        }
    }

    class SmoothChatDetailsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Hide Message Indicator", desc = "Hide the message indicator line to the left of chat messages.")
        @field:ConfigEditorBoolean
        var hideMessageIndicator = true
    }

    class LongerHistoryConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Keep more messages available in chat.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Message Limit", desc = "Maximum number of chat messages to keep.")
        @field:ConfigEditorSlider(
            minValue = VANILLA_CHAT_HISTORY_LIMIT.toFloat(),
            maxValue = MAX_CHAT_HISTORY_LIMIT.toFloat(),
            minStep = 100f,
        )
        @field:ConfigVisibleIf("enabled")
        var messageLimit = VANILLA_CHAT_HISTORY_LIMIT

        fun repairLoadedValues() {
            messageLimit = messageLimit.coerceIn(VANILLA_CHAT_HISTORY_LIMIT, MAX_CHAT_HISTORY_LIMIT)
        }
    }

    class ChatCompactingConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Combine repeated chat messages into one line.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(
            name = "Compact Duration",
            desc = "How long repeated messages can be combined.\n§cMay increase memory usage",
        )
        @field:ConfigEditorSlider(
            minValue = MIN_CHAT_COMPACT_DURATION_SECONDS.toFloat(),
            maxValue = MAX_CHAT_COMPACT_DURATION_SECONDS.toFloat(),
            minStep = 1f,
        )
        @field:ConfigVisibleIf("enabled")
        var durationSeconds = DEFAULT_CHAT_COMPACT_DURATION_SECONDS

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "No Blank Lines", desc = "Remove empty or whitespace-only chat messages.")
        @field:ConfigEditorBoolean
        @field:ConfigVisibleIf("enabled")
        var noBlankLines = false

        fun repairLoadedValues() {
            durationSeconds = durationSeconds.coerceIn(
                MIN_CHAT_COMPACT_DURATION_SECONDS,
                MAX_CHAT_COMPACT_DURATION_SECONDS,
            )
        }
    }
}

const val MIN_MESSAGE_ANIMATION_DURATION = 10
const val MAX_MESSAGE_ANIMATION_DURATION = 300
const val DEFAULT_MESSAGE_ANIMATION_DURATION = 150
const val MIN_CHAT_OPEN_ANIMATION_DURATION = 10
const val MAX_CHAT_OPEN_ANIMATION_DURATION = 700
const val DEFAULT_CHAT_OPEN_ANIMATION_DURATION = 170
const val VANILLA_CHAT_HISTORY_LIMIT = 100
const val MAX_CHAT_HISTORY_LIMIT = 1_000
const val MIN_CHAT_COMPACT_DURATION_SECONDS = 20
const val MAX_CHAT_COMPACT_DURATION_SECONDS = 240
const val DEFAULT_CHAT_COMPACT_DURATION_SECONDS = 60
