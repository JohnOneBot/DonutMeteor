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
    private static Vec3d lockedRayOrigin;
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
        lockedRayOrigin = null;
        storedHit = hit;
        autoMineTarget = targetSnapshot;
        updateStoredBlockPos(hit);
        progressionDirection = resolveProgressionDirection(hit, yaw, pitch);
    }

    public static void deactivate() {
        active = false;
        lockedPos = null;
        lockedRayOrigin = null;
        storedHit = null;
        storedBlockPos = null;
        progressionDirection = null;
        autoMineTarget = null;
    }

    /**
     * Uses vanilla reach, but advances a virtual ray origin as blocks are mined so targeting can continue indefinitely.
     * Direction is snapped to block direction to avoid drifting up/down from tiny pitch changes.
     */
    public static void refreshLockedRaycast(MinecraftClient mc) {
        if (!active || mc == null || mc.world == null || mc.player == null || lockedPos == null) return;

        if (progressionDirection == null) progressionDirection = resolveProgressionDirection(storedHit, lockedYaw, lockedPitch);

        Vec3d direction = Vec3d.of(progressionDirection.getVector());

        if (lockedRayOrigin == null) {
            double eyeY = lockedPos.y + mc.player.getEyeHeight(mc.player.getPose());
            lockedRayOrigin = new Vec3d(lockedPos.x, eyeY, lockedPos.z);
        }

        // If current target was mined (air), march the virtual origin forward so next blocks stay in vanilla reach.
        int marchCount = 0;
        while (storedBlockPos != null
            && mc.world.getBlockState(storedBlockPos).isAir()
            && marchCount < 64) {
            lockedRayOrigin = lockedRayOrigin.add(direction);
            marchCount++;

            // Refresh target check from new origin.
            Vec3d marchEnd = lockedRayOrigin.add(direction.multiply(VANILLA_REACH));
            HitResult marchHit = mc.world.raycast(new RaycastContext(lockedRayOrigin, marchEnd, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            updateStoredBlockPos(marchHit);
        }

        Vec3d end = lockedRayOrigin.add(direction.multiply(VANILLA_REACH));
        Entity entity = mc.player;
        HitResult hit = mc.world.raycast(new RaycastContext(lockedRayOrigin, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));

        if (!(hit instanceof BlockHitResult)) {
            hit = BlockHitResult.createMissed(end, progressionDirection, BlockPos.ofFloored(end));
        }

        storedHit = hit;
        updateStoredBlockPos(hit);
    }

    private static Direction resolveProgressionDirection(HitResult hit, float yaw, float pitch) {
        if (hit instanceof BlockHitResult blockHit) return blockHit.getSide().getOpposite();

        Vec3d look = Vec3d.fromPolar(pitch, yaw);

        // Prevent accidental vertical drift for almost-level mining lines.
        if (Math.abs(pitch) <= 20f) {
            return Direction.getFacing(look.x, 0.0, look.z);
        }

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
