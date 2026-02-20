package com.example.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.addon.modules.FreecamPlus;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Mixin to lock block breaking to the target block when FreecamPlus is active.
 * Only allows attacking the block that was being targeted when freecam was activated.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class PlayerAttackMixin {

    @Inject(
        method = "updateBlockBreakingProgress",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfo ci) {
        // If FreecamPlus is active and has a target block, only allow breaking that block
        if (FreecamPlus.isFreecamPlusActive && FreecamPlus.targetBlock != null) {
            if (!pos.equals(FreecamPlus.targetBlock)) {
                ci.cancel(); // Cancel breaking any other block
            }
        }
    }
}


