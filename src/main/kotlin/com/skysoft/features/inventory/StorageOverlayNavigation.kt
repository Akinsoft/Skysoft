package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.gui.scale.InventoryCursorMemory
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal fun rememberActivePage(handle: StorageHandle) {
    handle.entryIndex()?.let { rememberedPageIndex = it }
}

internal fun redirectToRememberedPage(screen: AbstractContainerScreen<*>, handle: StorageHandle) {
    if (handle != StorageHandle.Overview || !screen.menu.carried.isEmpty || pendingOverviewShortcutClick != null) return
    val pageIndex = rememberedPageIndex?.takeIf { storageEntryExists(it) } ?: return
    val screenId = System.identityHashCode(screen)
    if (redirectedOverviewScreenId == screenId) return
    if (tryNavigateToRememberedPage(screen, pageIndex)) redirectedOverviewScreenId = screenId
}

internal fun requestOverviewShortcutClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
    pageIndex: Int,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult {
    if (
        click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT ||
        !screen.menu.carried.isEmpty ||
        StorageOverviewSlots.slotForPageIndex(pageIndex) == null
    ) {
        return InputHandlingResult.IGNORED
    }
    val connection = Minecraft.getInstance().connection ?: return InputHandlingResult.IGNORED
    val now = System.currentTimeMillis()
    if (now - lastCommandMillis < StorageRuntime.COMMAND_COOLDOWN_MILLIS) return InputHandlingResult.IGNORED
    lastCommandMillis = now
    pendingOverviewShortcutClick = PendingOverviewShortcutClick(pageIndex, click.button(), now)
    saveCursorBeforeNavigation(screen, mouseX, mouseY)
    connection.sendCommand("storage")
    return InputHandlingResult.CONSUMED
}

internal fun routePendingOverviewShortcutClick(
    screen: AbstractContainerScreen<*>,
    handle: StorageHandle,
): InputHandlingResult {
    val pending = pendingOverviewShortcutClick ?: return InputHandlingResult.IGNORED
    val now = System.currentTimeMillis()
    val isExpired = now < pending.requestedAtMillis ||
        now - pending.requestedAtMillis > StorageRuntime.OVERVIEW_SHORTCUT_TIMEOUT_MILLIS
    if (isExpired) {
        pendingOverviewShortcutClick = null
        return InputHandlingResult.IGNORED
    }
    if (handle != StorageHandle.Overview) return InputHandlingResult.IGNORED
    pendingOverviewShortcutClick = null
    return routeOverviewShortcutClick(screen, pending.button, pending.pageIndex)
}

internal fun focusActivePageIfNeeded(
    activePage: Int?,
    measurements: Measurements,
    pageLayoutResult: PageLayoutResult,
): PageLayoutRefresh {
    val pageIndex = activePage ?: return PageLayoutRefresh.UNCHANGED
    val layoutKey = layoutPageKey(pageIndex, measurements, pageLayoutResult)
    val preservedPageIndex = preservedScrollPageIndex
    if (preservedPageIndex != null) {
        preservedScrollPageIndex = null
        if (preservedPageIndex == pageIndex) {
            clearPageFocusRequest()
            focusedPageKey = layoutKey
            return PageLayoutRefresh.UNCHANGED
        }
    }
    if (!config.settings.isAutofocusEnabled) {
        clearPageFocusRequest()
        return PageLayoutRefresh.UNCHANGED
    }
    val requested = requestedFocusPageIndex == pageIndex
    if (!requested && focusedPageKey == layoutKey) return PageLayoutRefresh.UNCHANGED
    if (requested && requestedFocusKey == layoutKey && focusedPageKey == layoutKey) {
        clearPageFocusRequest()
        return PageLayoutRefresh.UNCHANGED
    }
    val previousScroll = scroll
    focusPage(measurements, pageLayoutResult, pageIndex)
    focusedPageKey = layoutKey
    if (requested) requestedFocusKey = layoutKey
    return if (scroll != previousScroll) PageLayoutRefresh.REQUIRED else PageLayoutRefresh.UNCHANGED
}

internal fun tryNavigateTo(screen: AbstractContainerScreen<*>, pageIndex: Int, mouseX: Int, mouseY: Int): Boolean {
    val sent = trySendPageCommand(pageIndex) { saveCursorBeforeNavigation(screen, mouseX, mouseY) }
    if (sent) {
        preservedScrollPageIndex = null
        requestPageFocus(pageIndex)
    }
    return sent
}

internal fun tryNavigateToRememberedPage(screen: AbstractContainerScreen<*>, pageIndex: Int): Boolean {
    val sent = trySendPageCommand(pageIndex) { saveCurrentCursorBeforeNavigation(screen) }
    if (sent) {
        preservedScrollPageIndex = pageIndex
        clearPageFocusRequest()
    }
    return sent
}

internal fun trySendPageCommand(pageIndex: Int, saveCursor: () -> Unit): Boolean {
    val command = commandForPage(pageIndex) ?: return false
    val connection = Minecraft.getInstance().connection ?: return false
    val now = System.currentTimeMillis()
    if (now - lastCommandMillis < StorageRuntime.COMMAND_COOLDOWN_MILLIS) return false
    lastCommandMillis = now
    freezeStorageScroll()
    saveCursor()
    connection.sendCommand(command)
    return true
}

internal enum class PageLayoutRefresh {
    REQUIRED,
    UNCHANGED,
}

internal fun commandForPage(pageIndex: Int): String? = when (pageIndex) {
    in 0 until ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES -> "enderchest ${pageIndex + 1}"
    in ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT ->
        "backpack ${pageIndex - ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + 1}"
    StorageToolkit.FARMING_PAGE_INDEX -> ToolkitType.FARMING.command
    StorageToolkit.HUNTING_PAGE_INDEX -> ToolkitType.HUNTING.command
    else -> null
}

internal fun saveCursorBeforeNavigation(screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int) {
    val minecraft = Minecraft.getInstance()
    val window = minecraft.window
    val rawX = mouseX * window.screenWidth / screen.width.coerceAtLeast(1).toDouble()
    val rawY = mouseY * window.screenHeight / screen.height.coerceAtLeast(1).toDouble()
    InventoryCursorMemory.rememberScreenCursor(screen, rawX, rawY)
}

internal fun saveCurrentCursorBeforeNavigation(screen: AbstractContainerScreen<*>) {
    val minecraft = Minecraft.getInstance()
    InventoryCursorMemory.rememberScreenCursor(screen, minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos())
}

internal fun requestPageFocus(pageIndex: Int) {
    requestedFocusPageIndex = pageIndex
    requestedFocusKey = null
}

internal fun clearPageFocusRequest() {
    requestedFocusPageIndex = null
    requestedFocusKey = null
}

internal fun layoutPageKey(
    pageIndex: Int,
    measurements: Measurements,
    pageLayoutResult: PageLayoutResult,
): String {
    val layout = pageLayoutResult.pages[pageIndex]
    val pageTop = if (layout == null) null else layout.y + scroll - measurements.scrollPanel.y
    return listOf(
        pageIndex,
        pageTop,
        layout?.width,
        layout?.height,
        pageLayoutResult.contentHeight,
        measurements.scrollPanel.width,
        measurements.scrollPanel.height,
        measurements.columns,
    ).joinToString(":")
}
