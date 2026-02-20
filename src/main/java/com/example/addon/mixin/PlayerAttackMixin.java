package com.example.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;

/**
 * Mixin to make block breaking use player position instead of camera position.
 * This allows freecamming without breaking blocks where the camera is looking,
 * only where the player's actual body is facing.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class PlayerAttackMixin {

    @Inject(
        method = "updateBlockBreakingProgress",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        // Use player's actual position for raycast, not camera
        if (mc.player != null && mc.world != null) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d lookDirection = mc.player.getRotationVec(1.0f);
            Vec3d targetPos = eyePos.add(lookDirection.multiply(5.0)); // Standard reach
            
            HitResult hit = mc.world.raycast(
                new RaycastContext(
                    eyePos,
                    targetPos,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
                )
            );
            
            // Only allow breaking if player is looking at it, not camera
            if (hit instanceof BlockHitResult blockHit && !blockHit.getBlockPos().equals(pos)) {
                ci.cancel(); // Cancel the attack on wrong block
            }
        }
    }
}

