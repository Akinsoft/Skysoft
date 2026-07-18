package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftClientEvents
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.block.SkullBlock
import java.util.UUID
import java.util.WeakHashMap

object PlayerHeadSkinFix {
    private val ownerKey: RenderStateDataKey<UUID> = RenderStateDataKey.create { "skysoft:head_skin_owner" }
    private val slotStacks = WeakHashMap<Slot, ItemStack>()
    private val headRenderTypes = HashMap<UUID, RenderType>()

    fun register() {
        SkysoftClientEvents.onDisconnect("Player Head Skin Fix disconnect reset", ::clear)
    }

    @JvmStatic
    fun setOwner(state: LivingEntityRenderState, entity: LivingEntity) {
        if (!enabled) return
        (state as FabricRenderState).setData(ownerKey, entity.uuid)
        if (state.wornHeadType != SkullBlock.Types.PLAYER) {
            headRenderTypes.remove(entity.uuid)
        }
    }

    @JvmStatic
    fun inventoryStack(slot: Slot?, stack: ItemStack): ItemStack? {
        if (!enabled || slot == null) return stack
        if (stack.item != Items.PLAYER_HEAD) {
            slotStacks.remove(slot)
            return stack
        }

        val profile = stack.get(DataComponents.PROFILE)
        return if (isLoaded(profile)) {
            slotStacks[slot] = stack.copy()
            stack
        } else {
            slotStacks[slot]?.copy() ?: stack
        }
    }

    @JvmStatic
    fun wornHeadRenderType(state: LivingEntityRenderState, renderType: RenderType): RenderType? {
        if (!enabled || state.wornHeadType != SkullBlock.Types.PLAYER) return renderType
        val owner = (state as FabricRenderState).getData(ownerKey) ?: return renderType
        val profile = state.wornHeadProfile

        return if (isLoaded(profile)) {
            headRenderTypes[owner] = renderType
            renderType
        } else {
            headRenderTypes[owner]
        }
    }

    private val enabled: Boolean
        get() = SkysoftConfigGui.config().fixes.playerHeadSkinFix

    private fun isLoaded(profile: ResolvableProfile?): Boolean =
        profile != null && hasTexture(profile) && isReady(profile)

    private fun hasTexture(profile: ResolvableProfile): Boolean =
        profile.partialProfile().properties().containsKey("textures")

    private fun isReady(profile: ResolvableProfile): Boolean {
        val result = Minecraft.getInstance().playerSkinRenderCache().lookup(profile).getNow(null) ?: return false
        return result.isPresent
    }

    private fun clear() {
        slotStacks.clear()
        headRenderTypes.clear()
    }
}
