package com.skysoft.features.misc

import com.skysoft.config.PartyDisplayAlignment
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.features.event.diana.DianaLootshareReadyMarkers
import com.skysoft.features.event.diana.DianaRareMobSharing
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.TabDataOverlays
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

private val partyDisplayConfig get() = SkysoftConfigGui.config().gui.partyDisplay
private val cancelLabel = Component.literal("[Cancel]").withStyle(ChatFormatting.RED)
private val confirmLabel = Component.literal("[Confirm]").withStyle(ChatFormatting.GREEN)
private var hoveredControl: OverlayControlArea<PartyDisplayControl>? = null
private val hudControls = PartyDisplayHudControls()

internal fun registerPartyDisplayHud() {
    registerPartyDisplayInput()
    GuiOverlayRegistry.registerHud(
        GuiOverlay(
            id = "party_display",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = TabDataOverlays.contexts,
            screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
            render = { context, _ -> renderPartyDisplayHud(context) },
        ),
        object : HudEditorElement {
            override val id: String = "party_display"
            override val label: String = "Party Display"
            override val position get() = partyDisplayConfig.position
            override val hasEditorBackground: Boolean get() = !partyDisplayConfig.details.background
            override fun width(): Int = editorRenderable()?.width ?: 0
            override fun height(): Int = editorRenderable()?.height ?: 0
            override fun isVisible(): Boolean = partyDisplayConfig.enabled && PartyDisplay.currentMembers().isNotEmpty()
            override fun renderEditor(context: GuiGraphicsExtractor) = editorRenderable()?.render(context) ?: Unit
            override fun openConfig() = SkysoftConfigGui.open("Party Display")
        },
    )
}

private fun registerPartyDisplayInput() {
    InventoryOverlayInput.registerClickHandler("Party Display mouse click", isActive = { true }) { screen, click ->
        if (shouldAllowPartyDisplayClick(screen, click)) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
}

private fun shouldAllowPartyDisplayClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): Boolean {
    if (!isPartyDisplayVisible()) return true
    if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) {
        hudControls.closeMemberPanel()
        return true
    }
    val control = hoveredControl?.action
    val panelHovered = hudControls.memberPanelHovered
    if (!panelHovered && control !is PartyDisplayControl.Manage) hudControls.closeMemberPanel()
    if (control == null) return !panelHovered
    val handled = hudControls.wasClickHandled(control, click.button())
    if (handled && control !is PartyDisplayControl.Unavailable) SoundUtilities.playClickSound()
    return !handled && !panelHovered
}

private fun renderPartyDisplayHud(context: GuiGraphicsExtractor) {
    if (!isPartyDisplayVisible()) {
        clearPartyDisplayInteraction()
        return
    }
    val members = PartyDisplay.currentMembers()
    val minecraft = Minecraft.getInstance()
    val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = screen != null
    if (!inventoryOpen) hudControls.clear()
    hoveredControl = null
    val localLeader = minecraft.player?.uuid == HypixelPartyApi.leaderUuid
    val renderable = PartyDisplayRenderable(
        members,
        partyDisplayConfig.details.alignment,
        partyDisplayConfig.details.background,
        inventoryOpen,
        localLeader,
        lootshareCheckmarks(),
        hudControls,
    )
    if (screen == null) {
        renderPositioned(context, renderable)
        return
    }
    renderInteractive(context, screen, renderable, members, localLeader)
}

