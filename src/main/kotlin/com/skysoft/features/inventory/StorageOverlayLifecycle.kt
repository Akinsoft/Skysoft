package com.skysoft.features.inventory

import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.gui.nonPlayerSlots
import com.skysoft.utils.gui.textAfterDeletingPreviousWord
import com.skysoft.utils.input.InputHandlingResult
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

internal fun registerStorageOverlay() {
    SkyBlockProfileApi.onProfileChange { resetTransientState() }
    ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> resetTransientState() }
    registerStorageOverlayChat()
    ClientTickEvents.END_CLIENT_TICK.register {
        onClientTick()
    }
    registerMouseClickInterceptor()
}

internal fun registerMouseClickInterceptor() {
    ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
        if (screen is AbstractContainerScreen<*>) {
            ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
                handlePreScreenMouseClick(screen, click) == InputHandlingResult.IGNORED
            }
        }
    }
}

internal fun storageOverlayIsActive(screen: AbstractContainerScreen<*>?): Boolean = handleFor(screen) != null

internal fun storageOverlayLayoutScreen(
    screen: AbstractContainerScreen<*>,
    shouldReadScreen: Boolean = true,
): StorageOverlayLayoutState? {
    val handle = handleFor(screen) ?: run {
        restoreStorageOverlaySlots(screen)
        return null
    }
    if (shouldReadScreen) readScreen(screen, handle)
    rememberActivePage(handle)
    redirectToRememberedPage(screen, handle)
    advanceStorageScroll()

    val measurements = measurements(screen.width, screen.height)
    val activePage = handle.entryIndex()
    var pageLayoutResult = pageLayouts(measurements, activePage)
    if (centerActivePageIfNeeded(activePage, measurements, pageLayoutResult) == PageLayoutRefresh.REQUIRED) {
        pageLayoutResult = pageLayouts(measurements, activePage)
    }
    val pageLayouts = pageLayoutResult.pages

    val accessor = screen as AbstractContainerScreenAccessor
    val left = accessor.skysoftGetLeftPos()
    val top = accessor.skysoftGetTopPos()
    val playerInventory = Minecraft.getInstance().player?.inventory
    for (slot in screen.menu.slots) {
        val position = when {
            playerInventory != null && slot.container === playerInventory ->
                playerSlotPosition(measurements, slot.containerSlot)

            handle.gridRows() != null ->
                pageSlotPosition(
                    measurements,
                    handle,
                    handle.entryIndex()?.let { pageLayouts[it] },
                    slot,
                )

            else -> null
        }
        moveStorageOverlaySlot(
            screen,
            slot,
            position?.x?.minus(left) ?: StorageRuntime.OFFSCREEN,
            position?.y?.minus(top) ?: StorageRuntime.OFFSCREEN,
        )
    }
    return StorageOverlayLayoutState(handle, measurements, pageLayoutResult)
}

internal fun renderStorageOverlayBackground(
    screen: ContainerScreen,
    context: GuiGraphicsExtractor,
    mouseX: Int,
    mouseY: Int,
) {
    if (!storageOverlayIsActive(screen)) return
    renderOverlay(screen, context, mouseX, mouseY)
}

internal fun renderOverlay(screen: ContainerScreen, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
    val layoutState = storageOverlayLayoutScreen(screen, shouldReadScreen = false) ?: return
    SmoothSwapping.beginFrame(screen)
    val handle = layoutState.handle
    val measurements = layoutState.measurements
    val activePage = handle.entryIndex()
    val pageLayoutResult = layoutState.pageLayoutResult
    val isPointerOverSettings = storageSettingsContains(
        screen.width,
        screen.height,
        measurements,
        mouseX,
        mouseY,
    )
    val contentMouseX = if (isPointerOverSettings) StorageRuntime.OFFSCREEN else mouseX
    val contentMouseY = if (isPointerOverSettings) StorageRuntime.OFFSCREEN else mouseY

    drawStoragePanel(context, measurements)
    drawPages(
        context,
        screen,
        measurements,
        pageLayoutResult.pages,
        handle.takeIf { it.gridRows() != null },
        contentMouseX,
        contentMouseY,
    )
    drawScrollBar(context, measurements, pageLayoutResult.contentHeight)
    drawSearchBox(context, measurements)
    drawPlayerInventoryPanel(context, screen, measurements, contentMouseX, contentMouseY)
    if (config.settings.miniMenu) {
        drawStorageSelectorPanel(context, measurements, activePage, contentMouseX, contentMouseY)
    }
    drawStorageSettingsPanel(context, screen.width, screen.height, measurements, mouseX, mouseY)
    coerceScroll(measurements, pageLayoutResult.contentHeight)
    SmoothSwapping.render(screen, context)
}

internal fun shouldSuppressStorageOverlayContainerLabels(screen: AbstractContainerScreen<*>): Boolean =
    storageOverlayIsActive(screen)

