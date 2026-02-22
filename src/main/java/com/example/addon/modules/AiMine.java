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
    private enum Pattern { Straight, Diagonal }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Pattern> pattern = sgGeneral.add(new EnumSetting.Builder<Pattern>()
        .name("path-pattern")
        .description("Straight forward lanes or alternating diagonal lanes.")
        .defaultValue(Pattern.Diagonal)
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
        .description("Max yaw change per tick. Higher feels smoother/less robotic.")
        .defaultValue(6.0)
        .range(1.0, 20.0)
        .sliderRange(2.0, 12.0)
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
        .description("Avoid blocks that lead into caves/drops.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidGravityBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-gravel-sand")
        .description("Avoid mining into gravel/sand pockets.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> warnIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("no-path-warn-interval")
        .description("Ticks between 'no path' warnings.")
        .defaultValue(40)
        .range(5, 200)
        .sliderRange(10, 100)
        .build());

    private final Setting<Integer> stuckTicksLimit = sgGeneral.add(new IntSetting.Builder()
        .name("stuck-ticks")
        .description("Ticks with tiny movement before unstick behavior triggers.")
        .defaultValue(20)
        .range(5, 80)
        .sliderRange(10, 40)
        .build());

    private BlockPos waypoint;
    private BlockPos miningTarget;
    private int nextWarnTick;

    private float baseYaw;
    private boolean diagRight;

    private Vec3d lastPos;
    private int stuckTicks;
    private int jumpTicks;

    public AiMine() {
        super(AddonTemplate.CATEGORY, "Ai Mine", "Hazard-aware auto miner that follows straight/diagonal lanes.");
    }

    @Override
    public void onActivate() {
        waypoint = null;
        miningTarget = null;
        nextWarnTick = 0;
        baseYaw = mc.player != null ? mc.player.getYaw() : 0f;
        diagRight = true;
        lastPos = mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : null;
        stuckTicks = 0;
        jumpTicks = 0;
    }

    @Override
    public void onDeactivate() {
        releaseMovement();
        if (mc.options != null) setKey(mc.options.attackKey, false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        BlockPos feet = mc.player.getBlockPos();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        if (isImmediateDanger(feet)) {
            BlockPos escape = findEscapeWaypoint(feet);
            if (escape != null) waypoint = escape;
            else {
                warning("Danger detected (lava/cave/fall). Stopping Ai Mine.");
                toggle();
                return;
            }
        }

        updateStuck(playerPos);

        if (waypoint == null || playerPos.squaredDistanceTo(Vec3d.ofCenter(waypoint)) < 1.2 || stuckTicks > stuckTicksLimit.get()) {
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

        if (stuckTicks > stuckTicksLimit.get()) jumpTicks = 6;
        moveToward(waypoint, needsMining);
    }

    private void updateStuck(Vec3d pos) {
        if (lastPos == null) {
            lastPos = pos;
            return;
        }

        if (pos.squaredDistanceTo(lastPos) < 0.01) stuckTicks++;
        else stuckTicks = 0;

        lastPos = pos;
    }

    private BlockPos pickNextWaypoint(BlockPos from) {
        int[] step = laneStep();

        for (int d = stepDistance.get(); d <= stepDistance.get() + 4; d++) {
            BlockPos candidate = from.add(step[0] * d, 0, step[1] * d);
            if (isTraversableOrCarvable(candidate)) {
                if (pattern.get() == Pattern.Diagonal) diagRight = !diagRight;
                return candidate;
            }
        }

        // Mine around hazards: try side-step candidates before giving up.
        int sx = -step[1];
        int sz = step[0];
        for (int d = 2; d <= 6; d++) {
            BlockPos left = from.add(sx * d, 0, sz * d);
            if (isTraversableOrCarvable(left)) return left;
            BlockPos right = from.add(-sx * d, 0, -sz * d);
            if (isTraversableOrCarvable(right)) return right;
        }

        return null;
    }

    private int[] laneStep() {
        float yaw = baseYaw;
        if (pattern.get() == Pattern.Diagonal) yaw = baseYaw + (diagRight ? 45f : -45f);

        double r = Math.toRadians(yaw);
        int dx = (int) Math.round(-Math.sin(r));
        int dz = (int) Math.round(Math.cos(r));

        if (dx == 0 && dz == 0) dz = 1;
        return new int[] { dx, dz };
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

        // If stuck, try mining around the obstacle to unstick.
        if (stuckTicks > stuckTicksLimit.get()) {
            BlockPos sideL = from.offset(forward.rotateYCounterclockwise());
            if (isBreakCandidate(sideL)) {
                miningTarget = sideL;
                return true;
            }
            BlockPos sideR = from.offset(forward.rotateYClockwise());
            if (isBreakCandidate(sideR)) {
                miningTarget = sideR;
                return true;
            }
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
                BlockState near = mc.world.getBlockState(feet.offset(d));
                if (near.isOf(Blocks.LAVA) || near.isOf(Blocks.WATER)) return true;
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

    private BlockPos findEscapeWaypoint(BlockPos from) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos c = from.offset(d, 2);
            if (isSafeStandingPos(c)) return c;
        }
        return null;
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
        float smoothFactor = 0.55f;
        mc.player.setYaw(currentYaw + Math.copySign(step * smoothFactor, delta));

        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);
        setKey(mc.options.backKey, false);

        boolean alignEnough = Math.abs(delta) < 65f;
        boolean pauseForMine = activelyMining && Math.abs(delta) > 30f;
        setKey(mc.options.forwardKey, alignEnough && !pauseForMine);

        if (jumpTicks > 0) {
            setKey(mc.options.jumpKey, true);
            jumpTicks--;
        } else {
            BlockPos ahead = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
            boolean obstacle = !mc.world.getBlockState(ahead).isAir() && mc.world.getBlockState(ahead.up()).isAir();
            setKey(mc.options.jumpKey, obstacle && !activelyMining);
        }
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
