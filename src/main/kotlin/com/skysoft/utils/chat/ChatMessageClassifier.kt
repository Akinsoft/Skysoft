package com.skysoft.utils.chat

import com.skysoft.utils.SkysoftMessage

internal object ChatMessageClassifier {
    fun classify(raw: SkysoftMessage): ChatMessage {
        val clean = raw.cleanText.trim()
        return messageTypePattern(ChatMessageType.PARTY, partyPattern, clean, raw)
            ?: messageTypePattern(ChatMessageType.GUILD, guildPattern, clean, raw)
            ?: messageTypePattern(ChatMessageType.COOP, coopPattern, clean, raw)
            ?: privateMessage(clean, raw)
            ?: allChatMessage(clean, raw)
            ?: ChatMessage(raw = raw, type = ChatMessageType.SYSTEM, body = clean)
    }

    private fun messageTypePattern(
        messageType: ChatMessageType,
        pattern: Regex,
        clean: String,
        raw: SkysoftMessage,
    ): ChatMessage? {
        val match = pattern.matchEntire(clean) ?: return null
        return ChatMessage(
            raw = raw,
            type = messageType,
            sender = senderFromPrefix(match.groups["sender"]?.value.orEmpty()),
            body = match.groups["body"]?.value.orEmpty().trim(),
        )
    }

    private fun senderFromPrefix(prefix: String): ChatMessageSender? {
        val withoutBracketGroups = bracketGroupPattern.replace(prefix, " ")
        val name = withoutBracketGroups
            .split(whitespacePattern)
            .lastOrNull { playerNamePattern.matchEntire(it) != null }
            ?: return null
        return ChatMessageSender(name, null)
    }

    private fun privateMessage(clean: String, raw: SkysoftMessage): ChatMessage? {
        val match = privatePattern.matchEntire(clean)
            ?.takeUnless { clean.startsWith("From stash:", ignoreCase = true) }
            ?: return null
        val direction = when (match.groups["direction"]?.value?.lowercase()) {
            "from" -> PrivateMessageDirection.FROM
            "to" -> PrivateMessageDirection.TO
            else -> null
        }
        return ChatMessage(
            raw = raw,
            type = ChatMessageType.PRIVATE_MESSAGE,
            sender = senderFromPrefix(match.groups["sender"]?.value.orEmpty()),
            body = match.groups["body"]?.value.orEmpty().trim(),
            privateMessageDirection = direction,
        )
    }

    private fun allChatMessage(clean: String, raw: SkysoftMessage): ChatMessage? {
        val match = allPattern.matchEntire(clean) ?: return null
        val sender = senderFromPrefix(match.groups["sender"]?.value.orEmpty()) ?: return null
        return ChatMessage(
            raw = raw,
            type = ChatMessageType.ALL,
            sender = sender,
            body = match.groups["body"]?.value.orEmpty().trim(),
        )
    }

    private val partyPattern = Regex("""^Party > (?<sender>.+?): (?<body>.*)$""")
    private val guildPattern = Regex("""^Guild > (?<sender>.+?): (?<body>.*)$""")
    private val coopPattern = Regex("""^Co-op > (?<sender>.+?): (?<body>.*)$""")
    private val privatePattern = Regex("""^(?<direction>From|To) (?<sender>.+?): (?<body>.*)$""")
    private val allPattern = Regex(
        """^(?<sender>(?:(?:\[[^]]+] )+(?:(?:[^\[\]A-Za-z0-9_\s:]+ )(?:\[[^]]+] )*)*)?""" +
            """[A-Za-z0-9_]{1,16}(?: \[[^]]+])?(?: [^A-Za-z0-9_\s:]+)*): (?<body>.*)$""",
    )
    private val bracketGroupPattern = Regex("""\[[^]]+]""")
    private val whitespacePattern = Regex("""\s+""")
    private val playerNamePattern = Regex("""[A-Za-z0-9_]{1,16}""")
}