private fun renderInteractive(
    context: GuiGraphicsExtractor,
    screen: AbstractContainerScreen<*>,
    renderable: PartyDisplayRenderable,
    members: List<PartyDisplayMember>,
    localLeader: Boolean,
) {
    val minecraft = Minecraft.getInstance()
    val window = minecraft.window
    val (mouseX, mouseY) = InputUtilities.scaledMousePosition(minecraft)
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
    val interactive = !InventoryOverlayInput.isPointCovered(
        screen,
        screenMouseX.toDouble(),
        screenMouseY.toDouble(),
    )
    val position = partyDisplayConfig.position
    val scale = position.effectiveScale
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledAnchorHeight = (renderable.anchorHeight * scale).roundToInt()
    val x = position.getAbsX0AllowingOverflow(scaledWidth)
    val y = position.getAbsY0AllowingOverflow(scaledAnchorHeight)
    val localMouseX = OverlayControlMouse.localCoordinate(normalMouseX, x, scale)
    val localMouseY = OverlayControlMouse.localCoordinate(normalMouseY, y, scale)
    val placePanelRight = partyDisplayConfig.details.alignment != PartyDisplayAlignment.RIGHT &&
        x + ((renderable.width + MEMBER_PANEL_GAP + MEMBER_PANEL_WIDTH) * scale).roundToInt() <=
        window.guiScaledWidth

    context.nextStratum()
    val localControl = context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(scale, scale)
        val displayControl = renderable.renderInteractive(
            context,
            localMouseX.takeIf { interactive },
            localMouseY.takeIf { interactive },
        )
        hudControls.renderMemberPanel(
            context,
            members,
            renderable.width,
            placePanelRight,
            localLeader,
            localMouseX.takeIf { interactive },
            localMouseY.takeIf { interactive },
        ) ?: displayControl
    }
    hoveredControl = localControl?.toScreenArea(x, y, scale)
    if (interactive) hoveredControl?.tooltipLines?.takeIf { it.isNotEmpty() }?.let { lines ->
        context.nextStratum()
        SkysoftNativeTooltip.setForNextFrame(
            context,
            lines,
            screenMouseX,
            screenMouseY,
            scrollable = false,
        )
    }
}

private fun renderPositioned(context: GuiGraphicsExtractor, renderable: GuiRenderable) {
    val position = partyDisplayConfig.position
    val scale = position.effectiveScale
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledHeight = (renderable.height * scale).roundToInt()
    val x = position.getAbsX0AllowingOverflow(scaledWidth)
    val y = position.getAbsY0AllowingOverflow(scaledHeight)
    context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(scale, scale)
        renderable.render(context)
    }
}

private fun editorRenderable(): PartyDisplayRenderable? = PartyDisplay.currentMembers()
    .takeIf { it.isNotEmpty() }
    ?.let { members ->
        PartyDisplayRenderable(
            members,
            partyDisplayConfig.details.alignment,
            partyDisplayConfig.details.background,
            inventoryOpen = false,
            localLeader = false,
            lootshareCheckmarks(),
            hudControls,
        )
    }

private fun lootshareCheckmarks(): Map<String, Component> {
    val dianaLootshare = SkysoftConfigGui.config().events.diana.lootshare
    if (
        !partyDisplayConfig.settings.lootshareDisplay ||
        !dianaLootshare.enabled ||
        !dianaLootshare.settings.partyCheckmarks
    ) {
        return emptyMap()
    }
    val checkmarks = DianaLootshareReadyMarkers.readyPlayerNames(System.currentTimeMillis())
        .associateTo(mutableMapOf()) { name -> name.lowercase() to DianaLootshareReadyMarkers.checkmark }
    DianaRareMobSharing.activeSpawnerNames.forEach { name ->
        checkmarks[name.lowercase()] = DianaLootshareReadyMarkers.spawnerCheckmark
    }
    return checkmarks
}

private fun isPartyDisplayVisible(): Boolean {
    val minecraft = Minecraft.getInstance()
    return partyDisplayConfig.enabled &&
        HypixelLocationState.inSkyBlock &&
        PartyDisplay.currentMembers().isNotEmpty() &&
        !MinecraftClient.isGuiHidden(minecraft)
}

private fun clearPartyDisplayInteraction() {
    hoveredControl = null
    hudControls.clear()
}