internal fun handlePreScreenMouseClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): InputHandlingResult {
    val layoutState = storageOverlayLayoutScreen(screen) ?: return InputHandlingResult.IGNORED
    val mouseX = click.x().toInt()
    val mouseY = click.y().toInt()
    if (processStorageSettingsClick(screen, click, layoutState.measurements) == InputHandlingResult.CONSUMED) {
        return InputHandlingResult.CONSUMED
    }
    updateSearchFocusFromClick(layoutState.measurements, mouseX, mouseY)

    if (routeActivePageSlotClick(screen, layoutState.handle, click) == InputHandlingResult.CONSUMED) {
        return InputHandlingResult.CONSUMED
    }

    return handleStorageOverlayMouseClick(screen, click, layoutState)
}

internal fun handleStorageOverlayMouseClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
    preparedLayout: StorageOverlayLayoutState? = null,
): InputHandlingResult {
    val layoutState = preparedLayout ?: storageOverlayLayoutScreen(screen) ?: return InputHandlingResult.IGNORED
    val handle = layoutState.handle
    val measurements = layoutState.measurements
    val activePage = handle.entryIndex()
    val pageLayoutResult = layoutState.pageLayoutResult
    val mouseX = click.x().toInt()
    val mouseY = click.y().toInt()
    if (processStorageSettingsClick(screen, click, measurements) == InputHandlingResult.CONSUMED) {
        return InputHandlingResult.CONSUMED
    }
    updateSearchFocusFromClick(measurements, mouseX, mouseY)

    return when {
        click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && measurements.scrollbar.contains(mouseX, mouseY) -> {
            val maximum = maxScroll(measurements, pageLayoutResult.contentHeight)
            val knob = scrollbarKnobBounds(measurements, pageLayoutResult.contentHeight)
            val dragOffset = if (knob.contains(mouseX, mouseY)) mouseY - knob.y else knob.height / 2
            scrollbarDragOffset = dragOffset
            setStorageScrollFromScrollbar(mouseY, dragOffset, measurements.scrollbar, knob.height, maximum)
            InputHandlingResult.CONSUMED
        }
        pointInSearch(measurements, mouseX, mouseY) -> {
            searchFocused = true
            finishTitleEdit()
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && searchText.isNotEmpty()) {
                searchText = ""
                resetStorageScroll()
                coerceScroll(measurements, pageLayouts(measurements, activePage).contentHeight)
            }
            InputHandlingResult.CONSUMED
        }
        click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && measurements.scrollPanel.contains(mouseX, mouseY) -> {
            val titlePage = titlePageAt(pageLayoutResult.pages, mouseX, mouseY)
            if (titlePage != null) {
                startTitleEdit(titlePage)
                searchFocused = false
                InputHandlingResult.CONSUMED
            } else {
                finishTitleEdit()
                handlePageAreaClick(screen, click, measurements, pageLayoutResult.pages, activePage, mouseX, mouseY)
            }
        }
        config.settings.miniMenu &&
            handleSelectorPageClick(screen, handle, click, measurements, pageLayoutResult, activePage, mouseX, mouseY) ==
            InputHandlingResult.CONSUMED -> InputHandlingResult.CONSUMED
        config.settings.miniMenu && measurements.selectorBounds.contains(mouseX, mouseY) -> InputHandlingResult.CONSUMED
        measurements.playerBounds.contains(mouseX, mouseY) -> InputHandlingResult.IGNORED
        else -> {
            searchFocused = false
            finishTitleEdit()
            handlePageAreaClick(
                screen,
                click,
                measurements,
                pageLayoutResult.pages,
                activePage,
                mouseX,
                mouseY,
            )
        }
    }
}

