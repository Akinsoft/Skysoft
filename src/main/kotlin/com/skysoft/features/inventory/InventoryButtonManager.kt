package com.skysoft.features.inventory

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.skysoft.SkysoftMod
import com.skysoft.config.DEFAULT_INVENTORY_BUTTON_SCALE
import com.skysoft.config.INVENTORY_BUTTON_SCALE_STEP
import com.skysoft.config.InventoryButtonClickType
import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.MAX_INVENTORY_BUTTON_SCALE
import com.skysoft.config.MIN_INVENTORY_BUTTON_SCALE
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.pets.PetRepository
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import org.lwjgl.glfw.GLFW

private const val ADD_ICON_X_OFFSET = 6
private const val ADD_ICON_Y_OFFSET = 5

object InventoryButtonManager {
    const val BUTTON_SIZE = 18

    private const val BUTTON_INNER_INSET = 1
    private const val BUTTON_HIGHLIGHT_INSET = 2
    private const val BUTTON_HIGHLIGHT_BOTTOM_Y_OFFSET = 7
    private const val SELECTED_OUTLINE_INSET = 1
    private const val SELECTED_OUTLINE_EXTRA_SIZE = 2
    private const val ACTIVE_BUTTON_ALPHA_MASK = 0xFF000000.toInt()
    private const val INACTIVE_BUTTON_ALPHA_MASK = 0xAA000000.toInt()
    private const val BUTTON_RGB_MASK = 0x00FFFFFF
    private const val BUTTON_HOVER_COLOR = 0x35FFFFFF
    private const val BUTTON_SELECTED_COLOR = 0xFF55FFFF.toInt()

    private val config get() = SkysoftConfigGui.config().inventory.inventoryButtons
    private var hoveredButton: InventoryButtonConfig? = null
    private var hoveredMillis = 0L

