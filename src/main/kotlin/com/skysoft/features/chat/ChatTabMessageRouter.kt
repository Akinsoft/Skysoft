package com.skysoft.features.chat

import com.skysoft.config.ChatTabChannel
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import com.skysoft.utils.chat.ChatMessageClassifier
import com.skysoft.utils.chat.ChatMessageType
import net.minecraft.network.chat.Component

internal object ChatTabMessageRouter {
    fun isVisible(channel: ChatTabChannel, content: Component, text: String): Boolean {
        val type = ChatMessageClassifier.classify(
            SkysoftMessage(content, SkysoftMessageSource.GAME),
        ).type
        return when (channel) {
            ChatTabChannel.ALL -> true
            ChatTabChannel.GUILD -> type == ChatMessageType.GUILD || guildSystemPattern.matches(text)
            ChatTabChannel.DM -> type == ChatMessageType.PRIVATE_MESSAGE || directMessageSystemPattern.matches(text)
            ChatTabChannel.PARTY -> type == ChatMessageType.PARTY || partySystemPattern.matches(text)
        }
    }

    private val partySystemPattern = Regex(
        "^(?:" +
            "Party invite from .+|" +
            ".+ has invited you to join their party!|" +
            ".+ invited .+ to the party!(?: They have 60 seconds to accept\\.)?|" +
            "You have 60 seconds to accept\\. Click here to join!|" +
            "You have joined .+'s party!|" +
            "You'll be partying with: .+|" +
            ".+ joined the party\\.|" +
            "You left the party\\.|" +
            ".+ has left the party\\.|" +
            "You have been (?:removed|kicked) from the party.*|" +
            ".+ has been removed from the party\\.|" +
            "Kicked .+ because they were offline\\.|" +
            ".+ was removed from your party because they disconnected\\.|" +
            ".+ has disbanded the party!|" +
            "The party was disbanded.*|" +
            "The party was transferred to .+|" +
            ".+ was (?:promoted to Party Moderator|demoted to Party Member).*|" +
            ".+ has (?:promoted .+ to Party Moderator|demoted .+ to Party Member).*|" +
            "Party chat is (?:now )?(?:muted|unmuted).*|" +
            "All [Ii]nvite is now (?:enabled|disabled).*|" +
            "You are not (?:currently )?in a party(?: right now)?\\.|" +
            "Party Members \\(\\d+\\)|" +
            "Party (?:Leader|Moderators|Members):.*" +
            ")$",
    )

    private val guildSystemPattern = Regex(
        "^(?:" +
            "Officer > .+?: .*|" +
            "Guild > .+ (?:joined|left)\\.|" +
            "\\[Guild] .+|" +
            "Guild invite from .+|" +
            ".+ has invited you to join their guild!|" +
            "You have been invited to join .+ guild!|" +
            "You left the guild!|" +
            ".+ (?:joined|left) the guild\\.|" +
            ".+ (?:was|has been) kicked from the guild\\.|" +
            "The guild was disbanded.*|" +
            "Guild chat is (?:now )?(?:muted|unmuted).*|" +
            "Guild (?:Name|Master|Members|Level|Created|MOTD):.*" +
            ")$",
    )

    private val directMessageSystemPattern = Regex(
        "^(?:" +
            "Friend request from .+|" +
            "You sent a friend request to .+!|" +
            "The friend request to .+ has expired\\.|" +
            "You are now friends with .+|" +
            ".+ is now your friend!|" +
            "You removed .+ from your friends list!|" +
            ".+ removed you from their friends list!|" +
            ".+ is (?:now|no longer) a best friend!|" +
            "Friend > .+ (?:joined|left)\\.|" +
            "You have no friends(?: online)?\\.|" +
            "You don't have any friends.*|" +
            "You cannot message (?:this player|yourself)[.!]|" +
            "You don't have anyone to reply to!|" +
            "This player has private messages disabled\\.|" +
            "You must wait .+ before messaging this player again\\.|" +
            "Friends(?: List)?(?: .*)?" +
            ")$",
    )
}
