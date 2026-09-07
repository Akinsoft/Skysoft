package com.skysoft.features.inventory

import com.skysoft.utils.renderables.withIsolatedPose
import com.skysoft.config.InventoryButtonClickType
import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.normalizedInventoryButtonScale
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.ColorUtilities.withAlpha
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import java.util.Locale
import kotlin.math.max
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

private const val ADD_ICON_X_OFFSET = 6
private const val ADD_ICON_Y_OFFSET = 5

object InventoryButtonManager {
    const val BUTTON_SIZE = InventoryButtonLayout.BUTTON_SIZE

    private const val BUTTON_INNER_INSET = 1
    private const val BUTTON_HIGHLIGHT_INSET = 2
    private const val BUTTON_HIGHLIGHT_BOTTOM_Y_OFFSET = 7
    private const val SELECTED_OUTLINE_INSET = 1
    private const val SELECTED_OUTLINE_EXTRA_SIZE = 2
    private const val ACTIVE_BUTTON_ALPHA = 0xFF
    private const val INACTIVE_BUTTON_ALPHA = 0xAA
    private const val BUTTON_HOVER_COLOR = 0x35FFFFFF
    private const val BUTTON_SELECTED_COLOR = 0xFF55FFFF.toInt()

    private val config get() = SkysoftConfigGui.config().inventory.inventoryButtons
    private var hoveredButton: InventoryButtonConfig? = null
    private var hoveredMillis = 0L

    fun register() {
        SkyBlockDataRepository.Demand.register("Inventory Buttons") {
            config.enabled || MinecraftClient.screen() is InventoryButtonEditorScreen.EditorScreen
        }
        TabListApi.registerConsumer("Inventory Buttons") { config.enabled }
        InventoryButtonIcons.registerPlayerHeadCacheRefresh({ config.enabled }) {
            config.buttons.asSequence()
                .filter { it.isActive() }
                .mapNotNull { it.icon }
        }
    }

    data class ButtonPlacement(
        val index: Int,
        val button: InventoryButtonConfig,
        val bounds: Rect,
    )

    @JvmStatic
    fun render(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!shouldRender(screen)) {
            clearHover()
            return
        }

        val placements = placements(screen, includeInactive = false)
        var hovered: InventoryButtonConfig? = null
        for (placement in placements) {
            val hoveredNow = placement.bounds.contains(mouseX, mouseY)
            drawButton(context, placement.bounds.x, placement.bounds.y, placement.button, active = true, hovered = hoveredNow)
            if (hoveredNow) hovered = placement.button
        }

        if (hovered == null) {
            clearHover()
            return
        }

