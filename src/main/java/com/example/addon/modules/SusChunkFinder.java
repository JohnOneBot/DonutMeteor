package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce newly flagged suspicious chunks in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minBudding = sgGeneral.add(new IntSetting.Builder()
        .name("min-budding-amethyst")
        .description("Minimum budding amethyst blocks in chunk to flag.")
        .defaultValue(8)
        .range(1, 512)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> minClusters = sgGeneral.add(new IntSetting.Builder()
        .name("min-amethyst-growth")
        .description("Minimum amethyst growth blocks (small/medium/large/cluster) in chunk to flag.")
        .defaultValue(10)
        .range(1, 1024)
        .sliderRange(1, 128)
        .build()
    );

    private final Setting<Integer> minRotatedDeepslate = sgGeneral.add(new IntSetting.Builder()
        .name("min-rotated-deepslate")
        .description("Minimum rotated deepslate blocks (axis not Y) in chunk to flag.")
        .defaultValue(20)
        .range(1, 4096)
        .sliderRange(1, 256)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How suspicious chunks are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgGeneral.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Chunk fill color.")
        .defaultValue(new SettingColor(180, 20, 255, 20))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("outline-color")
        .description("Chunk outline color.")
        .defaultValue(new SettingColor(220, 90, 255, 180))
        .build()
    );

    private final Set<ChunkPos> flaggedChunks = new HashSet<>();

    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "sus-chunks", "Flags suspicious chunks using geode growth + rotated deepslate patterns.");
    }

    @Override
    public void onActivate() {
        flaggedChunks.clear();
        if (mc.world == null) return;

        for (Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) scanChunk(worldChunk);
        }
    }

    @Override
    public void onDeactivate() {
        flaggedChunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;
        Chunk chunk = mc.world.getChunk(event.pos);
        if (chunk instanceof WorldChunk worldChunk) scanChunk(worldChunk);
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();

        int budding = 0;
        int growth = 0;
        int rotatedDeepslate = 0;

        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        for (int x = cpos.getStartX(); x < cpos.getStartX() + 16; x++) {
            for (int z = cpos.getStartZ(); z < cpos.getStartZ() + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockState state = chunk.getBlockState(new BlockPos(x, y, z));

                    if (state.isOf(Blocks.BUDDING_AMETHYST)) budding++;
                    if (state.getBlock() instanceof AmethystClusterBlock) growth++;

                    if (state.isOf(Blocks.DEEPSLATE)
                        && state.contains(Properties.AXIS)
                        && state.get(Properties.AXIS) != Direction.Axis.Y) {
                        rotatedDeepslate++;
                    }
                }
            }
        }

        boolean suspicious = budding >= minBudding.get()
            && growth >= minClusters.get()
            && rotatedDeepslate >= minRotatedDeepslate.get();

        boolean added;
        if (suspicious) added = flaggedChunks.add(cpos);
        else {
            flaggedChunks.remove(cpos);
            added = false;
        }

        if (added && chatFeedback.get()) {
            info("Flagged " + cpos + " (budding=" + budding + ", growth=" + growth + ", rotated_deepslate=" + rotatedDeepslate + ")");
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        int minY = mc.world.getBottomY();
        int maxY = mc.world.getBottomY() + mc.world.getHeight();

        for (ChunkPos pos : flaggedChunks) {
            event.renderer.box(
                pos.getStartX(), minY, pos.getStartZ(),
                pos.getStartX() + 16, maxY, pos.getStartZ() + 16,
                fillColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }
}
