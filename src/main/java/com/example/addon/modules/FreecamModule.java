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

public class FreecamModule extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Freecam meteorFreecam;

    private boolean enabledMeteorFreecam;

    public FreecamModule() {
        super(AddonTemplate.CATEGORY, "Freecam+", "Detached freecam while mining stays locked to original player line.");
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

        mc.player.setYaw(FreecamMiningState.getLockedYaw());
        mc.player.setPitch(FreecamMiningState.getLockedPitch());

        FreecamMiningState.syncPlayerPosition(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        FreecamMiningState.refreshLockedRaycast(mc);
        mc.crosshairTarget = FreecamMiningState.getStoredHit();
    }
}
