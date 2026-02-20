package com.example.addon.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class FreecamMiningState {
    // Long raycast distance so locked targeting can continue past the first few mined blocks.
    // Using vanilla reach (~4.5-5) causes mining to stall after a short line.
    private static final double LOCKED_REACH = 128.0;

    private static boolean active;
    private static float lockedYaw;
    private static float lockedPitch;
    private static Vec3d lockedPos;
    private static HitResult storedHit;
    private static BlockPos storedBlockPos;
    private static Object autoMineTarget;

    private FreecamMiningState() {
    }

    public static void activate(float yaw, float pitch, Vec3d position, HitResult hit, Object targetSnapshot) {
        active = true;
        lockedYaw = yaw;
        lockedPitch = pitch;
        lockedPos = position;
        storedHit = hit;
        autoMineTarget = targetSnapshot;
        updateStoredBlockPos(hit);
    }

    public static void deactivate() {
        active = false;
        lockedPos = null;
        storedHit = null;
        storedBlockPos = null;
        autoMineTarget = null;
    }

    /**
     * Rebuilds crosshair hit from the original locked player location + locked rotation.
     * This keeps AutoMine advancing to the next block in the same line after a block breaks.
     */
    public static void refreshLockedRaycast(MinecraftClient mc) {
        if (!active || mc == null || mc.world == null || mc.player == null || lockedPos == null) return;

        double eyeY = lockedPos.y + mc.player.getEyeHeight(mc.player.getPose());
        Vec3d start = new Vec3d(lockedPos.x, eyeY, lockedPos.z);
        Vec3d direction = Vec3d.fromPolar(lockedPitch, lockedYaw);
        Vec3d end = start.add(direction.multiply(LOCKED_REACH));

        Entity entity = mc.player;
        HitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));

        if (hit == null) {
            hit = BlockHitResult.createMissed(end, Direction.getFacing(direction.x, direction.y, direction.z), BlockPos.ofFloored(end));
        }

        storedHit = hit;
        updateStoredBlockPos(hit);
    }

    private static void updateStoredBlockPos(HitResult hit) {
        if (hit instanceof BlockHitResult blockHit) storedBlockPos = blockHit.getBlockPos();
        else storedBlockPos = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static float getLockedYaw() {
        return lockedYaw;
    }

    public static float getLockedPitch() {
        return lockedPitch;
    }

    public static Vec3d getLockedPos() {
        return lockedPos;
    }

    public static HitResult getStoredHit() {
        return storedHit;
    }

    public static BlockPos getStoredBlockPos() {
        return storedBlockPos;
    }

    public static Object getAutoMineTarget() {
        return autoMineTarget;
    }
}
