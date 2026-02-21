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
    private static final int AIR_CONFIRM_TICKS = 6;

    private static boolean active;
    private static float lockedYaw;
    private static float lockedPitch;
    private static Vec3d lockedPos;
    private static Vec3d lockedRayOrigin;
    private static HitResult storedHit;
    private static BlockPos storedBlockPos;
    private static BlockPos airCandidatePos;
    private static int airCandidateTicks;
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
        airCandidatePos = null;
        airCandidateTicks = 0;
        updateStoredBlockPos(hit);
        progressionDirection = resolveProgressionDirection(hit, yaw, pitch);
    }

    public static void deactivate() {
        active = false;
        lockedPos = null;
        lockedRayOrigin = null;
        storedHit = null;
        storedBlockPos = null;
        airCandidatePos = null;
        airCandidateTicks = 0;
        progressionDirection = null;
        autoMineTarget = null;
    }

    /**
     * Uses vanilla reach and advances a virtual ray origin when blocks are confirmed gone.
     * Confirmation delay helps avoid marching forward on lag/rubber-band ghost breaks.
     */
    public static void refreshLockedRaycast(MinecraftClient mc) {
        if (!active || mc == null || mc.world == null || mc.player == null || lockedPos == null) return;

        if (progressionDirection == null) progressionDirection = resolveProgressionDirection(storedHit, lockedYaw, lockedPitch);

        Vec3d direction = Vec3d.of(progressionDirection.getVector());

        if (lockedRayOrigin == null) {
            double eyeY = lockedPos.y + mc.player.getEyeHeight(mc.player.getPose());
            lockedRayOrigin = new Vec3d(lockedPos.x, eyeY, lockedPos.z);
        }

        Vec3d end = lockedRayOrigin.add(direction.multiply(VANILLA_REACH));
        Entity entity = mc.player;
        HitResult directHit = mc.world.raycast(new RaycastContext(lockedRayOrigin, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));

        // Only move forward when:
        // 1) current tracked block is consistently air (anti-rubber-band delay)
        // 2) there is no block left in current vanilla-reach segment (avoid skipping into walls)
        if (storedBlockPos != null
            && mc.world.getBlockState(storedBlockPos).isAir()
            && directHit.getType() == HitResult.Type.MISS) {
            if (storedBlockPos.equals(airCandidatePos)) airCandidateTicks++;
            else {
                airCandidatePos = storedBlockPos;
                airCandidateTicks = 1;
            }

            if (airCandidateTicks >= AIR_CONFIRM_TICKS) {
                lockedRayOrigin = lockedRayOrigin.add(direction);
                airCandidatePos = null;
                airCandidateTicks = 0;
                end = lockedRayOrigin.add(direction.multiply(VANILLA_REACH));
                directHit = mc.world.raycast(new RaycastContext(lockedRayOrigin, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
            }
        } else {
            airCandidatePos = null;
            airCandidateTicks = 0;
        }

        HitResult hit = directHit;

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

    public static void syncPlayerPosition(Vec3d currentPlayerPos) {
        if (!active || currentPlayerPos == null) return;

        if (lockedPos == null) {
            lockedPos = currentPlayerPos;
            return;
        }

        Vec3d delta = currentPlayerPos.subtract(lockedPos);
        if (delta.lengthSquared() <= 1.0e-8) return;

        lockedPos = currentPlayerPos;

        // Keep the virtual ray origin aligned with player movement to reduce server-side view desync.
        if (lockedRayOrigin != null) lockedRayOrigin = lockedRayOrigin.add(delta);
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
