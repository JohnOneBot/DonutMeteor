package com.example.addon.mixin;

import com.example.addon.modules.FreecamPlus;
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
        // If FreecamPlus is active and has a locked target, only allow breaking that target
        if (FreecamPlus.isFreecamPlusActive && FreecamPlus.targetBlock != null) {
            if (!pos.equals(FreecamPlus.targetBlock)) {
                ci.cancel();
            }
        } else if (FreecamPlus.isFreecamPlusActive) {
            // If active but no locked target, cancel generic breaking
            ci.cancel();
        }
    }

    /**
     * Prevent clicking blocks to break them when Freecam+ is active.
     */
    @Inject(method = "method_30289", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, CallbackInfo ci) {
        // Same logic for direct attack/clicks: only allow attacks on the locked target
        if (FreecamPlus.isFreecamPlusActive && FreecamPlus.targetBlock != null) {
            if (!pos.equals(FreecamPlus.targetBlock)) {
                ci.cancel();
            }
        } else if (FreecamPlus.isFreecamPlusActive) {
            ci.cancel();
        }
    }
}