private class PartyDisplayRenderable(
    private val members: List<PartyDisplayMember>,
    private val alignment: PartyDisplayAlignment,
    background: Boolean,
    private val inventoryOpen: Boolean,
    localLeader: Boolean,
    private val lootshareCheckmarks: Map<String, Component>,
    private val controls: PartyDisplayHudControls,
) : GuiRenderable {
    private val font get() = Minecraft.getInstance().font
    private val title = Component.literal("Party").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
        .append(
            Component.literal(" (${members.count { !it.invited && it.leavingAtMillis == null }})")
                .withStyle(ChatFormatting.GRAY),
        )
    private val renderedAtMillis = System.currentTimeMillis()
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val commands = buildList {
        if (inventoryOpen) add(PartyDisplayCommand.Leave)
        if (inventoryOpen && localLeader) add(PartyDisplayCommand.Disband)
    }
    private val usernameWidth: Int get() = font.width(USERNAME_WIDTH_SAMPLE)
    private val statusWidth: Int get() = font.width(DianaLootshareReadyMarkers.checkmark)
    private val memberWidth: Int get() = HEAD_SIZE + HEAD_GAP + usernameWidth + STATUS_GAP + statusWidth
    private val contentWidth: Int
        get() = maxOf(
            font.width(title),
            memberWidth,
            commands.maxOfOrNull { command -> controls.commandRowWidth(command) } ?: 0,
        )
    private val memberContentHeight: Int
        get() = OverlayTextStyle.TITLE_HEIGHT + members.size * OverlayTextStyle.ROW_HEIGHT
    val anchorHeight: Int get() = memberContentHeight + padding * 2
    override val width: Int get() = contentWidth + padding * 2
    override val height: Int
        get() = anchorHeight + commands.size * OverlayTextStyle.ROW_HEIGHT

    private fun lineX(lineWidth: Int): Int = padding + alignment.offset(contentWidth, lineWidth)

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<PartyDisplayControl>? {
        if (padding > 0) OverlayPanelStyle.draw(context, 0, 0, width, height)
        context.text(font, title, lineX(font.width(title)), padding, TEXT_COLOR, true)
        var hovered: OverlayControlArea<PartyDisplayControl>? = null
        members.forEachIndexed { index, member ->
            val x = lineX(memberWidth)
            val y = padding + OverlayTextStyle.TITLE_HEIGHT + index * OverlayTextStyle.ROW_HEIGHT
            val rowBounds = Rect(padding, y, contentWidth, OverlayTextStyle.ROW_HEIGHT)
            val rowHovered = inventoryOpen && !member.invited && member.leavingAtMillis == null &&
                rowBounds.contains(mouseX, mouseY)
            if (rowHovered) {
                OverlayTextStyle.drawControlHover(context, rowBounds, 1.0)
                hovered = OverlayControlArea(
                    PartyDisplayControl.Manage(member.name),
                    rowBounds,
                    listOf("§eClick §7to manage §e${member.name}"),
                )
            }
            renderMember(context, member, x, y)
        }
        var actionY = padding + memberContentHeight
        commands.forEach { command ->
            controls.renderCommandRow(
                context,
                command,
                Rect(padding, actionY, contentWidth, OverlayTextStyle.ROW_HEIGHT),
                alignment,
                mouseX,
                mouseY,
            )?.let { hovered = it }
            actionY += OverlayTextStyle.ROW_HEIGHT
        }
        return hovered
    }

    private fun renderMember(context: GuiGraphicsExtractor, member: PartyDisplayMember, x: Int, y: Int) {
        val opacity = memberOpacity(member)
        val inactive = member.invited || member.disconnected
        val faceColor = (if (inactive) INACTIVE_MEMBER_COLOR else TEXT_COLOR).withScaledAlpha(opacity)
        val name = displayName(member)
        val nameWidth = font.width(name)
        val checkmark = lootshareCheckmarks[member.name.lowercase()]?.takeIf { !inactive }
        val rightAligned = alignment == PartyDisplayAlignment.RIGHT
        val centeredWidth = HEAD_SIZE + HEAD_GAP + nameWidth +
            if (checkmark == null) 0 else STATUS_GAP + font.width(checkmark)
        val faceX = when (alignment) {
            PartyDisplayAlignment.LEFT -> x
            PartyDisplayAlignment.CENTER -> x + (memberWidth - centeredWidth) / 2
            PartyDisplayAlignment.RIGHT -> x + memberWidth - HEAD_SIZE
        }
        val nameX = if (rightAligned) faceX - HEAD_GAP - nameWidth else faceX + HEAD_SIZE + HEAD_GAP
        PartyDisplay.face(member)?.let { face ->
            PlayerFaceExtractor.extractRenderState(
                context,
                face.texture,
                faceX,
                y,
                HEAD_SIZE,
                face.showHat,
                false,
                faceColor,
            )
        }
        context.text(
            font,
            if (inactive) animatedName(name) else name,
            nameX,
            y,
            TEXT_COLOR.withScaledAlpha(opacity),
            true,
        )
        checkmark?.let {
            context.text(
                font,
                it,
                when (alignment) {
                    PartyDisplayAlignment.LEFT -> nameX + usernameWidth + STATUS_GAP
                    PartyDisplayAlignment.CENTER -> nameX + nameWidth + STATUS_GAP
                    PartyDisplayAlignment.RIGHT -> x
                },
                y,
                TEXT_COLOR.withScaledAlpha(opacity),
                true,
            )
        }
    }

    private fun memberOpacity(member: PartyDisplayMember): Double = member.leavingAtMillis?.let { startedAt ->
        (1.0 - (renderedAtMillis - startedAt).toDouble() / MEMBER_LEAVE_FADE_MILLIS).coerceIn(0.0, 1.0)
    } ?: 1.0

    private fun displayName(member: PartyDisplayMember): Component {
        if (member.name.length <= MAX_USERNAME_LENGTH) return member.component
        val name = member.name.take(MAX_USERNAME_LENGTH - ELLIPSIS.length) + ELLIPSIS
        return Component.literal(name).withStyle(member.component.style)
    }

    private fun animatedName(name: Component): Component {
        val text = name.string
        val rgb = name.style.color?.value ?: (TEXT_COLOR and RGB_MASK)
        val progress = System.currentTimeMillis() % INACTIVE_WAVE_DURATION_MILLIS /
            INACTIVE_WAVE_DURATION_MILLIS.toDouble()
        val wavePosition = -1 - INACTIVE_WAVE_RADIUS +
            progress * (text.length + INACTIVE_WAVE_RADIUS * 2)
        return Component.empty().also { result ->
            text.forEachIndexed { index, character ->
                val strength = (1 - abs(index - wavePosition) / INACTIVE_WAVE_RADIUS).coerceIn(0.0, 1.0)
                val brightness = INACTIVE_BASE_BRIGHTNESS + strength * (1 - INACTIVE_BASE_BRIGHTNESS)
                val red = (((rgb shr RED_SHIFT) and COLOR_CHANNEL_MASK) * brightness).roundToInt()
                val green = (((rgb shr GREEN_SHIFT) and COLOR_CHANNEL_MASK) * brightness).roundToInt()
                val blue = ((rgb and COLOR_CHANNEL_MASK) * brightness).roundToInt()
                result.append(
                    Component.literal(character.toString()).withStyle(
                        name.style.withColor((red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue),
                    ),
                )
            }
        }
    }
}

private class PartyDisplayHudControls {
    private val memberPanelTransition = PanelFadeTransition()
    private val confirmationTransition = PanelFadeTransition()
    private var managedMember: String? = null
    private var pendingCommand: PartyDisplayCommand? = null
    var memberPanelHovered = false
        private set

    fun clear() {
        managedMember = null
        pendingCommand = null
        memberPanelHovered = false
        memberPanelTransition.reset()
        confirmationTransition.reset()
    }

    fun closeMemberPanel() {
        if (managedMember == null) return
        memberPanelTransition.hide()
        clearConfirmation()
    }

    fun commandRowWidth(command: PartyDisplayCommand): Int {
        val font = Minecraft.getInstance().font
        return maxOf(
            font.width(commandLabel(command, enabled = true)),
            font.width(cancelLabel) + CONFIRMATION_GAP + font.width(confirmLabel),
        )
    }

    fun wasClickHandled(control: PartyDisplayControl, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        return when (control) {
            is PartyDisplayControl.Manage -> {
                toggleMember(control.member)
                true
            }
            is PartyDisplayControl.Begin -> {
                pendingCommand = control.command
                confirmationTransition.show()
                true
            }
            is PartyDisplayControl.Cancel -> {
                if (pendingCommand == control.command) confirmationTransition.hide()
                true
            }
            is PartyDisplayControl.Confirm -> wasConfirmationHandled(control.command)
            is PartyDisplayControl.Unavailable -> true
        }
    }

    fun renderMemberPanel(
        context: GuiGraphicsExtractor,
        members: List<PartyDisplayMember>,
        displayWidth: Int,
        placeRight: Boolean,
        localLeader: Boolean,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<PartyDisplayControl>? {
        memberPanelHovered = false
        val managedName = managedMember ?: return null
        val member = members.firstOrNull { candidate ->
            !candidate.invited && candidate.leavingAtMillis == null &&
                candidate.name.equals(managedName, ignoreCase = true)
        }
        if (member == null) {
            clear()
            return null
        }
        val opacity = memberPanelTransition.opacity()
        if (!memberPanelTransition.isVisible) {
            clear()
            return null
        }
        val x = if (placeRight) displayWidth + MEMBER_PANEL_GAP else -MEMBER_PANEL_WIDTH - MEMBER_PANEL_GAP
        val height = MEMBER_PANEL_ROWS * OverlayTextStyle.ROW_HEIGHT + OverlayPanelStyle.PADDING * 2
        memberPanelHovered = Rect(x, 0, MEMBER_PANEL_WIDTH, height).contains(mouseX, mouseY)
        context.fill(
            x,
            0,
            x + MEMBER_PANEL_WIDTH,
            height,
            OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity),
        )
        context.outline(
            x,
            0,
            MEMBER_PANEL_WIDTH,
            height,
            OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity),
        )
        val font = Minecraft.getInstance().font
        val contentX = x + OverlayPanelStyle.PADDING
        val contentWidth = MEMBER_PANEL_WIDTH - OverlayPanelStyle.PADDING * 2
        val textColor = TEXT_COLOR.withScaledAlpha(opacity)
        context.text(
            font,
            member.component.copy().withStyle(ChatFormatting.BOLD),
            contentX,
            OverlayPanelStyle.PADDING,
            textColor,
            true,
        )
        val localPlayerName = Minecraft.getInstance().player?.gameProfile?.name
        val canManage = localLeader && !member.name.equals(localPlayerName, ignoreCase = true)
        val disabledReason = if (!localLeader) {
            "§cOnly the Party Leader can manage members."
        } else {
            "§7You cannot manage yourself."
        }
        var hovered: OverlayControlArea<PartyDisplayControl>? = null
        listOf(
            PartyDisplayCommand.Promote(member.name),
            PartyDisplayCommand.Kick(member.name),
        ).forEachIndexed { index, command ->
            renderCommandRow(
                context,
                command,
                Rect(
                    contentX,
                    OverlayPanelStyle.PADDING + (index + 1) * OverlayTextStyle.ROW_HEIGHT,
                    contentWidth,
                    OverlayTextStyle.ROW_HEIGHT,
                ),
                PartyDisplayAlignment.LEFT,
                mouseX,
                mouseY,
                opacity,
                memberPanelTransition.isInteractive,
                canManage,
                disabledReason,
                fullRowControl = true,
            )?.let { hovered = it }
        }
        return hovered
    }

    fun renderCommandRow(
        context: GuiGraphicsExtractor,
        command: PartyDisplayCommand,
        bounds: Rect,
        alignment: PartyDisplayAlignment,
        mouseX: Int?,
        mouseY: Int?,
        opacity: Double = 1.0,
        interactive: Boolean = true,
        enabled: Boolean = true,
        disabledReason: String = "",
        fullRowControl: Boolean = false,
    ): OverlayControlArea<PartyDisplayControl>? {
        val font = Minecraft.getInstance().font
        val confirmationOpacity = confirmationOpacity(command)
        val confirmationPending = pendingCommand == command
        val label = commandLabel(command, enabled)
        val labelWidth = font.width(label)
        val labelX = bounds.x + alignment.offset(bounds.width, labelWidth)
        val labelBounds = if (fullRowControl) bounds else Rect(labelX, bounds.y, labelWidth, bounds.height)
        val labelHovered = interactive && !confirmationPending && labelBounds.contains(mouseX, mouseY)
        var hovered: OverlayControlArea<PartyDisplayControl>? = when {
            !labelHovered -> null
            enabled -> OverlayControlArea(PartyDisplayControl.Begin(command), labelBounds)
            else -> OverlayControlArea(
                PartyDisplayControl.Unavailable(disabledReason),
                labelBounds,
                listOf(disabledReason),
            )
        }
        if (labelHovered && enabled) {
            OverlayTextStyle.drawControlHover(context, labelBounds, opacity * (1.0 - confirmationOpacity))
        }
        context.text(
            font,
            label,
            labelX,
            bounds.y,
            TEXT_COLOR.withScaledAlpha(opacity * (1.0 - confirmationOpacity)),
            true,
        )
        if (confirmationPending) {
            val cancelBounds = Rect(bounds.x, bounds.y, font.width(cancelLabel), bounds.height)
            val confirmBounds = Rect(
                bounds.x + bounds.width - font.width(confirmLabel),
                bounds.y,
                font.width(confirmLabel),
                bounds.height,
            )
            val confirmationInteractive = interactive && confirmationTransition.isInteractive
            hovered = when {
                confirmationInteractive && cancelBounds.contains(mouseX, mouseY) ->
                    OverlayControlArea(PartyDisplayControl.Cancel(command), cancelBounds)
                confirmationInteractive && confirmBounds.contains(mouseX, mouseY) ->
                    OverlayControlArea(PartyDisplayControl.Confirm(command), confirmBounds)
                else -> null
            }
            hovered?.let { area ->
                OverlayTextStyle.drawControlHover(context, area.bounds, opacity * confirmationOpacity)
            }
            val color = TEXT_COLOR.withScaledAlpha(opacity * confirmationOpacity)
            context.text(font, cancelLabel, cancelBounds.x, cancelBounds.y, color, true)
            context.text(font, confirmLabel, confirmBounds.x, confirmBounds.y, color, true)
        }
        return hovered
    }

    private fun toggleMember(member: String) {
        if (managedMember.equals(member, ignoreCase = true) && !memberPanelTransition.isClosing) {
            memberPanelTransition.hide()
            clearConfirmation()
        } else {
            managedMember = member
            clearConfirmation()
            memberPanelTransition.show()
        }
    }

    private fun wasConfirmationHandled(command: PartyDisplayCommand): Boolean {
        if (pendingCommand != command) return true
        val connection = Minecraft.getInstance().connection ?: return false
        connection.sendCommand(command.serverCommand)
        confirmationTransition.hide()
        if (command is PartyDisplayCommand.Promote || command is PartyDisplayCommand.Kick) {
            memberPanelTransition.hide()
        }
        return true
    }

    private fun confirmationOpacity(command: PartyDisplayCommand): Double {
        if (pendingCommand != command) return 0.0
        val opacity = confirmationTransition.opacity()
        if (!confirmationTransition.isVisible) pendingCommand = null
        return opacity
    }

    private fun clearConfirmation() {
        pendingCommand = null
        confirmationTransition.reset()
    }
}

