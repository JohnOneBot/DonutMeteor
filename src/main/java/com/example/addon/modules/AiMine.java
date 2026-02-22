package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AiMine extends Module {
    private enum Pattern { Straight, ZigZag }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Pattern> pattern = sgGeneral.add(new EnumSetting.Builder<Pattern>()
        .name("path-pattern")
        .description("How the miner advances through tunnels.")
        .defaultValue(Pattern.ZigZag)
        .build());

    private final Setting<Integer> stepDistance = sgGeneral.add(new IntSetting.Builder()
        .name("step-distance")
        .description("How many blocks to advance before picking a new waypoint.")
        .defaultValue(5)
        .range(2, 12)
        .sliderRange(2, 10)
        .build());

    private final Setting<Double> turnSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("turn-speed")
        .description("Max yaw change per tick for smoother movement.")
        .defaultValue(3.0)
        .range(1.0, 12.0)
        .sliderRange(1.0, 8.0)
        .build());

    private final Setting<Boolean> carveTunnel = sgGeneral.add(new BoolSetting.Builder()
        .name("carve-tunnel")
        .description("Allow selecting waypoints inside stone/deepslate and mining toward them.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidLiquids = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-liquids")
        .description("Avoid paths with nearby lava or water.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidFalls = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-caves-falls")
        .description("Avoid blocks that lead into big drops/caves.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidGravityBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-gravel-sand")
        .description("Avoid mining into gravel/sand pockets.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> warnIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("no-path-warn-interval")
        .description("Ticks between 'no path' warnings to avoid spam.")
        .defaultValue(40)
        .range(5, 200)
        .sliderRange(10, 100)
        .build());

    private BlockPos waypoint;
    private BlockPos miningTarget;
    private int nextWarnTick;

    private float baseYaw;
    private boolean zigRight;

    public AiMine() {
        super(AddonTemplate.CATEGORY, "Ai Mine", "Hazard-aware auto miner that follows straight or zigzag lanes.");
    }

    @Override
    public void onActivate() {
        waypoint = null;
        miningTarget = null;
        nextWarnTick = 0;

        baseYaw = mc.player != null ? mc.player.getYaw() : 0f;
        zigRight = true;
    }

    @Override
    public void onDeactivate() {
        releaseMovement();
        if (mc.options != null) setKey(mc.options.attackKey, false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        if (isImmediateDanger(mc.player.getBlockPos())) {
            warning("Danger detected (lava/cave/fall). Stopping Ai Mine.");
            toggle();
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        if (waypoint == null || playerPos.squaredDistanceTo(Vec3d.ofCenter(waypoint)) < 1.0) {
            waypoint = pickNextWaypoint(feet);
            if (waypoint == null) {
                if (mc.player.age >= nextWarnTick) {
                    warning("No safe path found. Stopping movement.");
                    nextWarnTick = mc.player.age + warnIntervalTicks.get();
                }
                releaseMovement();
                return;
            }
        }

        boolean needsMining = updateMiningTarget(feet);
        setKey(mc.options.attackKey, needsMining);
        moveToward(waypoint, needsMining);
    }

    private BlockPos pickNextWaypoint(BlockPos from) {
        float targetYaw = getTargetYaw();
        Direction forward = yawToCardinal(targetYaw);

        for (int d = stepDistance.get(); d <= stepDistance.get() + 4; d++) {
            BlockPos candidate = from.offset(forward, d);
            if (isTraversableOrCarvable(candidate)) {
                if (pattern.get() == Pattern.ZigZag) zigRight = !zigRight;
                return candidate;
            }
        }

        Direction left = forward.rotateYCounterclockwise();
        Direction right = forward.rotateYClockwise();

        for (int d = 2; d <= 6; d++) {
            BlockPos c1 = from.offset(left, d);
            if (isTraversableOrCarvable(c1)) return c1;
            BlockPos c2 = from.offset(right, d);
            if (isTraversableOrCarvable(c2)) return c2;
        }

        return null;
    }

    private float getTargetYaw() {
        if (pattern.get() == Pattern.Straight) return baseYaw;
        return baseYaw + (zigRight ? 45f : -45f);
    }

    private Direction yawToCardinal(float yaw) {
        int i = MathHelper.floor((yaw / 90.0f) + 0.5f) & 3;
        return switch (i) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private boolean isTraversableOrCarvable(BlockPos pos) {
        return isSafeStandingPos(pos) || (carveTunnel.get() && canCarveTunnelAt(pos));
    }

    private boolean isSafeStandingPos(BlockPos pos) {
        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState floor = mc.world.getBlockState(pos.down());

        if (!feet.isAir() || !head.isAir()) return false;
        if (floor.isAir() || !floor.isOpaque()) return false;

        if (avoidLiquids.get() && (touchesLiquid(pos) || floor.isOf(Blocks.LAVA) || floor.isOf(Blocks.WATER))) return false;

        if (avoidGravityBlocks.get() && isGravityBlock(mc.world.getBlockState(pos.up(2)).getBlock())) return false;

        if (avoidFalls.get()) {
            int airDepth = 0;
            BlockPos check = pos.down();
            for (int i = 0; i < 5; i++) {
                check = check.down();
                if (mc.world.getBlockState(check).isAir()) airDepth++;
            }
            if (airDepth >= 2) return false;
        }

        return true;
    }

    private boolean canCarveTunnelAt(BlockPos pos) {
        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState floor = mc.world.getBlockState(pos.down());

        if (!isMineableSolid(feet) || !isMineableSolid(head)) return false;
        if (floor.isAir() || floor.isOf(Blocks.LAVA) || floor.isOf(Blocks.WATER)) return false;
        if (avoidLiquids.get() && (touchesLiquid(pos) || touchesLiquid(pos.up()))) return false;
        if (avoidGravityBlocks.get() && isGravityBlock(mc.world.getBlockState(pos.up(2)).getBlock())) return false;

        return true;
    }

    private boolean isMineableSolid(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        return block != Blocks.BEDROCK && block != Blocks.BARRIER;
    }

    private boolean isGravityBlock(Block block) {
        return block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.RED_SAND;
    }

    private boolean updateMiningTarget(BlockPos from) {
        Direction forward = mc.player.getHorizontalFacing();

        BlockPos headFront = from.up().offset(forward);
        if (isBreakCandidate(headFront)) {
            miningTarget = headFront;
            return true;
        }

        BlockPos front = from.offset(forward);
        if (isBreakCandidate(front)) {
            miningTarget = front;
            return true;
        }

        miningTarget = null;
        return false;
    }

    private boolean isBreakCandidate(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (avoidLiquids.get() && touchesLiquid(pos)) return false;
        if (avoidGravityBlocks.get() && isGravityBlock(state.getBlock())) return false;
        return true;
    }

    private boolean touchesLiquid(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState state = mc.world.getBlockState(pos.offset(d));
            if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) return true;
        }
        return false;
    }

    private boolean isImmediateDanger(BlockPos feet) {
        if (!avoidLiquids.get() && !avoidFalls.get()) return false;

        if (avoidLiquids.get()) {
            BlockState below = mc.world.getBlockState(feet.down());
            if (below.isOf(Blocks.LAVA) || below.isOf(Blocks.WATER)) return true;

            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockState s = mc.world.getBlockState(feet.offset(d));
                if (s.isOf(Blocks.LAVA) || s.isOf(Blocks.WATER)) return true;
            }
        }

        if (avoidFalls.get()) {
            int airDepth = 0;
            BlockPos check = feet.down();
            for (int i = 0; i < 4; i++) {
                check = check.down();
                if (mc.world.getBlockState(check).isAir()) airDepth++;
            }
            if (airDepth >= 2) return true;
        }

        return false;
    }

    private void moveToward(BlockPos target, boolean activelyMining) {
        Vec3d targetPos = Vec3d.ofCenter(target);
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        float desiredYaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232D) - 90f;

        float currentYaw = mc.player.getYaw();
        float delta = MathHelper.wrapDegrees(desiredYaw - currentYaw);
        float step = (float) Math.min(Math.abs(delta), turnSpeed.get());
        mc.player.setYaw(currentYaw + Math.copySign(step, delta));

        // Smooth, legit-looking movement: no rapid left/right wiggle spam.
        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);
        setKey(mc.options.backKey, false);

        boolean alignEnough = Math.abs(delta) < 50f;
        boolean pauseForMine = activelyMining && Math.abs(delta) > 18f;
        setKey(mc.options.forwardKey, alignEnough && !pauseForMine);

        BlockPos ahead = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
        boolean obstacle = !mc.world.getBlockState(ahead).isAir() && mc.world.getBlockState(ahead.up()).isAir();
        setKey(mc.options.jumpKey, obstacle && !activelyMining);
    }

    private void releaseMovement() {
        if (mc.options == null) return;
        setKey(mc.options.forwardKey, false);
        setKey(mc.options.backKey, false);
        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);
        setKey(mc.options.jumpKey, false);
    }

    private void setKey(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
    }
}
