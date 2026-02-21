package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public class RtpMine extends Module {
    private enum FindMode {
        Stash,
        Spawner,
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> rtpCommand = sgGeneral.add(new StringSetting.Builder()
        .name("rtp-command")
        .description("Full RTP command, e.g. /rtp eu central")
        .defaultValue("/rtp eu central")
        .build()
    );

    private final Setting<FindMode> findMode = sgGeneral.add(new EnumSetting.Builder<FindMode>()
        .name("find-mode")
        .description("Disconnect condition.")
        .defaultValue(FindMode.Both)
        .build()
    );

    private final Setting<Integer> targetTopY = sgGeneral.add(new IntSetting.Builder()
        .name("target-top-y")
        .description("RTP trigger height. When this Y is reached, RTP to a new location.")
        .defaultValue(-30)
        .range(-64, 320)
        .build()
    );

    private final Setting<Integer> targetBottomY = sgGeneral.add(new IntSetting.Builder()
        .name("target-bottom-y")
        .description("Kept for compatibility; highest of top/bottom is used as trigger Y.")
        .defaultValue(-40)
        .range(-64, 320)
        .build()
    );

    private final Setting<Integer> stashThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("stash-block-threshold")
        .description("Fallback local stash detection threshold near player.")
        .defaultValue(8)
        .range(1, 128)
        .visible(() -> findMode.get() != FindMode.Spawner)
        .build()
    );

    private final Setting<Boolean> avoidLiquids = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-water-lava")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> centerMoveSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("center-move-speed")
        .defaultValue(0.12)
        .range(0.02, 0.4)
        .build()
    );

    private final Setting<Double> lookSmooth = sgGeneral.add(new DoubleSetting.Builder()
        .name("look-smooth")
        .defaultValue(0.2)
        .range(0.05, 1.0)
        .build()
    );

    private final Setting<Integer> liquidCheckDepth = sgGeneral.add(new IntSetting.Builder()
        .name("liquid-check-depth")
        .description("How many blocks below to scan for water/lava in the 3x3 drill path.")
        .defaultValue(10)
        .range(3, 32)
        .build()
    );

    private final Setting<Integer> maxSafeDrop = sgGeneral.add(new IntSetting.Builder()
        .name("max-safe-drop")
        .description("If open air drop below exceeds this, stop mining down and side-mine instead.")
        .defaultValue(6)
        .range(2, 30)
        .build()
    );

    private int retryRtpTicks;
    private boolean retryScheduled;
    private boolean awaitingRtpArrival;
    private BlockPos rtpStartPos;
    private BlockPos sideMineTarget;

    public RtpMine() {
        super(AddonTemplate.CATEGORY, "rtp-mine", "RTPs, mines down smoothly with a pickaxe, and disconnects on stash/spawner detection.");
    }

    @Override
    public void onActivate() {
        retryRtpTicks = 0;
        retryScheduled = false;
        awaitingRtpArrival = false;
        if (mc.options != null) mc.options.attackKey.setPressed(true);
        sideMineTarget = null;
        issueRtp(true);
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) mc.options.attackKey.setPressed(false);
        retryRtpTicks = 0;
        retryScheduled = false;
        awaitingRtpArrival = false;
        sideMineTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (!isHoldingPickaxe()) {
            error("Hold a pickaxe in main hand.");
            toggle();
            return;
        }

        if (findMode.get() != FindMode.Stash && hasSpawnerNearby(12)) {
            disconnect("spawner found");
            return;
        }

        if (findMode.get() != FindMode.Spawner && (isStashFinderTriggered() || hasLocalStashPattern(20))) {
            disconnect("stash found");
            return;
        }

        handleRtpArrivalAndRetry();
        if (awaitingRtpArrival) return;

        moveToBlockCenter();
        smoothLookDown();

        int triggerY = Math.max(targetTopY.get(), targetBottomY.get());
        if (mc.player.getBlockY() <= triggerY) {
            issueRtp(true);
            return;
        }

        BlockPos below = mc.player.getBlockPos().down();
        BlockState belowState = mc.world.getBlockState(below);

        boolean hazard = false;
        if (avoidLiquids.get()) {
            int liquidDepth = nearestLiquidDepthInDrillPath(below, liquidCheckDepth.get());
            if (liquidDepth != -1) hazard = true;
        }
        if (isUnsafeDrop(below)) hazard = true;

        if (hazard) {
            updateSideMineTarget(below);
            if (sideMineTarget != null) {
                smoothLookAtBlock(sideMineTarget);
                mc.interactionManager.updateBlockBreakingProgress(sideMineTarget, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            return;
        }

        sideMineTarget = null;

        if (!belowState.isAir()) {
            mc.interactionManager.updateBlockBreakingProgress(below, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void handleRtpArrivalAndRetry() {
        if (!awaitingRtpArrival) return;

        if (hasRtpMovedPlayer()) {
            awaitingRtpArrival = false;
            retryScheduled = false;
            retryRtpTicks = 0;
            return;
        }

        if (retryRtpTicks > 0) {
            retryRtpTicks--;
            if (retryRtpTicks == 0 && retryScheduled) {
                issueRtp(false);
            }
        }
    }

    private void issueRtp(boolean allowRetry) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        String raw = rtpCommand.get().trim();
        if (raw.isEmpty()) {
            error("rtp-command is empty.");
            toggle();
            return;
        }

        String command = raw.replaceFirst("^/+", "").trim().replaceAll("\\s+", " ");
        if (command.isEmpty()) {
            error("rtp-command is invalid.");
            toggle();
            return;
        }

        boolean sent = sendAsCommand(command);
        if (!sent) {
            // Fallback for mappings/versions without exposed command API.
            // Sending a clean slash-command chat string still executes server commands.
            mc.player.networkHandler.sendChatMessage("/" + command);
        }

        info("RTP -> /" + command);
        rtpStartPos = mc.player.getBlockPos();
        awaitingRtpArrival = true;

        retryScheduled = allowRetry;
        retryRtpTicks = allowRetry ? 20 : 0;
    }

    private boolean sendAsCommand(String commandNoSlash) {
        try {
            Object handler = mc.player.networkHandler;
            for (String methodName : new String[] {"sendChatCommand", "sendCommand"}) {
                try {
                    Method method = handler.getClass().getMethod(methodName, String.class);
                    method.invoke(handler, commandNoSlash);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean hasRtpMovedPlayer() {
        if (mc.player == null || rtpStartPos == null) return false;
        return mc.player.getBlockPos().getManhattanDistance(rtpStartPos) >= 2;
    }

    private void moveToBlockCenter() {
        BlockPos bp = mc.player.getBlockPos();
        Vec3d center = new Vec3d(bp.getX() + 0.5, mc.player.getY(), bp.getZ() + 0.5);
        Vec3d delta = center.subtract(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        Vec3d horizontal = new Vec3d(delta.x, 0, delta.z);

        if (horizontal.lengthSquared() < 0.0004) return;

        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(
            vel.x + horizontal.x * centerMoveSpeed.get(),
            vel.y,
            vel.z + horizontal.z * centerMoveSpeed.get()
        );
    }

    private void smoothLookDown() {
        float current = mc.player.getPitch();
        float target = 90f;
        float next = (float) (current + (target - current) * lookSmooth.get());
        mc.player.setPitch(next);
    }

    private void updateSideMineTarget(BlockPos below) {
        if (sideMineTarget != null) {
            BlockState current = mc.world.getBlockState(sideMineTarget);
            if (!current.isAir() && !isLiquidBlock(current)) return;
        }

        sideMineTarget = null;
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos side = below.offset(dir);
            BlockState sideState = mc.world.getBlockState(side);
            if (sideState.isAir()) continue;
            if (isLiquidBlock(sideState)) continue;
            sideMineTarget = side;
            return;
        }
    }

    private void smoothLookAtBlock(BlockPos pos) {
        Vec3d eyes = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3d d = target.subtract(eyes);

        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));

        mc.player.setYaw(lerpAngle(mc.player.getYaw(), targetYaw, lookSmooth.get().floatValue()));
        mc.player.setPitch(mc.player.getPitch() + (targetPitch - mc.player.getPitch()) * lookSmooth.get().floatValue());
    }

    private float lerpAngle(float from, float to, float t) {
        float delta = to - from;
        while (delta < -180.0f) delta += 360.0f;
        while (delta > 180.0f) delta -= 360.0f;
        return from + delta * t;
    }

    private int nearestLiquidDepthInDrillPath(BlockPos below, int maxDepth) {
        for (int depth = 0; depth <= maxDepth; depth++) {
            int yOff = -depth;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = below.add(dx, yOff, dz);
                    if (isLiquidBlock(mc.world.getBlockState(p))) return depth;
                }
            }
        }
        return -1;
    }

    private boolean isUnsafeDrop(BlockPos below) {
        int air = 0;
        BlockPos cursor = below;
        while (air <= maxSafeDrop.get()) {
            BlockState s = mc.world.getBlockState(cursor);
            if (!s.isAir()) return false;
            air++;
            cursor = cursor.down();
        }
        return true;
    }

    private boolean isLiquidBlock(BlockState state) {
        return state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA) || !state.getFluidState().isEmpty();
    }

    private boolean isHoldingPickaxe() {
        ItemStack stack = mc.player.getMainHandStack();
        return stack.isIn(ItemTags.PICKAXES);
    }

    private boolean hasSpawnerNearby(int radius) {
        BlockPos base = mc.player.getBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (mc.world.getBlockState(base.add(x, y, z)).isOf(Blocks.SPAWNER)) return true;
                }
            }
        }
        return false;
    }

    private boolean hasLocalStashPattern(int radius) {
        BlockPos base = mc.player.getBlockPos();
        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = mc.world.getBlockState(base.add(x, y, z)).getBlock();
                    if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
                        || block == Blocks.SHULKER_BOX || block == Blocks.ENDER_CHEST
                        || block == Blocks.HOPPER || block == Blocks.DISPENSER || block == Blocks.DROPPER) {
                        count++;
                        if (count >= stashThreshold.get()) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isStashFinderTriggered() {
        try {
            Module stashFinder = Modules.get().get("stash-finder");
            if (stashFinder == null || !stashFinder.isActive()) return false;

            for (Field field : stashFinder.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(stashFinder);
                String name = field.getName().toLowerCase();
                if (!name.contains("chest") && !name.contains("barrel") && !name.contains("shulker")
                    && !name.contains("stash") && !name.contains("echest")) continue;

                if (value instanceof Collection<?> c && !c.isEmpty()) return true;
                if (value instanceof Map<?, ?> m && !m.isEmpty()) return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private void disconnect(String reason) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.getConnection().disconnect(Text.literal(reason));
        toggle();
    }
}
