package com.example.addon.mixin;

import com.example.addon.modules.FreecamPlus;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent block breaking while Freecam+ is active.
 * This ensures blocks aren't destroyed while using the Freecam+ module.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class FreecamBlockBreakMixin {
    /**
     * Prevent block breaking when Freecam+ is active.
     */
    @Inject(method = "method_30287", at = @At("HEAD"), cancellable = true)
    private void onBlockBreakProgress(BlockPos pos, CallbackInfo ci) {
        FreecamPlus freecamPlus = Modules.get().get(FreecamPlus.class);
        if (freecamPlus != null && freecamPlus.isActive()) {
            ci.cancel();
        }
    }

    /**
     * Prevent clicking blocks to break them when Freecam+ is active.
     */
    @Inject(method = "method_30289", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, CallbackInfo ci) {
        FreecamPlus freecamPlus = Modules.get().get(FreecamPlus.class);
        if (freecamPlus != null && freecamPlus.isActive()) {
            ci.cancel();
        }
    }
}
