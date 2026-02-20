package com.example.addon.modules;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class FreecamMiningState {
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

        if (hit instanceof BlockHitResult blockHit) storedBlockPos = blockHit.getBlockPos();
        else storedBlockPos = null;
    }

    public static void deactivate() {
        active = false;
        lockedPos = null;
        storedHit = null;
        storedBlockPos = null;
        autoMineTarget = null;
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
