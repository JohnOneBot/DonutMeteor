package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Detaches the camera while keeping mining logic locked to the original player aim.
 *
 * Why this exists:
 * Meteor AutoMine reads {@code client.crosshairTarget} every tick.
 * If crosshair raycasting keeps updating from detached freecam camera, AutoMine switches target.
 * This module stores the original target and keeps feeding it back while active.
 */
public class FreecamModule extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Freecam meteorFreecam;

    private boolean enabledMeteorFreecam;

    public FreecamModule() {
        super(AddonTemplate.CATEGORY, "freecam-mining-lock", "Moves camera freely while mining keeps using the original player target.");
        this.meteorFreecam = Modules.get().get(Freecam.class);
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null) {
            Vec3d eyePos = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
            hit = BlockHitResult.createMissed(eyePos, mc.player.getHorizontalFacing(), mc.player.getBlockPos());
        }

        FreecamMiningState.activate(
            mc.player.getYaw(),
            mc.player.getPitch(),
            new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
            hit,
            null
        );

        enabledMeteorFreecam = meteorFreecam != null && !meteorFreecam.isActive();
        if (enabledMeteorFreecam) meteorFreecam.toggle();
    }

    @Override
    public void onDeactivate() {
        FreecamMiningState.deactivate();

        if (enabledMeteorFreecam && meteorFreecam != null && meteorFreecam.isActive()) {
            meteorFreecam.toggle();
        }
        enabledMeteorFreecam = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!FreecamMiningState.isActive() || mc.player == null) return;

        // Keep local player rotation frozen so rotation-dependent logic remains stable.
        mc.player.setYaw(FreecamMiningState.getLockedYaw());
        mc.player.setPitch(FreecamMiningState.getLockedPitch());

        // Recalculate the locked-player raycast so mining can advance to the next block in-line.
        FreecamMiningState.refreshLockedRaycast(mc);

        // Keep AutoMine-facing logic pinned to the locked-player raycast (not detached camera).
        mc.crosshairTarget = FreecamMiningState.getStoredHit();
    }

}
