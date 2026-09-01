package com.skysoft.features.misc.actionbar

import com.skysoft.config.SkillExpProgressFormat
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkillActionBarMatch
import com.skysoft.data.skyblock.SkillExpGainApi
import com.skysoft.data.skyblock.SkyBlockSkill
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.OverlayMessages
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.container.horizontalLayout
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import com.skysoft.utils.renderables.withIsolatedPose
import java.util.Locale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object SkillExpDisplay {
    private val config get() = SkysoftConfigGui.config().gui.skillExpDisplay
    private var current: SkillExpDisplayState? = null

    fun register() {
        SkillExpGainApi.onSkillExpGain("Skill EXP Display tracking", ::isActive, ::onSkillExpGain)
        ChatEvents.onActionBarModify("Skill EXP Display action bar", ::isActive, ::separateFromActionBar)
        SkysoftClientEvents.onDisconnect("Skill EXP Display reset") { current = null }
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "skill_exp_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = { context -> TabDataOverlays.canRender(context) && canRenderLive() },
                render = { context, _ -> renderHud(context) },
            ),
            object : HudEditorElement {
                override val id: String = "skill_exp_display"
                override val label: String = "Skill EXP Display"
                override val position get() = config.position
                override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
                override fun width(): Int = previewRenderable().width
                override fun height(): Int = previewRenderable().height
                override fun isVisible(): Boolean = config.enabled
                override fun renderEditor(context: GuiGraphicsExtractor) = previewRenderable().render(context)
                override fun openConfig() = SkysoftConfigGui.open("Skill EXP Display")
            },
        )
    }

    private fun onSkillExpGain(event: SkillExpGainApi.SkillExpGain) {
        if (!event.isFromActionBar) return
        val info = checkNotNull(SkillExpGainApi.getSkillInfo(event.skill)) {
            "Skill EXP action bar progress was not stored"
        }
        current = SkillExpDisplayState(event.skill, info.lastGain, info.currentXp, info.currentXpMax)
    }

    private fun separateFromActionBar(message: SkysoftMessage): Component {
        val normalized = NormalizedActionBar(message.plainText)
        val match = SkillExpGainApi.findActionBarGain(normalized.text)
        val displayed = current
        if (match == null || displayed?.matches(match) != true) {
            current = null
            return message.component
        }
        val range = message.plainText.actionBarSegmentRange(normalized.rawRange(match.range))
        return message.component.withoutRanges(listOf(range))
    }

    private fun renderHud(context: GuiGraphicsExtractor) {
        val state = current ?: return
        context.withIsolatedPose {
            pose().translate(0f, -BottomHudLayout.reservedHeight().toFloat())
            config.position.renderRenderable(context, renderable(state))
        }
    }

    private fun canRenderLive(minecraft: Minecraft = Minecraft.getInstance()): Boolean =
        isActive() &&
            current != null &&
            OverlayMessages.time(minecraft) > 0 &&
            !MinecraftClient.isGuiHidden(minecraft)

    private fun isActive(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    private fun previewRenderable(): GuiRenderable = renderable(PREVIEW_STATE)

    private fun renderable(state: SkillExpDisplayState): GuiRenderable {
        val format = config.settings.format
        if (!config.details.showSkillIcon) return StringRenderable(skillExpDisplayText(state, format))
        return horizontalLayout(
            listOf(
                StringRenderable("§3+${state.gainedText}"),
                ItemIconRenderable(
                    ItemStack(state.skill.iconItem()),
                    Minecraft.getInstance().font.lineHeight / ITEM_SIZE,
                ),
                StringRenderable("§3(${skillExpProgressText(state, format)})"),
            ),
            spacing = ICON_TEXT_SPACING,
        )
    }
}

internal data class SkillExpDisplayState(
    val skill: SkyBlockSkill,
    val gainedText: String,
    val currentXp: Long,
    val neededXp: Long,
) {
    fun matches(match: SkillActionBarMatch): Boolean =
        skill.displayName == match.skillName && gainedText == match.gainedText
}

internal fun skillExpDisplayText(state: SkillExpDisplayState, format: SkillExpProgressFormat): String =
    "§3+${state.gainedText} ${state.skill.displayName} (${skillExpProgressText(state, format)})"

private fun skillExpProgressText(state: SkillExpDisplayState, format: SkillExpProgressFormat): String = when (format) {
    SkillExpProgressFormat.NUMBERS -> "${state.currentXp.addSeparators()}/${state.neededXp.addSeparators()}"
    SkillExpProgressFormat.PERCENTAGE -> skillExpPercentage(state)
}

private fun skillExpPercentage(state: SkillExpDisplayState): String {
    val requiredXp = state.neededXp.takeUnless { it == 0L }
        ?: SkillExpGainApi.xpRequiredForMaxLevel(state.skill)
    val percentage = String.format(
        Locale.ROOT,
        "%.1f",
        state.currentXp.toDouble() / requiredXp * PERCENT_DENOMINATOR,
    )
    return "${percentage.removeSuffix(".0")}%"
}

private fun SkyBlockSkill.iconItem(): Item = when (this) {
    SkyBlockSkill.COMBAT -> Items.STONE_SWORD
    SkyBlockSkill.FARMING -> Items.GOLDEN_HOE
    SkyBlockSkill.FISHING -> Items.FISHING_ROD
    SkyBlockSkill.MINING -> Items.STONE_PICKAXE
    SkyBlockSkill.FORAGING -> Items.JUNGLE_SAPLING
    SkyBlockSkill.ENCHANTING -> Items.ENCHANTING_TABLE
    SkyBlockSkill.ALCHEMY -> Items.BREWING_STAND
    SkyBlockSkill.CARPENTRY -> Items.CRAFTING_TABLE
    SkyBlockSkill.TAMING -> Items.POLAR_BEAR_SPAWN_EGG
    SkyBlockSkill.HUNTING -> Items.LEAD
}

private const val PERCENT_DENOMINATOR = 100
private const val ITEM_SIZE = 16.0
private const val ICON_TEXT_SPACING = 2

private val PREVIEW_STATE = SkillExpDisplayState(
    skill = SkyBlockSkill.COMBAT,
    gainedText = "169.5",
    currentXp = 650_412_191,
    neededXp = 0,
)
