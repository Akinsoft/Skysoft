package com.skysoft.mixin

import com.skysoft.features.misc.SkyBlockResourcePackManagerBridge
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.resources.server.ServerPackManager
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(targets = ["net.minecraft.client.resources.server.ServerPackManager\$1"])
open class ServerPackReloadCallbacksMixin {
    @field:Shadow(aliases = ["val\$packsToLoad"])
    @field:Final
    private lateinit var packsToLoad: List<*>

    @field:Shadow(aliases = ["this\$0"])
    @field:Final
    private lateinit var serverPackManager: ServerPackManager

    @Inject(method = ["onSuccess"], at = [At("TAIL")])
    protected fun skysoftMarkResourcePacksApplied(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("SkyBlock resource pack reload completion") {
            (serverPackManager as SkyBlockResourcePackManagerBridge).skysoftMarkResourcePacksApplied(packsToLoad)
        }
    }
}