    fun register() {
        SkyBlockDataRepository.Demand.register("Inventory Buttons") { config.enabled }
        PetRepository.registerConsumer("Inventory Buttons") { config.enabled }
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

    data class IconCandidate(
        val id: String,
        val displayName: String,
        val stack: ItemStack,
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
            context.setTooltipForNextFrame(
                Minecraft.getInstance().font,
                Component.literal(command).withStyle(ChatFormatting.GRAY),
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

    fun resetButtonPosition(index: Int) {
        val button = config.buttons.getOrNull(index) ?: return
        InventoryButtonDefaults.create().getOrNull(index)?.let { default ->
            button.x = default.x
            button.y = default.y
            button.playerInvOnly = default.playerInvOnly
            button.anchorRight = default.anchorRight
            button.anchorBottom = default.anchorBottom
        }
        button.scale = DEFAULT_INVENTORY_BUTTON_SCALE
        InventoryButtonEditorActions.recordReset(index, button.scale)
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
        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(scale, scale)
        drawButtonContents(context, button, active, hovered, selected)
        context.pose().popMatrix()
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
        val alphaMask = if (active) ACTIVE_BUTTON_ALPHA_MASK else INACTIVE_BUTTON_ALPHA_MASK
        context.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, style.border and BUTTON_RGB_MASK or alphaMask)
        context.fill(
            x + BUTTON_INNER_INSET,
            y + BUTTON_INNER_INSET,
            x + BUTTON_SIZE - BUTTON_INNER_INSET,
            y + BUTTON_SIZE - BUTTON_INNER_INSET,
            style.fill and BUTTON_RGB_MASK or alphaMask,
        )
        context.fill(
            x + BUTTON_HIGHLIGHT_INSET,
            y + BUTTON_HIGHLIGHT_INSET,
            x + BUTTON_SIZE - BUTTON_HIGHLIGHT_INSET,
            y + BUTTON_HIGHLIGHT_BOTTOM_Y_OFFSET,
            style.highlight and BUTTON_RGB_MASK or alphaMask,
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

    fun iconStack(icon: String): ItemStack? = InventoryButtonIcons.iconStack(icon)

    fun searchIconCandidates(query: String, limit: Int = 1024): List<IconCandidate> =
        InventoryButtonIcons.searchIconCandidates(query, limit)

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
        config.buttons = defaults
    }

    fun clearIconCache() {
        InventoryButtonIcons.clearIconCache()
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

        if (screen.menu.carried.isEmpty) executeCommand(placement.button.command)
        return InputHandlingResult.CONSUMED
    }

    private fun executeCommand(rawCommand: String) {
        val command = rawCommand.trim().removePrefix("/")
        if (command.isBlank()) return

        val minecraft = Minecraft.getInstance()
        val connection = minecraft.connection ?: return
        val firstLiteral = command.substringBefore(' ')
        val source = connection.suggestionsProvider as? FabricClientCommandSource
        val dispatcher = try {
            ClientCommands.getActiveDispatcher()
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to access client command dispatcher from inventory button: /$command", e)
            source?.let { SkysoftChat.error(it, "Client commands are unavailable. See the log for details.") }
            return
        }
        if (dispatcher == null) {
            SkysoftMod.LOGGER.warn("Client command dispatcher is unavailable from inventory button: /$command")
            source?.let { SkysoftChat.error(it, "Client commands are unavailable. See the log for details.") }
            return
        }
        if (source != null && dispatcher.root.getChild(firstLiteral) != null) {
            try {
                dispatcher.execute(command, source)
            } catch (e: CommandSyntaxException) {
                SkysoftChat.error(source, e.message ?: "Invalid client command")
            } catch (e: Exception) {
                SkysoftMod.LOGGER.warn("Failed to execute client command from inventory button: /$command", e)
                SkysoftChat.error(source, e.message ?: "Failed to execute client command")
            }
            return
        }

        connection.sendCommand(command)
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

    internal val priorityIcons = setOf(
        "minecraft:crafting_table",
        "minecraft:chest",
        "minecraft:ender_chest",
        "minecraft:leather_chestplate",
        "minecraft:bone",
        "minecraft:gold_ingot",
        "minecraft:compass",
        "minecraft:emerald",
        "minecraft:diamond_sword",
        "minecraft:nether_star",
        "minecraft:book",
        "minecraft:command_block",
    )

    internal val iconAliases = mapOf(
        "WORKBENCH" to "minecraft:crafting_table",
        "CRAFTING_TABLE" to "minecraft:crafting_table",
        "CHEST" to "minecraft:chest",
        "ENDER_CHEST" to "minecraft:ender_chest",
        "LEATHER_CHESTPLATE" to "minecraft:leather_chestplate",
        "BONE" to "minecraft:bone",
        "GOLD_BARDING" to "minecraft:golden_horse_armor",
        "GOLD_BLOCK" to "minecraft:gold_block",
        "EMPTY_MAP" to "minecraft:map",
        "RAW_FISH" to "minecraft:cod",
        "FISHING_ROD" to "minecraft:fishing_rod",
        "EMERALD" to "minecraft:emerald",
        "IRON_SWORD" to "minecraft:iron_sword",
        "POTION" to "minecraft:potion",
        "NETHER_STAR" to "minecraft:nether_star",
        "PAINTING" to "minecraft:painting",
        "COMMAND" to "minecraft:command_block",
        "BOOK" to "minecraft:book",
    )
}

internal enum class InventoryButtonResetShortcutResult {
    IGNORED,
    RESET,
    REMOVED,
}

internal object InventoryButtonEditorActions {
    private val config get() = SkysoftConfigGui.config().inventory.inventoryButtons

    internal var lastOutcome = "none"
        private set

    fun changeButtonScale(index: Int, scrollY: Double): InputHandlingResult {
        val button = config.buttons.getOrNull(index) ?: return InputHandlingResult.IGNORED
        if (scrollY == 0.0) return InputHandlingResult.IGNORED
        val oldScale = normalizedInventoryButtonScale(button.scale)
        button.scale = inventoryButtonScaleAfterScroll(oldScale, scrollY)
        lastOutcome = "resize index=$index oldScale=$oldScale newScale=${button.scale} scrollY=$scrollY"
        return InputHandlingResult.CONSUMED
    }

    fun addButtonSlot(): Int {
        val button = InventoryButtonLayout.nextEditorButtonSlot(config.buttons)
        config.buttons.add(button)
        val index = config.buttons.lastIndex
        lastOutcome = "add index=$index x=${button.x} y=${button.y}"
        return index
    }

    fun resetOrRemoveButton(index: Int): InventoryButtonResetShortcutResult {
        val button = config.buttons.getOrNull(index) ?: return InventoryButtonResetShortcutResult.IGNORED
        if (button.isUserCreated == true) {
            config.buttons.removeAt(index)
            lastOutcome = "remove index=$index x=${button.x} y=${button.y}"
            return InventoryButtonResetShortcutResult.REMOVED
        }
        InventoryButtonManager.resetButtonPosition(index)
        return InventoryButtonResetShortcutResult.RESET
    }

    fun recordReset(index: Int, scale: Float) {
        lastOutcome = "reset index=$index scale=$scale"
    }
}

internal object InventoryButtonLayout {
    internal val editorAddButtonBounds = Rect(
        EDITOR_INVENTORY_WIDTH + EDITOR_ADD_BUTTON_GAP,
        EDITOR_ADD_BUTTON_Y,
        InventoryButtonManager.BUTTON_SIZE,
        InventoryButtonManager.BUTTON_SIZE,
    )

    fun buttonBounds(canvas: InventoryButtonCanvas, button: InventoryButtonConfig): Rect {
        val point = canvas.position(button)
        val size = inventoryButtonSizeAtScale(button.scale)
        val growth = size - InventoryButtonManager.BUTTON_SIZE
        val x = if (point.x + InventoryButtonManager.BUTTON_SIZE <= canvas.container.x) point.x - growth else point.x
        val y = if (point.y + InventoryButtonManager.BUTTON_SIZE <= canvas.container.y) point.y - growth else point.y
        return Rect(x, y, size, size)
    }

    fun moveButton(
        canvas: InventoryButtonCanvas,
        button: InventoryButtonConfig,
        screenX: Int,
        screenY: Int,
    ) {
        val size = inventoryButtonSizeAtScale(button.scale)
        val growth = size - InventoryButtonManager.BUTTON_SIZE
        val baseX = if (screenX + size <= canvas.container.x) screenX + growth else screenX
        val baseY = if (screenY + size <= canvas.container.y) screenY + growth else screenY
        canvas.move(button, baseX, baseY)
    }

    fun nextEditorButtonSlot(buttons: List<InventoryButtonConfig>): InventoryButtonConfig {
        val canvas = InventoryButtonCanvas(
            Rect(0, 0, EDITOR_INVENTORY_WIDTH, InventoryButtonDefaults.PLAYER_INVENTORY_HEIGHT),
            playerInventory = true,
        )
        val occupied = buttons.map { button -> buttonBounds(canvas, button) }
        var x = editorAddButtonBounds.x + editorAddButtonBounds.width + EDITOR_BUTTON_GAP
        while (occupied.any { it.intersects(Rect(x, EDITOR_ADD_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) }) {
            x += BUTTON_SIZE + EDITOR_BUTTON_GAP
        }
        return InventoryButtonConfig(x = x, y = EDITOR_ADD_BUTTON_Y, isUserCreated = true)
    }

    private const val BUTTON_SIZE = InventoryButtonManager.BUTTON_SIZE
    private const val EDITOR_INVENTORY_WIDTH = 176
    private const val EDITOR_ADD_BUTTON_GAP = 2
    private const val EDITOR_ADD_BUTTON_Y = -19
    private const val EDITOR_BUTTON_GAP = 2
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
        val stack = icon?.let(InventoryButtonManager::iconStack)
        if (stack != null && !stack.isEmpty) {
            context.item(stack, 1, 1)
            return
        }
        InventoryButtonIcons.drawFallbackIcon(context, 0, 0, button.command, icon)
    } else {
        val color = if (hovered || selected) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
        context.text(Minecraft.getInstance().font, "+", ADD_ICON_X_OFFSET, ADD_ICON_Y_OFFSET, color, false)
    }
}

internal fun normalizedInventoryButtonScale(scale: Float): Float = scale
    .takeIf(Float::isFinite)
    ?.takeIf { it > 0f }
    ?.coerceIn(MIN_INVENTORY_BUTTON_SCALE, MAX_INVENTORY_BUTTON_SCALE)
    ?: DEFAULT_INVENTORY_BUTTON_SCALE

internal fun inventoryButtonScaleAfterScroll(scale: Float, scrollY: Double): Float {
    val current = normalizedInventoryButtonScale(scale)
    if (scrollY == 0.0) return current
    val direction = if (scrollY > 0.0) 1 else -1
    val stepsPerUnit = (1f / INVENTORY_BUTTON_SCALE_STEP).roundToInt()
    val steps = (current * stepsPerUnit).roundToInt() + direction
    return (steps / stepsPerUnit.toFloat())
        .coerceIn(MIN_INVENTORY_BUTTON_SCALE, MAX_INVENTORY_BUTTON_SCALE)
}

internal fun inventoryButtonSizeAtScale(scale: Float): Int =
    (InventoryButtonManager.BUTTON_SIZE * normalizedInventoryButtonScale(scale)).roundToInt().coerceAtLeast(1)

private object InventoryButtonIcons {
    private const val PLAYER_HEAD_CACHE_REFRESH_INTERVAL_MILLIS = 4 * 60 * 1000L

    private val iconStackCache = linkedMapOf<String, ItemStack>()
    private val playerNamePattern = Regex("^[A-Za-z0-9_]{3,16}$")
    private val playerIconPrefixes = listOf("player", "head", "skull")
    private var nextPlayerHeadCacheRefreshMillis = 0L
    private const val EXPLICIT_TEXT_ICON_MAX_LENGTH = 3
    private const val COMMAND_FALLBACK_ICON_LENGTH = 2
    private const val MIN_FALLBACK_ICON_LENGTH = 1
    private const val FALLBACK_ICON_TEXT_Y_OFFSET = 5

    fun registerPlayerHeadCacheRefresh(
        isActive: () -> Boolean,
        activeIcons: () -> Sequence<String>,
    ) {
        SkysoftClientEvents.onEndTick("Inventory Button player head cache", isActive) tick@{ minecraft ->
            if (minecraft.connection == null) {
                nextPlayerHeadCacheRefreshMillis = 0L
                return@tick
            }

            val now = System.currentTimeMillis()
            if (now < nextPlayerHeadCacheRefreshMillis) return@tick
            nextPlayerHeadCacheRefreshMillis = now + PLAYER_HEAD_CACHE_REFRESH_INTERVAL_MILLIS

            activeIcons()
                .mapNotNull(::iconStack)
                .filter { it.item == Items.PLAYER_HEAD }
                .mapNotNull { it.get(DataComponents.PROFILE) }
                .distinct()
                .forEach { minecraft.playerSkinRenderCache().lookup(it) }
        }
    }

    fun iconStack(icon: String): ItemStack? {
        val trimmed = icon.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("text:", ignoreCase = true)) return null
        inventoryButtonTextureHash(trimmed)?.let { textureHash ->
            return iconStackCache.getOrPut("skull:$textureHash") {
                texturedHeadStack(textureHash)
            }.copy()
        }
        playerNameFromIcon(trimmed)?.let { playerName ->
            return iconStackCache.getOrPut("player:${playerName.lowercase(Locale.ROOT)}") {
                playerHeadStack(playerName)
            }.copy()
        }
        skyBlockInternalName(trimmed)?.let { internalName ->
            return PetRepository.itemStackOrNull(internalName)
        }
        return iconStackCache.getOrPut(trimmed.lowercase(Locale.ROOT)) {
            resolveItem(trimmed)?.let { ItemStack(it) } ?: ItemStack.EMPTY
        }.takeUnless { it.isEmpty }
    }

    fun searchIconCandidates(query: String, limit: Int): List<InventoryButtonManager.IconCandidate> {
        explicitPlayerNameQuery(query)?.let { playerName ->
            return listOf(playerHeadCandidate(playerName)).take(limit)
        }

        val results = linkedMapOf<String, InventoryButtonManager.IconCandidate>()
        vanillaIconCandidates(query, limit).forEach { candidate ->
            results.putIfAbsent(candidate.id, candidate)
        }
        if (query.isNotBlank()) {
            PetRepository.searchItemIconCandidates(query, limit).forEach { candidate ->
                val id = "skyblock:${candidate.internalName}"
                results.putIfAbsent(
                    id,
                    InventoryButtonManager.IconCandidate(id, candidate.displayName, candidate.stack),
                )
            }
        }
        playerIconCandidates(query, limit).forEach { candidate ->
            results.putIfAbsent(candidate.id, candidate)
        }
        return results.values.take(limit).toList()
    }

    fun drawFallbackIcon(context: GuiGraphicsExtractor, x: Int, y: Int, command: String, icon: String?) {
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

    fun clearIconCache() {
        iconStackCache.clear()
        nextPlayerHeadCacheRefreshMillis = 0L
    }

    private fun playerIconCandidates(query: String, limit: Int): List<InventoryButtonManager.IconCandidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val words = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = linkedMapOf<String, InventoryButtonManager.IconCandidate>()

        fun add(candidate: InventoryButtonManager.IconCandidate) {
            results.putIfAbsent(candidate.id.lowercase(Locale.ROOT), candidate)
        }

        Minecraft.getInstance().connection?.listedOnlinePlayers.orEmpty()
            .asSequence()
            .map { it.profile }
            .filter { profile -> profile.name.isNotBlank() && matchesPlayerQuery(profile.name, words) }
            .take(limit)
            .forEach { profile -> add(playerHeadCandidate(profile.name, profile)) }

        typedPlayerNameQuery(trimmed)?.let { playerName ->
            add(playerHeadCandidate(playerName))
        }

        return results.values.take(limit).toList()
    }

    private fun playerHeadCandidate(
        playerName: String,
        profile: GameProfile? = null,
    ): InventoryButtonManager.IconCandidate = InventoryButtonManager.IconCandidate(
        id = "player:$playerName",
        displayName = "$playerName's Head",
        stack = playerHeadStack(playerName, profile),
    )

    private fun playerHeadStack(playerName: String, profile: GameProfile? = null): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(
            DataComponents.PROFILE,
            if (profile != null) ResolvableProfile.createResolved(profile) else ResolvableProfile.createUnresolved(playerName),
        )
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("$playerName's Head"))
        return stack
    }

    private fun texturedHeadStack(textureHash: String): ItemStack {
        val textureJson = """{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/$textureHash"}}}"""
        val textureValue = Base64.getEncoder().encodeToString(textureJson.toByteArray(StandardCharsets.UTF_8))
        val profile = GameProfile(
            UUID.nameUUIDFromBytes(textureHash.toByteArray(StandardCharsets.UTF_8)),
            "Skysoft",
        )
        profile.properties.put("textures", Property("textures", textureValue))
        return ItemStack(Items.PLAYER_HEAD).apply {
            set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
        }
    }

    private fun playerNameFromIcon(icon: String): String? = explicitPlayerNameQuery(icon)

    private fun explicitPlayerNameQuery(query: String): String? {
        val trimmed = query.trim()
        val prefix = playerIconPrefixes.firstOrNull { trimmed.startsWith("$it:", ignoreCase = true) } ?: return null
        return typedPlayerNameQuery(trimmed.substring(prefix.length + 1))
    }

    private fun typedPlayerNameQuery(query: String): String? {
        val name = query.trim().removePrefix("@").takeIf { it.isNotBlank() } ?: return null
        return name.takeIf { playerNamePattern.matches(it) }
    }

    private fun matchesPlayerQuery(playerName: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        val searchable = playerName.lowercase(Locale.ROOT)
        return words.all { it in searchable }
    }

    private fun vanillaIconCandidates(query: String, limit: Int): List<InventoryButtonManager.IconCandidate> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val words = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        return BuiltInRegistries.ITEM.keySet().asSequence()
            .mapNotNull { id ->
                val item = BuiltInRegistries.ITEM.getValue(id)
                if (item == Items.AIR) return@mapNotNull null
                val stack = ItemStack(item)
                val display = stack.hoverName.string
                val searchable = buildString {
                    append(id.toString()).append(' ')
                    append(id.path.replace('_', ' ')).append(' ')
                    append(display.lowercase(Locale.ROOT))
                }.lowercase(Locale.ROOT)
                if (words.isNotEmpty() && words.any { it !in searchable }) return@mapNotNull null
                InventoryButtonManager.IconCandidate(id.toString(), display, stack)
            }
            .sortedWith(
                compareByDescending<InventoryButtonManager.IconCandidate> {
                    it.id in InventoryButtonManager.priorityIcons
                }.thenBy { it.displayName },
            )
            .take(limit)
            .toList()
    }

    private fun skyBlockInternalName(icon: String): String? {
        if (icon.startsWith("skyblock:", ignoreCase = true)) return icon.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        if (':' in icon) return null
        if (resolveItem(icon) != null) return null
        return icon.trim().uppercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }

    private fun resolveItem(icon: String): Item? {
        val id = inventoryButtonVanillaIconIdentifier(icon) ?: return null
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null)
    }
}

internal fun inventoryButtonVanillaIconIdentifier(icon: String): Identifier? {
    val alias = InventoryButtonManager.iconAliases[icon.uppercase(Locale.ROOT)] ?: icon
    val normalized = when {
        ':' in alias -> alias.lowercase(Locale.ROOT)
        alias.startsWith("minecraft/", ignoreCase = true) ->
            "minecraft:${alias.substringAfter('/').lowercase(Locale.ROOT)}"
        else -> alias.lowercase(Locale.ROOT).replace(' ', '_')
    }
    return Identifier.tryParse(normalized)
}