internal fun handleSelectorPageClick(
    screen: AbstractContainerScreen<*>,
    handle: StorageHandle,
    click: MouseButtonEvent,
    measurements: Measurements,
    pageLayoutResult: PageLayoutResult,
    activePage: Int?,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult {
    val pageIndex = selectorPageAt(measurements, mouseX, mouseY) ?: return InputHandlingResult.IGNORED
    if (
        handle == StorageHandle.Overview &&
        routeOverviewShortcutClick(screen, click.button(), pageIndex) == InputHandlingResult.CONSUMED
    ) {
        storageOverlayLayoutScreen(screen)
        return InputHandlingResult.CONSUMED
    }
    if (
        requestOverviewShortcutClick(screen, click, pageIndex, mouseX, mouseY) == InputHandlingResult.CONSUMED
    ) {
        return InputHandlingResult.CONSUMED
    }
    if (pageIndex == activePage || !screen.menu.carried.isEmpty || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        centerPage(measurements, pageLayoutResult, pageIndex)
    } else {
        tryNavigateTo(screen, pageIndex, mouseX, mouseY)
    }
    storageOverlayLayoutScreen(screen)
    return InputHandlingResult.CONSUMED
}

internal fun routeOverviewShortcutClick(
    screen: AbstractContainerScreen<*>,
    button: Int,
    pageIndex: Int,
): InputHandlingResult {
    val containerSlot = StorageOverviewSlots.slotForPageIndex(pageIndex) ?: return InputHandlingResult.IGNORED
    val slot = screen.nonPlayerSlots().firstOrNull { it.containerSlot == containerSlot }
        ?: return InputHandlingResult.IGNORED
    if (
        slot.item.isEmpty ||
        storageOverviewSlotState(slot.item) == StorageOverviewSlotState.LOCKED ||
        (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
    ) {
        return InputHandlingResult.IGNORED
    }
    redirectedOverviewScreenId = System.identityHashCode(screen)
    (screen as AbstractContainerScreenAccessor).skysoftSlotClicked(
        slot,
        slot.index,
        button,
        ContainerInput.PICKUP,
    )
    screen.skysoftSetSkipNextRelease(true)
    return InputHandlingResult.CONSUMED
}

internal fun handlePageAreaClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
    measurements: Measurements,
    layouts: Map<Int, PageLayout>,
    activePage: Int?,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult {
    val clickedPage = if (measurements.scrollPanel.contains(mouseX, mouseY)) {
        layouts.values.firstOrNull { it.contains(mouseX, mouseY) }
    } else {
        null
    } ?: return if (measurements.totalBounds.contains(mouseX, mouseY)) {
        InputHandlingResult.CONSUMED
    } else {
        InputHandlingResult.IGNORED
    }

    return if (clickedPage.pageIndex != activePage && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        if (screen.menu.carried.isEmpty) tryNavigateTo(screen, clickedPage.pageIndex, mouseX, mouseY)
        InputHandlingResult.CONSUMED
    } else {
        InputHandlingResult.IGNORED
    }
}

internal fun handleStorageOverlayMouseScroll(
    screen: AbstractContainerScreen<*>,
    mouseX: Double,
    mouseY: Double,
    scrollY: Double,
): InputHandlingResult {
    if (scrollY == 0.0) return InputHandlingResult.IGNORED
    val layoutState = storageOverlayLayoutScreen(screen) ?: return InputHandlingResult.IGNORED
    val measurements = layoutState.measurements
    if (storageSettingsContains(screen.width, screen.height, measurements, mouseX.toInt(), mouseY.toInt())) {
        return InputHandlingResult.CONSUMED
    }
    if (!measurements.scrollPanel.contains(mouseX.toInt(), mouseY.toInt())) return InputHandlingResult.IGNORED
    moveStorageScrollTarget(
        -(scrollY * config.details.scrollSpeed),
        maxScroll(measurements, layoutState.pageLayoutResult.contentHeight),
    )
    return InputHandlingResult.CONSUMED
}

internal fun handleStorageOverlayKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): InputHandlingResult {
    if (!storageOverlayIsActive(screen)) return InputHandlingResult.IGNORED
    if (processStorageSettingsKey(event.key()) == InputHandlingResult.CONSUMED) return InputHandlingResult.CONSUMED
    if (editingTitlePage != null) return handleTitleEditKeyPress(screen, event)
    if (!searchFocused) return InputHandlingResult.IGNORED
    when (event.key()) {
        GLFW.GLFW_KEY_ESCAPE -> {
            if (searchText.isEmpty()) {
                searchFocused = false
            } else {
                searchText = ""
                resetStorageScroll()
                storageOverlayLayoutScreen(screen)
            }
            return InputHandlingResult.CONSUMED
        }

        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            searchFocused = false
            return InputHandlingResult.CONSUMED
        }

        GLFW.GLFW_KEY_BACKSPACE -> {
            if (searchText.isNotEmpty()) {
                val control = event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0
                searchText = if (control) textAfterDeletingPreviousWord(searchText) else searchText.dropLast(1)
                resetStorageScroll()
                storageOverlayLayoutScreen(screen)
            }
            return InputHandlingResult.CONSUMED
        }
    }
    val typed = when (event.key()) {
        GLFW.GLFW_KEY_SPACE -> " "
        else -> GLFW.glfwGetKeyName(event.key(), event.scancode())
    }
    val blockedModifiers = GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_ALT or GLFW.GLFW_MOD_SUPER
    if (typed?.length == 1 && (event.modifiers() and blockedModifiers) == 0) {
        searchText += typed
        resetStorageScroll()
        storageOverlayLayoutScreen(screen)
    }
    return InputHandlingResult.CONSUMED
}

internal fun isStorageOverlayClickInside(
    screen: AbstractContainerScreen<*>,
    mouseX: Double,
    mouseY: Double,
): Boolean {
    if (!storageOverlayIsActive(screen)) return false
    val measurements = measurements(screen.width, screen.height)
    return measurements.totalBounds.contains(mouseX.toInt(), mouseY.toInt()) ||
        measurements.playerBounds.contains(mouseX.toInt(), mouseY.toInt()) ||
        (config.settings.miniMenu && measurements.selectorBounds.contains(mouseX.toInt(), mouseY.toInt())) ||
        storageSettingsContains(screen.width, screen.height, measurements, mouseX.toInt(), mouseY.toInt())
}
