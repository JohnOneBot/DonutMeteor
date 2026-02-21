package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
import net.minecraft.item.PickaxeItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Field;
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

    private int rtpCooldown;

    public RtpMine() {
        super(AddonTemplate.CATEGORY, "rtp-mine", "RTPs, mines down smoothly with a pickaxe, and disconnects on stash/spawner detection.");
    }

    @Override
    public void onActivate() {
        rtpCooldown = 0;
        issueRtp();
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

        int y = mc.player.getBlockY();
        int min = Math.min(topY.get(), bottomY.get());
        int max = Math.max(topY.get(), bottomY.get());

        if (y <= max && y >= min) {
            if (rtpCooldown <= 0) {
                issueRtp();
                rtpCooldown = 40;
            } else rtpCooldown--;
            return;
        }

        BlockPos below = mc.player.getBlockPos().down();
        BlockState belowState = mc.world.getBlockState(below);

        if (avoidLiquids.get() && (belowState.isOf(Blocks.WATER) || belowState.isOf(Blocks.LAVA))) {
            if (rtpCooldown <= 0) {
                issueRtp();
                rtpCooldown = 40;
            } else rtpCooldown--;
            return;
        }

        if (!belowState.isAir()) {
            mc.player.setPitch(90f);
            mc.interactionManager.updateBlockBreakingProgress(below, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void issueRtp() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        String reg = region.get().trim();
        String cmd = reg.isEmpty() ? "/rtp" : "/rtp " + reg;
        mc.player.networkHandler.sendChatMessage(cmd);
        info("RTP -> " + (reg.isEmpty() ? "default" : reg));
    }

    private boolean isHoldingPickaxe() {
        ItemStack stack = mc.player.getMainHandStack();
        return stack.getItem() instanceof PickaxeItem;
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
