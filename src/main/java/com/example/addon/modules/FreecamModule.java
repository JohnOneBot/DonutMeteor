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
        if (hit == null) hit = BlockHitResult.createMissed(mc.player.getPos(), mc.player.getHorizontalFacing(), mc.player.getBlockPos());

        FreecamMiningState.activate(
            mc.player.getYaw(),
            mc.player.getPitch(),
            mc.player.getPos(),
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

        // Keep AutoMine-facing logic pinned to the originally targeted block.
        mc.crosshairTarget = FreecamMiningState.getStoredHit();
    }

}
