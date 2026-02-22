package com.example.addon.mixin;

import com.example.addon.modules.FreecamMiningState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional safety gate: while mining-lock freecam is active, prevent changing the broken block.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class PlayerAttackMixin {
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void lockBreakingTarget(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!FreecamMiningState.isActive()) return;
        BlockPos locked = FreecamMiningState.getStoredBlockPos();
        if (locked != null && !locked.equals(pos)) {
            cir.setReturnValue(false);
        }
    }
}