        val now = System.currentTimeMillis()
        if (hoveredButton !== hovered) {
            hoveredButton = hovered
            hoveredMillis = now
        }
        if (now - hoveredMillis >= config.details.tooltipDelay) {
            val command = displayCommand(hovered.command)
            val tooltip = buildList {
                add(Component.literal(command).withStyle(ChatFormatting.GRAY))
                if (hovered.requiredKey != GLFW.GLFW_KEY_UNKNOWN) {
                    add(
                        Component.literal("Hold ${InputUtilities.bindingName(hovered.requiredKey)} to use")
                            .withStyle(ChatFormatting.YELLOW),
                    )
                }
            }
            context.setComponentTooltipForNextFrame(
                Minecraft.getInstance().font,
                tooltip,
                mouseX,
                mouseY,
            )
        }
    }

    @JvmStatic
    fun handleMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult {
        if (!shouldRender(screen)) return InputHandlingResult.IGNORED
        if (config.settings.clickType != InventoryButtonClickType.MOUSE_DOWN) return InputHandlingResult.IGNORED
        return activateButtonAtClick(screen, click)
    }

    @JvmStatic
    fun handleMouseRelease(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult {
        if (!shouldRender(screen)) return InputHandlingResult.IGNORED
        if (config.settings.clickType != InventoryButtonClickType.MOUSE_UP) return InputHandlingResult.IGNORED
        return activateButtonAtClick(screen, click)
    }

    fun placements(screen: AbstractContainerScreen<*>, includeInactive: Boolean): List<ButtonPlacement> {
        val accessor = screen as AbstractContainerScreenAccessor
        val reserved = ItemListController.reservedBounds(screen)
        return placements(
            left = accessor.skysoftGetLeftPos(),
            top = accessor.skysoftGetTopPos(),
            imageWidth = accessor.skysoftGetImageWidth(),
            imageHeight = accessor.skysoftGetImageHeight(),
            playerInventory = screen is InventoryScreen,
            includeInactive = includeInactive,
        ).filterNot { placement -> reserved?.intersects(placement.bounds) == true }
    }

    fun placements(
        left: Int,
        top: Int,
        imageWidth: Int,
        imageHeight: Int,
        playerInventory: Boolean,
        includeInactive: Boolean,
    ): List<ButtonPlacement> {
        val canvas = InventoryButtonCanvas(Rect(left, top, imageWidth, imageHeight), playerInventory)
        return config.buttons.mapIndexedNotNull { index, button ->
            if (!includeInactive && !button.isActive()) return@mapIndexedNotNull null
            if (button.playerInvOnly && !playerInventory) return@mapIndexedNotNull null
            val bounds = InventoryButtonLayout.buttonBounds(canvas, button)
            if (canvas.overlapsContainer(bounds)) return@mapIndexedNotNull null
            ButtonPlacement(index, button, bounds)
        }
    }

    fun moveButton(screen: AbstractContainerScreen<*>, index: Int, screenX: Int, screenY: Int) {
        val button = config.buttons.getOrNull(index) ?: return
        val accessor = screen as AbstractContainerScreenAccessor
        val left = accessor.skysoftGetLeftPos()
        val top = accessor.skysoftGetTopPos()
        val imageWidth = accessor.skysoftGetImageWidth()
        val imageHeight = accessor.skysoftGetImageHeight()
        val playerInventory = screen is InventoryScreen
        InventoryButtonLayout.moveButton(
            InventoryButtonCanvas(Rect(left, top, imageWidth, imageHeight), playerInventory),
            button,
            screenX,
            screenY,
        )
    }

    fun drawButton(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        button: InventoryButtonConfig,
        active: Boolean,
        hovered: Boolean,
        selected: Boolean = false,
        renderScale: Float = button.scale,
    ) {
        val scale = normalizedInventoryButtonScale(renderScale)
        context.withIsolatedPose {
            context.pose().translate(x.toFloat(), y.toFloat())
            context.pose().scale(scale, scale)
            drawButtonContents(context, button, active, hovered, selected)
        }
    }

    fun drawButtonBackground(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        backgroundIndex: Int,
        active: Boolean,
        hovered: Boolean,
        selected: Boolean = false,
    ) {
        val style = buttonStyles[backgroundIndex.coerceIn(buttonStyles.indices)]
        val alpha = if (active) ACTIVE_BUTTON_ALPHA else INACTIVE_BUTTON_ALPHA
        context.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, style.border.withAlpha(alpha))
        context.fill(
            x + BUTTON_INNER_INSET,
            y + BUTTON_INNER_INSET,
            x + BUTTON_SIZE - BUTTON_INNER_INSET,
            y + BUTTON_SIZE - BUTTON_INNER_INSET,
            style.fill.withAlpha(alpha),
        )
        context.fill(
            x + BUTTON_HIGHLIGHT_INSET,
            y + BUTTON_HIGHLIGHT_INSET,
            x + BUTTON_SIZE - BUTTON_HIGHLIGHT_INSET,
            y + BUTTON_HIGHLIGHT_BOTTOM_Y_OFFSET,
            style.highlight.withAlpha(alpha),
        )
        if (hovered) {
            context.fill(
                x + BUTTON_INNER_INSET,
                y + BUTTON_INNER_INSET,
                x + BUTTON_SIZE - BUTTON_INNER_INSET,
                y + BUTTON_SIZE - BUTTON_INNER_INSET,
                BUTTON_HOVER_COLOR,
            )
        }
        if (selected) {
            context.outline(
                x - SELECTED_OUTLINE_INSET,
                y - SELECTED_OUTLINE_INSET,
                BUTTON_SIZE + SELECTED_OUTLINE_EXTRA_SIZE,
                BUTTON_SIZE + SELECTED_OUTLINE_EXTRA_SIZE,
                BUTTON_SELECTED_COLOR,
            )
        }
    }

    fun displayCommand(command: String): String {
        val trimmed = command.trim()
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    fun applySkyBlockPreset() {
        val defaults = InventoryButtonDefaults.create()
        val universalSlots = defaults.withIndex()
            .filter { !it.value.playerInvOnly }
            .map { it.index }
        val active = listOf(
            "/craft" to "minecraft:crafting_table",
            "/storage" to "minecraft:chest",
            "/wardrobe" to "minecraft:leather_chestplate",
            "/pets" to "minecraft:bone",
            "/bz" to "minecraft:gold_ingot",
            "/warp hub" to "minecraft:compass",
            "/warp home" to "minecraft:grass_block",
            "/warp dungeon_hub" to "minecraft:diamond_sword",
        ).mapIndexed { index, (command, icon) -> PresetButton(index, command, icon) }
        for (preset in active) {
            val button = defaults.getOrNull(universalSlots.getOrNull(preset.index) ?: continue) ?: continue
            button.command = preset.command
            button.icon = preset.icon
            button.backgroundIndex = preset.index % buttonStyles.size
        }
        config.replaceActiveButtons(defaults)
    }

    fun isAvailableInCurrentLocation(): Boolean = HypixelLocationState.inSkyBlock

    private fun shouldRender(screen: AbstractContainerScreen<*>): Boolean {
        if (!config.enabled || !isAvailableInCurrentLocation()) return false
        if (StorageOverlayController.isActive(screen)) return false
        return config.buttons.any { it.isActive() && (!it.playerInvOnly || screen is InventoryScreen) }
    }

    private fun activateButtonAtClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        val placement = placements(screen, includeInactive = false).firstOrNull { it.bounds.contains(mouseX, mouseY) }
            ?: return InputHandlingResult.IGNORED

        if (placement.button.requiredKey != GLFW.GLFW_KEY_UNKNOWN &&
            !InputUtilities.isBindingDown(placement.button.requiredKey)
        ) {
            return InputHandlingResult.IGNORED
        }
        if (screen.menu.carried.isEmpty) executeCommand(placement.button.command)
        return InputHandlingResult.CONSUMED
    }

    private fun executeCommand(rawCommand: String) {
        val command = rawCommand.trim().removePrefix("/")
        if (command.isNotBlank()) Minecraft.getInstance().connection?.sendCommand(command)
    }

    private fun clearHover() {
        hoveredButton = null
        hoveredMillis = 0L
    }

    private data class ButtonStyle(val border: Int, val fill: Int, val highlight: Int)
    private data class PresetButton(val index: Int, val command: String, val icon: String)

    private val buttonStyles = listOf(
        ButtonStyle(0xFF6B6B6B.toInt(), 0xFF2C2C2C.toInt(), 0xFF404040.toInt()),
        ButtonStyle(0xFF2F78C4.toInt(), 0xFF143352.toInt(), 0xFF1E4D7A.toInt()),
        ButtonStyle(0xFF43A047.toInt(), 0xFF173D1A.toInt(), 0xFF255C28.toInt()),
        ButtonStyle(0xFFC78222.toInt(), 0xFF4D310C.toInt(), 0xFF754A12.toInt()),
        ButtonStyle(0xFF9C4DCC.toInt(), 0xFF35184A.toInt(), 0xFF522571.toInt()),
        ButtonStyle(0xFFD84343.toInt(), 0xFF4A1818.toInt(), 0xFF742525.toInt()),
        ButtonStyle(0xFF00A6A6.toInt(), 0xFF083B3B.toInt(), 0xFF0E5D5D.toInt()),
    )


}

