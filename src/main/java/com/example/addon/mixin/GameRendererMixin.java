package com.example.addon.mixin;

import com.example.addon.modules.FreecamMiningState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final MinecraftClient client;

    /**
     * Raycast override: force the stored pre-freecam hit so modules like AutoMine keep the same block target.
     */
    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void lockCrosshairTarget(float tickDelta, CallbackInfo ci) {
        if (FreecamMiningState.isActive()) {
            FreecamMiningState.refreshLockedRaycast(client);
            if (FreecamMiningState.getStoredHit() != null) {
                client.crosshairTarget = FreecamMiningState.getStoredHit();
            }
        }
    }
}
