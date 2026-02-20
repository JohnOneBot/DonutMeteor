package com.example.addon.mixin;

import com.example.addon.modules.FreecamPlus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the client's crosshair target while FreecamPlus is active.
 * This ensures AutoMine and other modules that rely on `client.crosshairTarget`
 * keep using the original block the player targeted before enabling freecam.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    private MinecraftClient client;

    @Inject(method = "updateTargetedEntity", at = @At("RETURN"))
    private void onUpdateTargetedEntity(CallbackInfo ci) {
        if (FreecamPlus.isFreecamPlusActive && FreecamPlus.storedHit != null) {
            // Replace the client's crosshair target with the stored hit result
            client.crosshairTarget = (HitResult) FreecamPlus.storedHit;
        }
    }
}