private sealed interface PartyDisplayCommand {
    val serverCommand: String

    data object Leave : PartyDisplayCommand {
        override val serverCommand = "party leave"
    }

    data object Disband : PartyDisplayCommand {
        override val serverCommand = "party disband"
    }

    data class Promote(val member: String) : PartyDisplayCommand {
        override val serverCommand = "party transfer $member"
    }

    data class Kick(val member: String) : PartyDisplayCommand {
        override val serverCommand = "party kick $member"
    }
}

private sealed interface PartyDisplayControl {
    data class Manage(val member: String) : PartyDisplayControl
    data class Begin(val command: PartyDisplayCommand) : PartyDisplayControl
    data class Cancel(val command: PartyDisplayCommand) : PartyDisplayControl
    data class Confirm(val command: PartyDisplayCommand) : PartyDisplayControl
    data class Unavailable(val reason: String) : PartyDisplayControl
}

private fun commandLabel(command: PartyDisplayCommand, enabled: Boolean): Component {
    val (text, color) = when (command) {
        PartyDisplayCommand.Leave -> "[Leave]" to ChatFormatting.RED
        PartyDisplayCommand.Disband -> "[Disband]" to ChatFormatting.RED
        is PartyDisplayCommand.Promote -> "Promote to Leader" to ChatFormatting.YELLOW
        is PartyDisplayCommand.Kick -> "Kick" to ChatFormatting.RED
    }
    return Component.literal(text).withStyle(if (enabled) color else ChatFormatting.GRAY)
}

