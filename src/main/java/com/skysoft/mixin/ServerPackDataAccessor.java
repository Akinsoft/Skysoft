package com.skysoft.mixin;

import com.google.common.hash.HashCode;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.resources.server.ServerPackManager$ServerPackData")
public interface ServerPackDataAccessor {
    @Accessor("id")
    UUID skysoftGetId();

    @Accessor("hash")
    HashCode skysoftGetHash();
}
