package com.skysoft.mixin

import com.google.common.hash.HashCode
import com.skysoft.SkysoftMod
import com.skysoft.features.misc.SkyBlockResourcePackManagerBridge
import com.skysoft.features.misc.SkyBlockResourcePackRetention
import com.skysoft.utils.SkysoftErrorBoundary
import java.net.URL
import java.util.UUID
import net.minecraft.client.resources.server.PackLoadFeedback
import net.minecraft.client.resources.server.ServerPackManager
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPackManager::class)
abstract class ServerPackManagerMixin : SkyBlockResourcePackManagerBridge {
    @field:Shadow
    @field:Final
    private lateinit var packLoadFeedback: PackLoadFeedback

    @field:Shadow
    @field:Final
    private lateinit var packs: List<*>

    @Shadow
    abstract fun popPack(id: UUID)

    @field:Unique
    private var skysoftRetainedPackId: UUID? = null

    @field:Unique
    private var skysoftRetainedPackHash: HashCode? = null

    @field:Unique
    private var skysoftRetainedPackApplied = false

    @Inject(method = ["pushPack"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftReuseRetainedPack(id: UUID, url: URL, hash: HashCode?, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("SkyBlock resource pack retention push") {
            if (!SkyBlockResourcePackRetention.isEnabled()) {
                skysoftClearRetainedPack()
                return@run
            }
            if (!SkyBlockResourcePackRetention.isOfficialPackUrl(url)) return@run
            if (!SkyBlockResourcePackRetention.canRetain(url, hash)) {
                SkysoftMod.LOGGER.warn(
                    "Cannot retain the official SkyBlock resource pack because it has no valid SHA-1 hash",
                )
                return@run
            }

            val retainedHash = hash!!
            if (
                skysoftRetainedPackApplied &&
                id == skysoftRetainedPackId &&
                retainedHash == skysoftRetainedPackHash
            ) {
                packLoadFeedback.reportUpdate(id, PackLoadFeedback.Update.ACCEPTED)
                packLoadFeedback.reportUpdate(id, PackLoadFeedback.Update.DOWNLOADED)
                packLoadFeedback.reportFinalResult(id, PackLoadFeedback.FinalResult.APPLIED)
                ci.cancel()
                return@run
            }

            val previousPackId = skysoftRetainedPackId.takeIf { skysoftRetainedPackApplied }
            skysoftRetainedPackId = id
            skysoftRetainedPackHash = retainedHash
            skysoftRetainedPackApplied = false
            if (previousPackId != null && previousPackId != id) popPack(previousPackId)
        }
    }

    @Inject(method = ["popPack"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftKeepPackLoaded(id: UUID, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("SkyBlock resource pack retention pop") {
            val retentionEnabled = SkyBlockResourcePackRetention.isEnabled()
            if (skysoftRetainedPackApplied && retentionEnabled && id == skysoftRetainedPackId) {
                ci.cancel()
            } else if (!retentionEnabled) {
                skysoftClearRetainedPack()
            }
        }
    }

    @Inject(method = ["popAll"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftKeepPackLoaded(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("SkyBlock resource pack retention pop all") {
            val retentionEnabled = SkyBlockResourcePackRetention.isEnabled()
            if (!skysoftRetainedPackApplied || !retentionEnabled) {
                if (!retentionEnabled) skysoftClearRetainedPack()
                return@run
            }

            val retainedPackId = skysoftRetainedPackId!!
            packs.asSequence()
                .map { (it as ServerPackDataAccessor).skysoftGetId() }
                .filter { it != retainedPackId }
                .distinct()
                .forEach(::popPack)
            ci.cancel()
        }
    }

    override fun skysoftMarkResourcePacksApplied(packs: Collection<*>) {
        SkysoftErrorBoundary.run("SkyBlock resource pack applied state") {
            val retainedPackId = skysoftRetainedPackId
            if (!SkyBlockResourcePackRetention.isEnabled() || retainedPackId == null) return@run
            val retainedPackHash = skysoftRetainedPackHash!!
            skysoftRetainedPackApplied = packs.any { pack ->
                val data = pack as ServerPackDataAccessor
                retainedPackId == data.skysoftGetId() && retainedPackHash == data.skysoftGetHash()
            }
        }
    }

    @Unique
    private fun skysoftClearRetainedPack() {
        skysoftRetainedPackId = null
        skysoftRetainedPackHash = null
        skysoftRetainedPackApplied = false
    }
}
