package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Method;

public class FreecamRender extends Module {
    private static final double VANILLA_REACH = 4.5;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWhenActive = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-freecam+")
        .description("Only render when Freecam+ mining lock is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(30, 170, 255, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(30, 170, 255, 170))
        .build()
    );

    public FreecamRender() {
        super(AddonTemplate.CATEGORY, "Freecam+ Render", "Renders a 3x3 plane from your actual camera look target.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (onlyWhenActive.get() && !FreecamMiningState.isActive()) return;
        if (mc.world == null) return;

        BlockPos center;
        Direction face;

        // While Freecam+ mining lock is active, anchor render to the locked mined block (not detached camera).
        if (FreecamMiningState.isActive()) {
            center = FreecamMiningState.getStoredBlockPos();
            face = FreecamMiningState.getProgressionDirection();
            if (center == null || face == null) return;
        } else {
            BlockHitResult hit = getCameraTarget();
            if (hit == null) return;
            center = hit.getBlockPos();
            face = hit.getSide();

            // When looking up/down, use camera yaw to pick a vertical plane instead of horizontal flat plane.
            if (face.getAxis() == Direction.Axis.Y) {
                Camera camera = mc.gameRenderer.getCamera();
                if (camera != null) {
                    Vec3d look = Vec3d.fromPolar(0f, camera.getYaw());
                    face = Math.abs(look.x) > Math.abs(look.z) ? Direction.EAST : Direction.SOUTH;
                }
            }
        }

        BlockState state = mc.world.getBlockState(center);
        if (state.isAir()) return;
        if (!state.getFluidState().isEmpty()) return;

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos pos = switch (face.getAxis()) {
                    case X -> center.add(0, a, b);
                    case Y -> center.add(a, 0, b);
                    case Z -> center.add(a, b, 0);
                };

                BlockState planeState = mc.world.getBlockState(pos);
                if (planeState.isAir()) continue;
                if (!planeState.getFluidState().isEmpty()) continue;

                event.renderer.box(pos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            }
        }
    }


    private Vec3d getCameraPos(Camera camera) {
        // Mapping-compatible camera position lookup across different Yarn names.
        for (String name : new String[] {"getPos", "getPosition"}) {
            try {
                Method method = camera.getClass().getMethod(name);
                Object out = method.invoke(camera);
                if (out instanceof Vec3d vec) return vec;
            } catch (Throwable ignored) {
            }
        }

        BlockPos bp = camera.getBlockPos();
        return new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
    }

    private BlockHitResult getCameraTarget() {
        if (mc.world == null) return null;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return null;

        Vec3d start = getCameraPos(camera);
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d end = start.add(look.multiply(VANILLA_REACH));

        HitResult result = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (result instanceof BlockHitResult bhr) return bhr;
        return null;
    }
}
