package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class LeaveOnFeature extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> leaveOnTotemPop = sgGeneral.add(new BoolSetting.Builder()
        .name("leave-on-totem-pop")
        .description("Disconnect when your totem pops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> leaveOnStashFind = sgGeneral.add(new BoolSetting.Builder()
        .name("leave-on-stash-find")
        .description("Disconnect when Meteor Stash Finder has findings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> leaveOnSpawnerFind = sgGeneral.add(new BoolSetting.Builder()
        .name("leave-on-spawner-find")
        .description("Disconnect when a mob spawner is detected nearby.")
        .defaultValue(true)
        .build()
    );

    public LeaveOnFeature() {
        super(AddonTemplate.CATEGORY, "leave-on", "Auto disconnect safety for totem pop, stash find, or spawner find.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (leaveOnSpawnerFind.get() && hasSpawnerInLoadedChunks()) {
            disconnect("spawner found");
            return;
        }

        if (leaveOnStashFind.get() && isStashFinderTriggered()) {
            disconnect("stash found");
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!leaveOnTotemPop.get() || mc.player == null || mc.world == null) return;

        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {
                disconnect("totem poped");
            }
        }
    }

    private boolean hasSpawnerInLoadedChunks() {
        for (Chunk chunk : Utils.chunks()) {
            if (!(chunk instanceof WorldChunk worldChunk)) continue;

            int yMin = worldChunk.getBottomY();
            int yMax = yMin + worldChunk.getHeight();
            int xStart = worldChunk.getPos().getStartX();
            int zStart = worldChunk.getPos().getStartZ();

            for (int x = xStart; x < xStart + 16; x++) {
                for (int z = zStart; z < zStart + 16; z++) {
                    for (int y = yMin; y < yMax; y++) {
                        if (worldChunk.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.SPAWNER)) return true;
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
                String name = field.getName().toLowerCase();
                if (!name.contains("chest") && !name.contains("barrel") && !name.contains("shulker")
                    && !name.contains("stash") && !name.contains("echest") && !name.contains("finder")) continue;

                Object value = field.get(stashFinder);
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
