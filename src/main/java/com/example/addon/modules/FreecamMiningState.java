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
    private static final double VANILLA_REACH = 4.5;

    private static boolean active;
    private static float lockedYaw;
    private static float lockedPitch;
    private static Vec3d lockedPos;
    private static HitResult storedHit;
    private static BlockPos storedBlockPos;
    private static Direction progressionDirection;
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
        progressionDirection = resolveProgressionDirection(hit, yaw, pitch);
    }

    public static void deactivate() {
        active = false;
        lockedPos = null;
        storedHit = null;
        storedBlockPos = null;
        progressionDirection = null;
        autoMineTarget = null;
    }

    /**
     * Strict vanilla reach lock: raycast always starts from the player's synced position
     * and never marches forward beyond normal reach distance.
     */
    public static void refreshLockedRaycast(MinecraftClient mc) {
        if (!active || mc == null || mc.world == null || mc.player == null || lockedPos == null) return;

        if (progressionDirection == null) progressionDirection = resolveProgressionDirection(storedHit, lockedYaw, lockedPitch);

        Vec3d look = Vec3d.fromPolar(lockedPitch, lockedYaw);
        double eyeY = lockedPos.y + mc.player.getEyeHeight(mc.player.getPose());
        Vec3d start = new Vec3d(lockedPos.x, eyeY, lockedPos.z);
        Vec3d end = start.add(look.multiply(VANILLA_REACH));

        Entity entity = mc.player;
        HitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));

        if (!(hit instanceof BlockHitResult)) {
            hit = BlockHitResult.createMissed(end, progressionDirection, BlockPos.ofFloored(end));
        }

        storedHit = hit;
        updateStoredBlockPos(hit);

        if (hit instanceof BlockHitResult blockHit) progressionDirection = blockHit.getSide().getOpposite();
    }

    private static Direction resolveProgressionDirection(HitResult hit, float yaw, float pitch) {
        if (hit instanceof BlockHitResult blockHit) return blockHit.getSide().getOpposite();

        Vec3d look = Vec3d.fromPolar(pitch, yaw);

        return Direction.getFacing(look.x, look.y, look.z);
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

    public static void syncPlayerPosition(Vec3d currentPlayerPos) {
        if (!active || currentPlayerPos == null) return;
        lockedPos = currentPlayerPos;
    }

    public static HitResult getStoredHit() {
        return storedHit;
    }

    public static BlockPos getStoredBlockPos() {
        return storedBlockPos;
    }

    public static Direction getProgressionDirection() {
        return progressionDirection;
    }

    public static Object getAutoMineTarget() {
        return autoMineTarget;
    }
}
