package com.example.addon.mixin;

import com.example.addon.modules.FreecamMiningState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float lockYawInMovePacket(ClientPlayerEntity instance) {
        if (FreecamMiningState.isActive()) return FreecamMiningState.getLockedYaw();
        return instance.getYaw();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float lockPitchInMovePacket(ClientPlayerEntity instance) {
        if (FreecamMiningState.isActive()) return FreecamMiningState.getLockedPitch();
        return instance.getPitch();
    }
}
