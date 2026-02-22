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
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AiMine extends Module {
    private enum Pattern {
        Straight,
        ZigZag
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Pattern> pattern = sgGeneral.add(new EnumSetting.Builder<Pattern>()
        .name("path-pattern")
        .description("How the miner advances through tunnels.")
        .defaultValue(Pattern.ZigZag)
        .build()
    );

    private final Setting<Integer> stepDistance = sgGeneral.add(new IntSetting.Builder()
        .name("step-distance")
        .description("How many blocks to advance before picking a new waypoint.")
        .defaultValue(5)
        .range(2, 12)
        .sliderRange(2, 10)
        .build()
    );

    private final Setting<Integer> zigZagWidth = sgGeneral.add(new IntSetting.Builder()
        .name("zigzag-width")
        .description("Side offset when using zigzag mode.")
        .defaultValue(2)
        .range(1, 5)
        .sliderRange(1, 4)
        .visible(() -> pattern.get() == Pattern.ZigZag)
        .build()
    );

    private final Setting<Double> turnSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("turn-speed")
        .description("Max yaw change per tick for smoother movement.")
        .defaultValue(5.0)
        .range(1.0, 30.0)
        .sliderRange(1.0, 15.0)
        .build()
    );

    private final Setting<Boolean> carveTunnel = sgGeneral.add(new BoolSetting.Builder()
        .name("carve-tunnel")
        .description("Allow selecting waypoints inside stone/deepslate and mining toward them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoidLiquids = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-liquids")
        .description("Avoid paths with nearby lava or water.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoidFalls = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-caves-falls")
        .description("Avoid blocks that lead into big drops/caves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoidGravityBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-gravel-sand")
        .description("Avoid mining into gravel/sand pockets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoMineFront = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-front-block")
        .description("Auto mine the front block(s) if path is blocked.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> warnIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("no-path-warn-interval")
        .description("Ticks between 'no path' warnings to avoid spam.")
        .defaultValue(40)
        .range(5, 200)
        .sliderRange(10, 100)
        .build()
    );

    private BlockPos waypoint;
    private boolean zigRight = true;
    private int nextWarnTick;

    public AiMine() {
        super(AddonTemplate.CATEGORY, "Ai Mine", "Hazard-aware auto miner that follows straight or zigzag lanes.");
    }

    @Override
    public void onActivate() {
        waypoint = null;
        zigRight = true;
        nextWarnTick = 0;
    }

    @Override
    public void onDeactivate() {
        releaseMovement();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

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

        if (autoMineFront.get()) mineBlockingBlocks(feet);
        moveToward(waypoint);
    }

    private BlockPos pickNextWaypoint(BlockPos from) {
        Vec3d look = Vec3d.fromPolar(0f, mc.player.getYaw()).normalize();
        int fx = (int) Math.signum(look.x);
        int fz = (int) Math.signum(look.z);

        if (Math.abs(look.x) < Math.abs(look.z)) fx = 0;
        else fz = 0;

        int sx = -fz;
        int sz = fx;

        int side = 0;
        if (pattern.get() == Pattern.ZigZag) {
            side = zigRight ? zigZagWidth.get() : -zigZagWidth.get();
            zigRight = !zigRight;
        }

        for (int d = stepDistance.get(); d <= stepDistance.get() + 4; d++) {
            BlockPos candidate = from.add(fx * d + sx * side, 0, fz * d + sz * side);
            if (isTraversableOrCarvable(candidate)) return candidate;
        }

        for (int turn = -1; turn <= 1; turn += 2) {
            for (int d = 3; d <= 8; d++) {
                BlockPos candidate = from.add(sx * turn * d, 0, sz * turn * d);
                if (isTraversableOrCarvable(candidate)) return candidate;
            }
        }

        return null;
    }

    private boolean isTraversableOrCarvable(BlockPos pos) {
        if (isSafeStandingPos(pos)) return true;
        return carveTunnel.get() && canCarveTunnelAt(pos);
    }

    private boolean isSafeStandingPos(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState floor = mc.world.getBlockState(pos.down());

        if (!feet.isAir() || !head.isAir()) return false;
        if (floor.isAir() || !floor.isOpaque()) return false;

        if (avoidLiquids.get()) {
            for (Direction dir : Direction.values()) {
                BlockState near = mc.world.getBlockState(pos.offset(dir));
                if (near.isOf(Blocks.LAVA) || near.isOf(Blocks.WATER)) return false;
            }
        }

        if (avoidGravityBlocks.get()) {
            BlockState above = mc.world.getBlockState(pos.up(2));
            if (isGravityBlock(above.getBlock())) return false;
        }

        if (avoidFalls.get()) {
            int airDepth = 0;
            BlockPos check = pos.down();
            for (int i = 0; i < 4; i++) {
                check = check.down();
                if (mc.world.getBlockState(check).isAir()) airDepth++;
            }
            if (airDepth >= 3) return false;
        }

        return true;
    }

    private boolean canCarveTunnelAt(BlockPos pos) {
        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState floor = mc.world.getBlockState(pos.down());

        if (!isMineableSolid(feet) || !isMineableSolid(head)) return false;
        if (floor.isAir()) return false;

        if (avoidLiquids.get() && (touchesLiquid(pos) || touchesLiquid(pos.up()))) return false;
        if (avoidGravityBlocks.get() && isGravityBlock(mc.world.getBlockState(pos.up(2)).getBlock())) return false;

        return true;
    }

    private boolean isMineableSolid(BlockState state) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        return block != Blocks.BEDROCK && block != Blocks.BARRIER;
    }

    private boolean isGravityBlock(Block block) {
        return block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.RED_SAND;
    }

    private void mineBlockingBlocks(BlockPos from) {
        Vec3d look = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw()).normalize();
        BlockPos front = from.add((int) Math.signum(look.x), 0, (int) Math.signum(look.z));

        mineIfNeeded(front, from);
        mineIfNeeded(front.up(), from);
    }

    private void mineIfNeeded(BlockPos target, BlockPos from) {
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || !state.getFluidState().isEmpty()) return;
        if (avoidLiquids.get() && touchesLiquid(target)) return;
        if (avoidGravityBlocks.get() && isGravityBlock(state.getBlock())) return;

        Direction face = directionToward(from, target);
        if (mc.interactionManager != null) {
            mc.interactionManager.updateBlockBreakingProgress(target, face);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private Direction directionToward(BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (Math.abs(dx) > Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        if (dz != 0) return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        return Direction.UP;
    }

    private boolean touchesLiquid(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState state = mc.world.getBlockState(pos.offset(d));
            if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) return true;
        }
        return false;
    }

    private void moveToward(BlockPos target) {
        Vec3d targetPos = Vec3d.ofCenter(target);
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        float desiredYaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232D) - 90f;

        float currentYaw = mc.player.getYaw();
        float delta = MathHelper.wrapDegrees(desiredYaw - currentYaw);
        float step = (float) Math.min(Math.abs(delta), turnSpeed.get());
        mc.player.setYaw(currentYaw + Math.copySign(step, delta));

        setKey(mc.options.forwardKey, true);
        setKey(mc.options.backKey, false);
        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);

        BlockPos ahead = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
        boolean obstacle = !mc.world.getBlockState(ahead).isAir() && mc.world.getBlockState(ahead.up()).isAir();
        setKey(mc.options.jumpKey, obstacle);
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