private fun drawButtonContents(
    context: GuiGraphicsExtractor,
    button: InventoryButtonConfig,
    active: Boolean,
    hovered: Boolean,
    selected: Boolean,
) {
    InventoryButtonManager.drawButtonBackground(context, 0, 0, button.backgroundIndex, active, hovered, selected)
    if (active) {
        val icon = button.icon?.takeIf { it.isNotBlank() }
        val stack = icon?.let(InventoryButtonIcons::iconStack)
        if (stack != null && !stack.isEmpty) {
            context.item(stack, 1, 1)
            return
        }
        drawFallbackIcon(context, 0, 0, button.command, icon)
    } else {
        val color = if (hovered || selected) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
        context.text(Minecraft.getInstance().font, "+", ADD_ICON_X_OFFSET, ADD_ICON_Y_OFFSET, color, false)
    }
}

private fun drawFallbackIcon(context: GuiGraphicsExtractor, x: Int, y: Int, command: String, icon: String?) {
    val font = Minecraft.getInstance().font
    val explicitText = icon?.takeIf { it.startsWith("text:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.take(EXPLICIT_TEXT_ICON_MAX_LENGTH)
    val text = when {
        explicitText != null -> explicitText
        icon != null -> "!"
        command.isNotBlank() -> command.trim().removePrefix("/").take(COMMAND_FALLBACK_ICON_LENGTH)
        else -> "?"
    }.uppercase(Locale.ROOT)
    val draw = text.take(max(MIN_FALLBACK_ICON_LENGTH, EXPLICIT_TEXT_ICON_MAX_LENGTH - text.length / 2))
    val textX = x + (InventoryButtonManager.BUTTON_SIZE - font.width(draw)) / 2
    val color = if (icon != null && explicitText == null) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
    context.text(font, draw, textX, y + FALLBACK_ICON_TEXT_Y_OFFSET, color, true)
}

private const val EXPLICIT_TEXT_ICON_MAX_LENGTH = 3
private const val COMMAND_FALLBACK_ICON_LENGTH = 2
private const val MIN_FALLBACK_ICON_LENGTH = 1
private const val FALLBACK_ICON_TEXT_Y_OFFSET = 5
