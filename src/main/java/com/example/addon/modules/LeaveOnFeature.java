package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class LeaveOnFeature extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> leaveOnTotemPop = sgGeneral.add(new BoolSetting.Builder()
        .name("leave-on-totem-pop")
        .description("Disconnect when your totem pops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> leaveOnSpawnerFind = sgGeneral.add(new BoolSetting.Builder()
        .name("leave-on-spawner-find")
        .description("Disconnect when any loaded chunk contains a mob spawner.")
        .defaultValue(true)
        .build()
    );

    private final Set<ChunkPos> spawnerChunks = new HashSet<>();

    public LeaveOnFeature() {
        super(AddonTemplate.CATEGORY, "leave-on", "Auto disconnect safety for totem pop or spawner find.");
    }

    @Override
    public void onActivate() {
        spawnerChunks.clear();

        if (mc.world == null) return;
        for (Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) scanChunkForSpawner(worldChunk);
        }
    }

    @Override
    public void onDeactivate() {
        spawnerChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (leaveOnSpawnerFind.get() && !spawnerChunks.isEmpty()) disconnect("spawner found");
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        scanChunkForSpawner(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;
        Chunk chunk = mc.world.getChunk(event.pos);
        if (chunk instanceof WorldChunk worldChunk) scanChunkForSpawner(worldChunk);
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

    private void scanChunkForSpawner(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();

        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                    if (state.isOf(Blocks.SPAWNER)) {
                        spawnerChunks.add(cpos);
                        return;
                    }
                }
            }
        }

        spawnerChunks.remove(cpos);
    }

    private void disconnect(String reason) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.getConnection().disconnect(Text.literal(reason));
        toggle();
    }
}
