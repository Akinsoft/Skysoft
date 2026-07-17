package com.skysoft.features.chat

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.SkysoftMod
import com.skysoft.config.MAX_CHAT_HISTORY_LIMIT
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.mixin.ChatComponentAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization

object ChatHistoryPersistence {
    fun register() {
        SkysoftClientEvents.onClientStarted("Chat History restore") {
            if (ChatFeatureSettings.isHistoryRetained()) restore(MinecraftClient.chat(it))
        }
        SkysoftClientEvents.onClientStopping("Chat History save") {
            if (ChatFeatureSettings.isHistoryRetained()) save(MinecraftClient.chat(it))
        }
    }

    fun save(chat: ChatComponent) {
        save((chat as ChatComponentAccessor).skysoftAllMessages())
    }

    fun save(messages: List<GuiMessage>) {
        try {
            SkysoftConfigFiles.writeStringSafely(
                SkysoftConfigFiles.chatHistory,
                encode(messages.take(ChatFeatureSettings.historyLimit())),
            )
        } catch (exception: Exception) {
            SkysoftMod.LOGGER.error("Failed to save retained chat history", exception)
        }
    }

    fun restore(chat: ChatComponent) {
        if (!SkysoftConfigFiles.hasFileOrBackup(SkysoftConfigFiles.chatHistory)) return
        try {
            val messages = SkysoftConfigFiles.readWithBackup(SkysoftConfigFiles.chatHistory) { path ->
                require(Files.size(path) <= MAX_HISTORY_FILE_BYTES) { "Retained chat history exceeds the size limit" }
                Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                    decode(JsonParser.parseReader(reader).asJsonObject, ChatFeatureSettings.historyLimit())
                }
            }
            val accessor = chat as ChatComponentAccessor
            accessor.skysoftAllMessages().apply {
                clear()
                addAll(messages)
            }
            accessor.skysoftRefreshTrimmedMessages()
        } catch (exception: Exception) {
            SkysoftMod.LOGGER.warn("Failed to restore retained chat history", exception)
        }
    }

    internal fun encode(messages: List<GuiMessage>): String {
        val root = JsonObject()
        root.addProperty("version", HISTORY_VERSION)
        root.add(
            "messages",
            JsonArray().also { array ->
                messages.take(MAX_CHAT_HISTORY_LIMIT).forEach { message -> array.add(encodeMessage(message)) }
            },
        )
        return GSON.toJson(root)
    }

    internal fun decode(root: JsonObject, limit: Int): List<GuiMessage> {
        require(root.get("version")?.asInt == HISTORY_VERSION) { "Unsupported retained chat history version" }
        return root.getAsJsonArray("messages")
            ?.take(limit.coerceIn(0, MAX_CHAT_HISTORY_LIMIT))
            ?.mapNotNull { element ->
                runCatching {
                    element.takeIf { it.isJsonObject }?.asJsonObject?.let(::decodeMessage)
                }.getOrNull()
            }
            .orEmpty()
    }

    private fun encodeMessage(message: GuiMessage): JsonObject = JsonObject().apply {
        add("content", encodeComponent(message.content()))
        addProperty("source", message.source().name)
        message.tag()?.let { add("tag", encodeTag(it)) }
    }

    private fun decodeMessage(json: JsonObject): GuiMessage? {
        val content = json.get("content")?.let(::decodeComponent) ?: return null
        val source = json.get("source")?.asString
            ?.let { runCatching { GuiMessageSource.valueOf(it) }.getOrNull() }
            ?: GuiMessageSource.SYSTEM_CLIENT
        return GuiMessage(RESTORED_MESSAGE_TIME, content, null, source, json.getAsJsonObject("tag")?.let(::decodeTag))
    }

    private fun encodeTag(tag: GuiMessageTag): JsonObject = JsonObject().apply {
        addProperty("indicatorColor", tag.indicatorColor())
        tag.icon()?.let { addProperty("icon", it.name) }
        tag.text()?.let { add("text", encodeComponent(it)) }
        tag.logTag()?.let { addProperty("logTag", it) }
    }

    private fun decodeTag(json: JsonObject): GuiMessageTag = GuiMessageTag(
        json.get("indicatorColor")?.asInt ?: 0,
        json.get("icon")?.asString?.let { runCatching { GuiMessageTag.Icon.valueOf(it) }.getOrNull() },
        json.get("text")?.let(::decodeComponent),
        json.get("logTag")?.asString,
    )

    private fun encodeComponent(component: Component) =
        ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, component).getOrThrow()

    private fun decodeComponent(element: com.google.gson.JsonElement): Component? =
        ComponentSerialization.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, element).result().orElse(null)

    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private const val HISTORY_VERSION = 1
    private const val RESTORED_MESSAGE_TIME = -1_000_000
    private const val MAX_HISTORY_FILE_BYTES = 16L * 1024L * 1024L
}
