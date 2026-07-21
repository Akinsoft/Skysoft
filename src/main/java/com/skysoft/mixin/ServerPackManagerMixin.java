package com.skysoft.mixin;

import com.google.common.hash.HashCode;
import com.skysoft.SkysoftMod;
import com.skysoft.features.misc.SkyBlockResourcePackManagerBridge;
import com.skysoft.features.misc.SkyBlockResourcePackRetention;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.resources.server.PackLoadFeedback;
import net.minecraft.client.resources.server.ServerPackManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPackManager.class)
public abstract class ServerPackManagerMixin implements SkyBlockResourcePackManagerBridge {
    @Shadow
    @Final
    private PackLoadFeedback packLoadFeedback;

    @Shadow
    @Final
    private List<?> packs;

    @Shadow
    public abstract void popPack(UUID id);

    @Unique
    private @Nullable UUID skysoftRetainedPackId;

    @Unique
    private @Nullable HashCode skysoftRetainedPackHash;

    @Unique
    private boolean skysoftRetainedPackApplied;

    @Inject(method = "pushPack", at = @At("HEAD"), cancellable = true)
    private void skysoftReuseRetainedPack(UUID id, URL url, @Nullable HashCode hash, CallbackInfo ci) {
        if (!SkyBlockResourcePackRetention.isEnabled()) {
            this.skysoftClearRetainedPack();
            return;
        }
        if (!SkyBlockResourcePackRetention.isOfficialPackUrl(url)) {
            return;
        }
        if (!SkyBlockResourcePackRetention.canRetain(url, hash)) {
            SkysoftMod.Companion.getLOGGER().warn(
                "Cannot retain the official SkyBlock resource pack because it has no valid SHA-1 hash"
            );
            return;
        }
        if (this.skysoftRetainedPackApplied
            && id.equals(this.skysoftRetainedPackId)
            && hash.equals(this.skysoftRetainedPackHash)) {
            this.packLoadFeedback.reportUpdate(id, PackLoadFeedback.Update.ACCEPTED);
            this.packLoadFeedback.reportUpdate(id, PackLoadFeedback.Update.DOWNLOADED);
            this.packLoadFeedback.reportFinalResult(id, PackLoadFeedback.FinalResult.APPLIED);
            ci.cancel();
            return;
        }

        UUID previousPackId = this.skysoftRetainedPackApplied ? this.skysoftRetainedPackId : null;
        this.skysoftRetainedPackId = id;
        this.skysoftRetainedPackHash = hash;
        this.skysoftRetainedPackApplied = false;
        if (previousPackId != null && !previousPackId.equals(id)) {
            this.popPack(previousPackId);
        }
    }

    @Inject(method = "popPack", at = @At("HEAD"), cancellable = true)
    private void skysoftKeepPackLoaded(UUID id, CallbackInfo ci) {
        boolean retentionEnabled = SkyBlockResourcePackRetention.isEnabled();
        if (this.skysoftRetainedPackApplied && retentionEnabled && id.equals(this.skysoftRetainedPackId)) {
            ci.cancel();
        } else if (!retentionEnabled) {
            this.skysoftClearRetainedPack();
        }
    }

    @Inject(method = "popAll", at = @At("HEAD"), cancellable = true)
    private void skysoftKeepPackLoaded(CallbackInfo ci) {
        boolean retentionEnabled = SkyBlockResourcePackRetention.isEnabled();
        if (!this.skysoftRetainedPackApplied || !retentionEnabled) {
            if (!retentionEnabled) {
                this.skysoftClearRetainedPack();
            }
            return;
        }

        UUID retainedPackId = Objects.requireNonNull(this.skysoftRetainedPackId);
        this.packs.stream()
            .map(pack -> ((ServerPackDataAccessor) pack).skysoftGetId())
            .filter(id -> !id.equals(retainedPackId))
            .distinct()
            .forEach(this::popPack);
        ci.cancel();
    }

    @Override
    public void skysoftMarkResourcePacksApplied(Collection<?> appliedPacks) {
        if (!SkyBlockResourcePackRetention.isEnabled() || this.skysoftRetainedPackId == null) {
            return;
        }
        this.skysoftRetainedPackApplied = appliedPacks.stream().anyMatch(pack -> {
            ServerPackDataAccessor data = (ServerPackDataAccessor) pack;
            return this.skysoftRetainedPackId.equals(data.skysoftGetId())
                && this.skysoftRetainedPackHash.equals(data.skysoftGetHash());
        });
    }

    @Unique
    private void skysoftClearRetainedPack() {
        this.skysoftRetainedPackId = null;
        this.skysoftRetainedPackHash = null;
        this.skysoftRetainedPackApplied = false;
    }
}
