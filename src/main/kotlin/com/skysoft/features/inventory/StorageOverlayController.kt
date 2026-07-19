package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.MinecraftItems
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object StorageOverlayController {
    fun register() = registerStorageOverlay()

    @JvmStatic
    fun isActive(screen: AbstractContainerScreen<*>?): Boolean = storageOverlayIsActive(screen)

    @JvmStatic
    fun shouldDimBackground(): Boolean = config.details.dimBackground

    @JvmStatic
    fun layoutScreen(screen: AbstractContainerScreen<*>) {
        val window = Minecraft.getInstance().window
        if (
            GuiScaleController.usesSeparateInventoryScale(screen) &&
            window.guiScale != GuiScaleController.resolve(screen, window).inventory()
        ) {
            return
        }
        storageOverlayLayoutScreen(screen)
    }

    @JvmStatic
    fun renderBackground(
        screen: ContainerScreen,
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
    ) = renderStorageOverlayBackground(screen, context, mouseX, mouseY)

    @JvmStatic
    fun shouldSuppressContainerLabels(screen: AbstractContainerScreen<*>): Boolean =
        shouldSuppressStorageOverlayContainerLabels(screen)

    @JvmStatic
    fun handleMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult =
        handleStorageOverlayMouseClick(screen, click)

    @JvmStatic
    fun handleMouseDrag(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult =
        handleStorageOverlayMouseDrag(screen, click)

    @JvmStatic
    fun handleMouseRelease(click: MouseButtonEvent): InputHandlingResult =
        handleStorageOverlayMouseRelease(click)

    @JvmStatic
    fun handleMouseScroll(
        screen: AbstractContainerScreen<*>,
        mouseX: Double,
        mouseY: Double,
        scrollY: Double,
    ): InputHandlingResult =
        handleStorageOverlayMouseScroll(screen, mouseX, mouseY, scrollY)

    @JvmStatic
    fun shouldPreferMouseScroll(
        screen: AbstractContainerScreen<*>,
        mouseX: Double,
        mouseY: Double,
        scrollY: Double,
    ): Boolean = shouldPreferStorageOverlayMouseScroll(screen, mouseX, mouseY, scrollY)

    @JvmStatic
    fun handleKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): InputHandlingResult =
        handleStorageOverlayKeyPress(screen, event)

    @JvmStatic
    fun handleCharTyped(screen: AbstractContainerScreen<*>, event: CharacterEvent): InputHandlingResult =
        handleStorageOverlayCharTyped(screen, event)

    @JvmStatic
    fun isClickInsideOverlay(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean =
        isStorageOverlayClickInside(screen, mouseX, mouseY)
}

internal val emptyOverviewItems: Set<Item> = buildSet {
    addAll(MinecraftItems.stainedGlassPanes())
    add(MinecraftItems.grayDye())
}

internal val storage get() = ProfileStorageApi.storage
internal val config get() = SkysoftConfigGui.config().inventory.storageOverlay
internal val isStorageOverlayEnabled get() = SkysoftConfigGui.config().inventory.isStorageOverlayEnabled

internal var lastInventoryKey: String? = null
internal val storageSearchField = TextFieldState()
internal var editingTitlePage: Int? = null
internal var editingTitleText = ""
internal var editingTitleSelected = false
internal var lastCommandMillis = 0L
internal var rememberedPageIndex: Int? = null
internal var redirectedOverviewScreenId: Int? = null
internal var focusedPageKey: String? = null
internal var requestedFocusPageIndex: Int? = null
internal var requestedFocusKey: String? = null
internal var preservedScrollPageIndex: Int? = null
internal var pendingOverviewShortcutClick: PendingOverviewShortcutClick? = null
internal val decodedStacks = linkedMapOf<String, ItemStack>()
internal val emptyOverviewStacks = mutableMapOf<Int, ItemStack>()

internal enum class ToolkitType(
    val storageKey: String,
    val pageIndex: Int,
    val title: String,
    val command: String,
    val selectorSlot: Int,
) {
    FARMING(
        "farming",
        StorageToolkit.FARMING_PAGE_INDEX,
        "Farming Toolkit",
        "farmingtoolkit",
        StorageToolkit.FARMING_SELECTOR_SLOT,
    ),
    HUNTING(
        "hunting",
        StorageToolkit.HUNTING_PAGE_INDEX,
        "Hunting Toolkit",
        "huntingtoolkit",
        StorageToolkit.HUNTING_SELECTOR_SLOT,
    ),
    ;

    fun shortcutTitle(isAvailable: Boolean): String = if (isAvailable) title else "Locked $title"

    fun shortcutTooltip(): List<String> = listOf(
        "§a$title",
        "§7Store all of your ${title.removeSuffix(" Toolkit")} Tools in one convenient place.",
        "",
        "§eClick to open the $title!",
    )

    companion object {
        fun fromTitle(title: String): ToolkitType? = entries.firstOrNull { it.title == title }

        fun fromPageIndex(pageIndex: Int): ToolkitType? = entries.firstOrNull { it.pageIndex == pageIndex }
    }
}

internal sealed interface StorageHandle {
    data object Overview : StorageHandle
    data class Page(val pageIndex: Int, val rows: Int) : StorageHandle
    data class Rift(val pageIndex: Int, val rows: Int) : StorageHandle
    data class Toolkit(val type: ToolkitType, val rows: Int) : StorageHandle
}

internal data class Measurements(
    val storageX: Int,
    val storageY: Int,
    val storageWidth: Int,
    val storageHeight: Int,
    val scrollPanel: Rect,
    val scrollbar: Rect,
    val search: Rect,
    val playerBounds: Rect,
    val selectorBounds: Rect,
    val totalBounds: Rect,
    val columns: Int,
    val isSelectorVisible: Boolean = false,
)

internal data class PageLayoutResult(
    val pages: Map<Int, PageLayout>,
    val contentHeight: Int,
)

internal data class StorageOverlayLayoutState(
    val handle: StorageHandle,
    val measurements: Measurements,
    val pageLayoutResult: PageLayoutResult,
)

internal data class PageLayout(
    val pageIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean = Rect(x, y, width, height).contains(mouseX, mouseY)
    fun isVerticallyVisibleWithin(rect: Rect): Boolean =
        y >= rect.y && y + height <= rect.y + rect.height

    fun intersects(rect: Rect): Boolean =
        x < rect.x + rect.width &&
            x + width > rect.x &&
            y < rect.y + rect.height &&
            y + height > rect.y
}

internal data class SlotClickAction(val button: Int, val input: ContainerInput)

internal data class PendingOverviewShortcutClick(
    val pageIndex: Int,
    val button: Int,
    val requestedAtMillis: Long,
)

internal val enderChestTitlePattern = Regex("""^Ender Chest (?:✦ )?\(([1-9])/[1-9]\)$""")
internal val backpackTitlePattern = Regex("""^.+Backpack (?:✦ )?\(Slot #([0-9]+)\)$""")
internal val riftStorageTitlePattern = Regex("""^Rift Storage \(([1-2])/2\)$""")
