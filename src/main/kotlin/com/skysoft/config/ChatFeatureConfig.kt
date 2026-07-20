package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.utils.SoundUtilities
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorTextList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.gui.editors.TextListEntry
import io.github.notenoughupdates.moulconfig.observer.Property
import org.lwjgl.glfw.GLFW

class ChatFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Smooth Chat", desc = "Chat animation and visual settings.")
    val smoothChat = SmoothChatConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Chat Peek", desc = "Temporarily expand chat without opening it.")
    val chatPeek = ChatPeekConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Chat History", desc = "Configure chat history size and persistence.")
    val history = ChatHistoryConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Chat Compacting", desc = "Combine repeated chat messages.")
    val compacting = ChatCompactingConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Message Filtering", desc = "Hide selected game messages.")
    val messageFiltering = MessageFilteringConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Chat Notify", desc = "Highlight chosen words and optionally play a ping.")
    val notify = ChatNotifyConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Chat Tabs", desc = "Separate chat messages into channels.")
    val tabs = ChatTabsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Timestamps", desc = "Show the time beside chat messages.")
    val timestamps = ChatTimestampsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Copy Chat", desc = "Copy complete chat messages from the chat screen.")
    val copyChat = CopyChatConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Preview Image", desc = "Preview image links hovered in chat.")
    val previewImage = ImagePreviewConfig()

    fun repairLoadedValues() {
        smoothChat.repairLoadedValues()
        history.repairLoadedValues()
        compacting.repairLoadedValues()
    }

    class MessageFilteringConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Hide Sacks Messages", desc = "Hide item transfer summaries from Sacks.")
        @field:ConfigEditorBoolean
        var hideSacksMessages = false
    }

    class ImagePreviewConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Show image links hovered in chat.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Image preview controls.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = ImagePreviewSettingsConfig()
    }

    class ImagePreviewSettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Preview Key", desc = "Hold to show the image. Leave unbound to show it automatically.")
        @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
        var previewKey = GLFW.GLFW_KEY_UNKNOWN
    }

    class SmoothChatConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Animate Messages", desc = "Slide new chat messages into place.")
        @field:ConfigEditorBoolean
        var animateMessages = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Animate Chat Open", desc = "Slide the chat input bar into place when chat opens.")
        @field:ConfigEditorBoolean
        var animateChatOpen = false

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

    class ChatPeekConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Expand chat while the peek key is held.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Chat Peek settings.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = ChatPeekSettingsConfig()
    }

    class ChatPeekSettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Peek Key", desc = "Key held to expand chat.")
        @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_Z)
        var key = GLFW.GLFW_KEY_Z
    }

    class ChatHistoryConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Longer History", desc = "Keep more messages available in chat.")
        @field:ConfigEditorBoolean
        var isLongerHistoryEnabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Retain History", desc = "Keep recent chat after disconnecting or restarting Minecraft.")
        @field:ConfigEditorBoolean
        var isHistoryRetained = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Chat history settings.")
        @field:Accordion
        @field:ConfigVisibleIf("isLongerHistoryEnabled")
        val settings = ChatHistorySettingsConfig()

        fun repairLoadedValues() {
            settings.repairLoadedValues()
        }
    }

    class ChatHistorySettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Message Limit", desc = "Maximum number of chat messages to keep.")
        @field:ConfigEditorSlider(
            minValue = VANILLA_CHAT_HISTORY_LIMIT.toFloat(),
            maxValue = MAX_CHAT_HISTORY_LIMIT.toFloat(),
            minStep = 100f,
        )
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
        @field:ConfigOption(name = "Settings", desc = "Chat compacting settings.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = ChatCompactingSettingsConfig()

        fun repairLoadedValues() {
            settings.repairLoadedValues()
        }
    }

    class ChatCompactingSettingsConfig {
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
        var durationSeconds = DEFAULT_CHAT_COMPACT_DURATION_SECONDS

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "No Blank Lines", desc = "Remove empty or whitespace-only chat messages.")
        @field:ConfigEditorBoolean
        var noBlankLines = false

        fun repairLoadedValues() {
            durationSeconds = durationSeconds.coerceIn(
                MIN_CHAT_COMPACT_DURATION_SECONDS,
                MAX_CHAT_COMPACT_DURATION_SECONDS,
            )
        }
    }

    class ChatTabsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Show channel tabs when chat is open.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Chat tab settings.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = ChatTabsSettingsConfig()
    }

    class ChatTabsSettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Position", desc = "Where channel tabs appear around chat.")
        @field:ConfigEditorDropdown
        var position = ChatTabPosition.ABOVE

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Channels", desc = "Channels available as chat tabs.")
        @field:ConfigEditorDraggableList
        val channels: Property<MutableList<ChatTabChannel>> = Property.of(
            mutableListOf(ChatTabChannel.ALL, ChatTabChannel.GUILD, ChatTabChannel.DM, ChatTabChannel.PARTY),
        )
    }

    class ChatNotifyConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Highlight configured words in chat.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Chat notification settings.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = ChatNotifySettingsConfig()
    }

    class ChatNotifySettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Ignore Own", desc = "Do not notify on messages sent by you.")
        @field:ConfigEditorBoolean
        var isOwnMessagesIgnored = true

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Words", desc = "Words or phrases to highlight and optionally notify you about.")
        @field:ConfigEditorTextList(
            disabledSound = SoundUtilities.NAVIGATION_LEFT_SOUND_ID,
            enabledSound = SoundUtilities.NAVIGATION_RIGHT_SOUND_ID,
            defaultSound = SoundUtilities.CHAT_NOTIFY_DEFAULT_SOUND_ID,
            showColour = true,
            showNotification = true,
            showVolume = true,
        )
        val words: Property<MutableList<TextListEntry>> = Property.of(mutableListOf())
    }

    class ChatTimestampsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Show the time beside each chat message.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Chat timestamp settings.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = ChatTimestampsSettingsConfig()
    }

    class ChatTimestampsSettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Format", desc = "How chat timestamps are displayed.")
        @field:ConfigEditorDropdown
        var format = ChatTimestampFormat.TWENTY_FOUR_HOUR
    }

    class CopyChatConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Copy a complete chat message while hovering it.")
        @field:ConfigEditorBoolean
        var enabled = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Copy Chat settings.")
        @field:Accordion
        @field:ConfigVisibleIf("enabled")
        val settings = CopyChatSettingsConfig()
    }

    class CopyChatSettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Copy Key", desc = "Key used to copy the hovered chat message.")
        @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
        var key = GLFW.GLFW_KEY_LEFT_SHIFT
    }
}

enum class ChatTabPosition(private val displayName: String) {
    ABOVE("Above Chat"),
    UNDER("Under Chat"),
    RIGHT("Right of Chat"),
    ;

    override fun toString(): String = displayName
}

enum class ChatTabChannel(private val displayName: String) {
    ALL("All"),
    GUILD("Guild"),
    DM("DM"),
    PARTY("Party"),
    ;

    override fun toString(): String = displayName
}

enum class ChatTimestampFormat(private val displayName: String, val pattern: String) {
    TWENTY_FOUR_HOUR("24-hour (HH:mm)", "HH:mm"),
    TWENTY_FOUR_HOUR_SECONDS("24-hour with seconds", "HH:mm:ss"),
    TWELVE_HOUR("12-hour (h:mm a)", "h:mm a"),
    TWELVE_HOUR_SECONDS("12-hour with seconds", "h:mm:ss a"),
    ;

    override fun toString(): String = displayName
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
