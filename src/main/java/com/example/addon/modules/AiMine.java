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
        .description("Auto mine the front block if path is blocked.")
        .defaultValue(true)
        .build()
    );

    private BlockPos waypoint;
    private int leg;
    private boolean zigRight = true;

    public AiMine() {
        super(AddonTemplate.CATEGORY, "Ai Mine", "Hazard-aware auto miner that follows straight or zigzag lanes.");
    }

    @Override
    public void onActivate() {
        waypoint = null;
        leg = 0;
        zigRight = true;
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
                warning("No safe path found. Stopping movement.");
                releaseMovement();
                return;
            }
        }

        if (autoMineFront.get()) mineBlockingBlock(feet);
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
            if (isSafeStandingPos(candidate)) {
                leg++;
                return candidate;
            }
        }

        for (int turn = -1; turn <= 1; turn += 2) {
            for (int d = 3; d <= 8; d++) {
                BlockPos candidate = from.add(sx * turn * d, 0, sz * turn * d);
                if (isSafeStandingPos(candidate)) return candidate;
            }
        }

        return null;
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
            BlockState a = mc.world.getBlockState(pos.up(2));
            if (isGravityBlock(a.getBlock())) return false;
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

    private boolean isGravityBlock(Block b) {
        return b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.RED_SAND;
    }

    private void mineBlockingBlock(BlockPos from) {
        Vec3d look = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw()).normalize();
        BlockPos front = from.add((int) Math.signum(look.x), 0, (int) Math.signum(look.z));
        BlockState state = mc.world.getBlockState(front);

        if (state.isAir() || !state.getFluidState().isEmpty()) return;
        if (avoidLiquids.get() && touchesLiquid(front)) return;
        if (avoidGravityBlocks.get() && isGravityBlock(state.getBlock())) return;

        Direction side = Direction.UP;
        if (mc.interactionManager != null) {
            mc.interactionManager.updateBlockBreakingProgress(front, side);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    private boolean touchesLiquid(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState s = mc.world.getBlockState(pos.offset(d));
            if (s.isOf(Blocks.WATER) || s.isOf(Blocks.LAVA)) return true;
        }
        return false;
    }

    private void moveToward(BlockPos target) {
        Vec3d tp = Vec3d.ofCenter(target);
        Vec3d pp = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        double dx = tp.x - pp.x;
        double dz = tp.z - pp.z;
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
