package com.skysoft.data.skyblock

import com.skysoft.SkysoftMod
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack

internal object SkyBlockItemStackCodec {
    fun encode(stack: ItemStack): String {
        val tag = ItemStack.CODEC.encodeStart(registryOps(), stack)
            .resultOrPartial { error -> throw IllegalStateException("Failed to encode item stack: $error") }
            .orElseThrow { IllegalStateException("Failed to encode item stack") } as? CompoundTag
            ?: error("Expected encoded item stack to be a CompoundTag")
        val root = CompoundTag()
        root.put(STACK_KEY, tag)
        return ByteArrayOutputStream().use { output ->
            NbtIo.writeCompressed(root, output)
            Base64.getEncoder().encodeToString(output.toByteArray())
        }
    }

    fun decode(encoded: String): ItemStack? = runCatching {
        require(encoded.length <= MAXIMUM_ENCODED_LENGTH) { "Encoded item stack is too large" }
        val bytes = Base64.getDecoder().decode(encoded)
        val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(MAXIMUM_NBT_BYTES))
        val tag = root.getCompound(STACK_KEY).orElse(null) ?: return null
        ItemStack.CODEC.parse(registryOps(), tag)
            .resultOrPartial { error -> SkysoftMod.LOGGER.warn("Failed to decode item stack: $error") }
            .orElse(null)
    }.getOrElse { error ->
        SkysoftMod.LOGGER.warn("Failed to decode cached item stack", error)
        null
    }

    fun registryOps(): RegistryOps<Tag> {
        val registryAccess = Minecraft.getInstance().connection?.registryAccess() ?: RegistryAccess.EMPTY
        return RegistryOps.create(NbtOps.INSTANCE, registryAccess)
    }

    private const val STACK_KEY = "stack"
    private const val MAXIMUM_ENCODED_LENGTH = 2_000_000
    private const val MAXIMUM_NBT_BYTES = 1_000_000L
}
