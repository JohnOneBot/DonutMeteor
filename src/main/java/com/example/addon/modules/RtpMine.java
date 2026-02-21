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

    private final Setting<String> region = sgGeneral.add(new StringSetting.Builder()
        .name("rtp-region")
        .description("Region argument appended to /rtp command, e.g. 'eu central'.")
        .defaultValue("eu central")
        .build()
    );

    private final Setting<FindMode> findMode = sgGeneral.add(new EnumSetting.Builder<FindMode>()
        .name("find-mode")
        .description("Disconnect condition.")
        .defaultValue(FindMode.Both)
        .build()
    );

    private final Setting<Integer> topY = sgGeneral.add(new IntSetting.Builder()
        .name("target-top-y")
        .defaultValue(-30)
        .range(-64, 320)
        .build()
    );

    private final Setting<Integer> bottomY = sgGeneral.add(new IntSetting.Builder()
        .name("target-bottom-y")
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
        .description("How fast player moves to block center to avoid edge/corner stuck.")
        .defaultValue(0.12)
        .range(0.02, 0.4)
        .build()
    );

    private final Setting<Double> lookSmooth = sgGeneral.add(new DoubleSetting.Builder()
        .name("look-smooth")
        .description("Pitch smoothing while looking down.")
        .defaultValue(0.2)
        .range(0.05, 1.0)
        .build()
    );

    private final Setting<Integer> maxSafeDrop = sgGeneral.add(new IntSetting.Builder()
        .name("max-safe-drop")
        .description("If air-drop below is bigger than this, RTP instead of continuing to mine down.")
        .defaultValue(6)
        .range(2, 30)
        .build()
    );

    private int rtpCooldown;
    private int retryRtpTicks;
    private boolean retryScheduled;
    private BlockPos rtpStartPos;

    public RtpMine() {
        super(AddonTemplate.CATEGORY, "rtp-mine", "RTPs, mines down smoothly with a pickaxe, and disconnects on stash/spawner detection.");
    }

    @Override
    public void onActivate() {
        rtpCooldown = 0;
        retryRtpTicks = 0;
        retryScheduled = false;
        if (mc.options != null) mc.options.attackKey.setPressed(true);
        issueRtp(true);
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) mc.options.attackKey.setPressed(false);
        retryScheduled = false;
        retryRtpTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (retryRtpTicks > 0) {
            retryRtpTicks--;
            if (retryRtpTicks == 0 && retryScheduled && !hasRtpMovedPlayer()) {
                issueRtp(false);
            }
        }

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

        int y = mc.player.getBlockY();
        int min = Math.min(topY.get(), bottomY.get());
        int max = Math.max(topY.get(), bottomY.get());

        if (y <= max && y >= min) {
            if (rtpCooldown <= 0) {
                issueRtp(true);
                rtpCooldown = 40;
            } else rtpCooldown--;
            return;
        }

        moveToBlockCenter();
        smoothLookDown();

        BlockPos below = mc.player.getBlockPos().down();
        BlockState belowState = mc.world.getBlockState(below);

        if (avoidLiquids.get() && (isLiquidBlock(belowState) || hasLiquidInDrillPath(below))) {
            if (!tryMineSafeSide(below)) {
                if (rtpCooldown <= 0) {
                    issueRtp(true);
                    rtpCooldown = 40;
                } else rtpCooldown--;
            }
            return;
        }

        if (isUnsafeDrop(below)) {
            if (rtpCooldown <= 0) {
                issueRtp(true);
                rtpCooldown = 40;
            } else rtpCooldown--;
            return;
        }

        if (!belowState.isAir()) {
            mc.interactionManager.updateBlockBreakingProgress(below, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void issueRtp(boolean allowRetry) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        String reg = region.get().trim();
        String command = reg.isEmpty() ? "rtp" : "rtp " + reg;

        boolean sent = sendAsCommand(command);
        if (!sent) {
            String cmdWithSlash = "/" + command;
            mc.player.networkHandler.sendChatMessage(cmdWithSlash);
        }

        info("RTP -> " + (reg.isEmpty() ? "default" : reg));
        rtpStartPos = mc.player.getBlockPos();

        retryScheduled = allowRetry;
        retryRtpTicks = allowRetry ? 20 : 0;
    }

    private boolean sendAsCommand(String commandNoSlash) {
        try {
            Object handler = mc.player.networkHandler;
            for (String m : new String[] {"sendChatCommand", "sendCommand"}) {
                try {
                    Method method = handler.getClass().getMethod(m, String.class);
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
        if (mc.player == null || rtpStartPos == null) return true;
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

    private boolean tryMineSafeSide(BlockPos below) {
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos side = below.offset(dir);
            BlockState sideState = mc.world.getBlockState(side);
            if (sideState.isAir()) continue;
            if (isLiquidBlock(sideState)) continue;
            mc.interactionManager.updateBlockBreakingProgress(side, dir.getOpposite());
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private boolean hasLiquidInDrillPath(BlockPos below) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p1 = below.add(dx, 0, dz);
                BlockPos p2 = below.add(dx, -1, dz);
                if (isLiquidBlock(mc.world.getBlockState(p1)) || isLiquidBlock(mc.world.getBlockState(p2))) return true;
            }
        }
        return false;
    }

    private boolean isUnsafeDrop(BlockPos below) {
        int air = 0;
        BlockPos cursor = below;
        while (air <= maxSafeDrop.get()) {
            BlockState s = mc.world.getBlockState(cursor);
            if (!s.isAir()) return false;
            if (isLiquidBlock(s)) return true;
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
