package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class FreecamModule extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Freecam meteorFreecam;

    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a 3x3 mining plane for the locked target direction.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(30, 170, 255, 25))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(30, 170, 255, 170))
        .visible(render::get)
        .build()
    );

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

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || !FreecamMiningState.isActive()) return;

        BlockPos center = FreecamMiningState.getStoredBlockPos();
        Direction dir = FreecamMiningState.getProgressionDirection();
        if (center == null || dir == null) return;

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos pos = switch (dir.getAxis()) {
                    case X -> center.add(0, a, b);
                    case Y -> center.add(a, 0, b);
                    case Z -> center.add(a, b, 0);
                };

                event.renderer.box(pos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            }
        }
    }
}
