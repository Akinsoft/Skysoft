package com.skysoft.features.inventory

import com.skysoft.SkysoftMod
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.MinecraftItems
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

internal fun storageOverviewSlotState(name: String, isPlaceholderItem: Boolean): StorageOverviewSlotState = when {
    name == "Locked Page" || name.startsWith("Locked Backpack Slot") -> StorageOverviewSlotState.LOCKED
    isPlaceholderItem -> StorageOverviewSlotState.PLACEHOLDER
    else -> StorageOverviewSlotState.PAGE
}

internal fun storageOverviewSlotState(stack: ItemStack): StorageOverviewSlotState = storageOverviewSlotState(
    stack.formattedHoverName().cleanSkyBlockText(),
    stack.item in emptyOverviewItems,
)

internal fun emptyBackpackShortcutStack(backpackSlot: Int): ItemStack = ItemStack(MinecraftItems.grayDye()).apply {
    set(
        DataComponents.CUSTOM_NAME,
        Component.literal("Empty Backpack Slot $backpackSlot").withStyle { it.withItalic(false) },
    )
}

internal fun ensureUnloadedPage(pageIndex: Int): ChangeResult {
    val page = storage.skyBlockStoragePages[pageIndex] ?: run {
        storage.skyBlockStoragePages[pageIndex] =
            ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(pageIndex), 0)
        return ChangeResult.CHANGED
    }
    var changed = ensurePageTitle(page, pageIndex) == ChangeResult.CHANGED
    if (page.overviewIcon.isNotEmpty()) {
        page.overviewIcon = ""
        changed = true
    }
    return ChangeResult.from(changed)
}

internal fun isEnderChestPage(pageIndex: Int): Boolean =
    pageIndex in 0 until ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES

internal fun fixedPageTitle(pageIndex: Int): String? = ToolkitType.fromPageIndex(pageIndex)?.title

internal fun defaultPageTitle(pageIndex: Int): String = fixedPageTitle(pageIndex) ?: when {
    isRiftStoragePage(pageIndex) -> "Rift Storage #${riftStoragePageNumber(pageIndex) + 1}"
    pageIndex < ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES -> "Ender Chest #${pageIndex + 1}"
    else -> "Backpack #${pageIndex - ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + 1}"
}

internal fun ensurePageTitle(page: ProfileStorage.SkyBlockStoragePageData, pageIndex: Int): ChangeResult {
    val title = fixedPageTitle(pageIndex)
        ?: page.title.takeIf { it.isNotBlank() }
        ?: defaultPageTitle(pageIndex)
    if (page.title == title) return ChangeResult.UNCHANGED
    page.title = title
    return ChangeResult.CHANGED
}

internal fun encodeItem(stack: ItemStack): ProfileStorage.SkyBlockStorageItemData =
    if (stack.isEmpty) {
        ProfileStorage.SkyBlockStorageItemData()
    } else {
        ProfileStorage.SkyBlockStorageItemData(encodeStack(stack.copy()))
    }

internal fun stackFor(item: ProfileStorage.SkyBlockStorageItemData?): ItemStack {
    val encoded = item?.encodedStack?.takeIf { it.isNotBlank() } ?: return ItemStack.EMPTY
    decodedStacks[encoded]?.let { return it }
    val decodedStack = decodeStack(encoded) ?: return ItemStack.EMPTY
    decodedStacks[encoded] = decodedStack
    return decodedStack
}

internal fun encodeStack(stack: ItemStack): String {
    val tag = ItemStack.CODEC.encodeStart(registryOps(), stack)
        .resultOrPartial { error -> throw IllegalStateException("Failed to encode SkyBlock storage item: $error") }
        .orElseThrow { IllegalStateException("Failed to encode SkyBlock storage item") } as? CompoundTag
        ?: error("Expected encoded SkyBlock storage item to be a CompoundTag")
    val root = CompoundTag()
    root.put("stack", tag)
    return ByteArrayOutputStream().use { output ->
        NbtIo.writeCompressed(root, output)
        Base64.getEncoder().encodeToString(output.toByteArray())
    }
}

internal fun decodeStack(encoded: String): ItemStack? = runCatching {
    val bytes = Base64.getDecoder().decode(encoded)
    val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(StorageRuntime.MAX_ITEM_NBT_BYTES))
    val tag = root.getCompound("stack").orElse(null) ?: return null
    ItemStack.CODEC.parse(registryOps(), tag)
        .resultOrPartial { error -> SkysoftMod.LOGGER.warn("Failed to decode SkyBlock storage item: $error") }
        .orElse(null)
}.getOrElse { error ->
    SkysoftMod.LOGGER.warn("Failed to decode SkyBlock storage item cache", error)
    null
}

internal fun registryOps(): RegistryOps<Tag> {
    val registryAccess = Minecraft.getInstance().connection?.registryAccess() ?: RegistryAccess.EMPTY
    return RegistryOps.create(NbtOps.INSTANCE, registryAccess)
}

internal enum class StorageOverviewSlotState {
    LOCKED,
    PLACEHOLDER,
    PAGE,
}

