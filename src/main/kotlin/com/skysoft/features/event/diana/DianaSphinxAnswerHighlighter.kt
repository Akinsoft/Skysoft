package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.PlainTextContents

internal object DianaSphinxAnswerHighlighter {
    private val state = DianaSphinxAnswerState()
    private val config get() = SkysoftConfigGui.config().events.diana

    @JvmStatic
    fun highlight(component: Component): Component {
        if (!config.sphinxHelper.enabled || !DianaEventState.isOnHub()) {
            state.clear()
            return component
        }
        return state.highlight(component)
    }
}

internal class DianaSphinxAnswerState {
    private var waitingForQuestion = false
    private var activeAnswer: String? = null
    private var answersSeen = 0
    private var expiresAtMillis = 0L

    fun highlight(component: Component, now: Long = System.currentTimeMillis()): Component {
        if (now >= expiresAtMillis) clear()

        val text = component.cleanSkyBlockText()
        return when {
            text == RIGHT_ANSWER_MESSAGE -> {
                clear()
                component
            }
            text == QUESTION_HEADER -> {
                startQuestion(now)
                component
            }
            waitingForQuestion -> {
                acceptQuestion(text, now)
                component
            }
            else -> highlightAnswer(component, text)
        }
    }

    private fun startQuestion(now: Long) {
        waitingForQuestion = true
        activeAnswer = null
        answersSeen = 0
        expiresAtMillis = now + QUESTION_TIMEOUT_MILLIS
    }

    private fun acceptQuestion(text: String, now: Long) {
        val answer = SPHINX_ANSWERS[text]
        if (answer == null) {
            clear()
        } else {
            waitingForQuestion = false
            activeAnswer = answer
            expiresAtMillis = now + QUESTION_TIMEOUT_MILLIS
        }
    }

    private fun highlightAnswer(component: Component, text: String): Component {
        val answer = activeAnswer ?: return component
        val option = ANSWER_PATTERN.matchEntire(text)?.groupValues?.get(1) ?: run {
            if (text.isNotBlank() && text != ANSWER_FOOTER) clear()
            return component
        }

        answersSeen++
        val highlighted = if (option == answer) {
            component.sphinxAnswerHighlight()
        } else {
            component
        }
        if (answersSeen >= ANSWER_COUNT) clear()
        return highlighted
    }

    fun clear() {
        waitingForQuestion = false
        activeAnswer = null
        answersSeen = 0
        expiresAtMillis = 0L
    }
}

private const val QUESTION_HEADER = "Question"
private const val ANSWER_FOOTER = "Click your response to answer!"
private const val RIGHT_ANSWER_MESSAGE = "Right answer! The Sphinx is intrigued by your superior intellect."
private const val ANSWER_COUNT = 3
private const val QUESTION_TIMEOUT_MILLIS = 30_000L
private val ANSWER_PATTERN = Regex("""[ABC]\) (.+)""")

private fun Component.sphinxAnswerHighlight(): Component {
    val highlighted = plainCopyWithoutLegacyFormatting().withStyle { style ->
        style.withColor(ChatFormatting.GREEN).withBold(true)
    }
    siblings.forEach { sibling ->
        highlighted.append(sibling.sphinxAnswerHighlight())
    }
    return highlighted
}

private fun Component.plainCopyWithoutLegacyFormatting(): MutableComponent {
    val currentContents = contents
    return if (currentContents is PlainTextContents) {
        Component.literal(currentContents.text().removeLegacyFormatting())
    } else {
        plainCopy()
    }.withStyle(style)
}

private fun String.removeLegacyFormatting(): String =
    legacyFormattingPattern.replace(this, "")

private val SPHINX_ANSWERS = mapOf(
    "How do you obtain the Dark Purple Dye?" to "Dark Auction",
    "How many floors are there in The Catacombs?" to "7",
    "What does Junker Joel collect?" to "Junk",
    "What is the first type of slayer Maddox offers?" to "Zombie",
    "What item do you use to kill Pests?" to "Vacuum",
    "What type of mob is exclusive to the Fishing Festival?" to "Shark",
    "Where is the Titanoboa found?" to "Backwater Bayou",
    "Where is Trevor the Trapper found?" to "Mushroom Desert",
    "Which item rarity comes after Mythic?" to "Divine",
    "Which of these is NOT a pet?" to "Slime",
    "Which of these is NOT a type of Gemstone?" to "Prismite",
    "Which type of Gemstone has the lowest Breaking Power?" to "Ruby",
    "Who helps you apply Rod Parts?" to "Roddy",
    "Who owns the Gold Essence Shop?" to "Marigold",
    "Who runs the Chocolate Factory?" to "Hoppity",
)

private val legacyFormattingPattern = Regex("\u00A7.")