private fun PartyDisplayAlignment.offset(containerWidth: Int, contentWidth: Int): Int = when (this) {
    PartyDisplayAlignment.LEFT -> 0
    PartyDisplayAlignment.CENTER -> (containerWidth - contentWidth) / 2
    PartyDisplayAlignment.RIGHT -> containerWidth - contentWidth
}

private fun Rect.contains(mouseX: Int?, mouseY: Int?): Boolean =
    mouseX != null && mouseY != null && contains(mouseX, mouseY)

private fun OverlayControlArea<PartyDisplayControl>.toScreenArea(
    x: Int,
    y: Int,
    scale: Float,
): OverlayControlArea<PartyDisplayControl> = copy(
    bounds = Rect(
        x = x + (bounds.x * scale).roundToInt(),
        y = y + (bounds.y * scale).roundToInt(),
        width = (bounds.width * scale).roundToInt().coerceAtLeast(1),
        height = (bounds.height * scale).roundToInt().coerceAtLeast(1),
    ),
)

private const val HEAD_SIZE = 8
private const val HEAD_GAP = 2
private const val STATUS_GAP = 4
private const val MAX_USERNAME_LENGTH = 16
private const val USERNAME_WIDTH_SAMPLE = "WWWWWWWWWWWWWWWW"
private const val ELLIPSIS = "..."
private const val CONFIRMATION_GAP = 8
private const val MEMBER_PANEL_WIDTH = 130
private const val MEMBER_PANEL_ROWS = 3
private const val MEMBER_PANEL_GAP = 4
private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val INACTIVE_MEMBER_COLOR = 0x80FFFFFF.toInt()
private const val INACTIVE_WAVE_DURATION_MILLIS = 2_000L
private const val INACTIVE_WAVE_RADIUS = 2.5
private const val INACTIVE_BASE_BRIGHTNESS = 0.5
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val COLOR_CHANNEL_MASK = 0xFF
