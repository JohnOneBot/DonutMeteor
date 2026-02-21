package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SusChunkFinder extends Module {
    private enum RenderMode {
        FullChunk,
        TopPlane
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce newly flagged suspicious chunks in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectGeode = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-geode-growth")
        .description("Flags chunks with heavy budding amethyst + amethyst growth.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectRotatedDeepslate = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Flags chunks with lots of rotated deepslate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectKelp = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-kelp-pattern")
        .description("Flags chunks with suspicious kelp farm-like patterns.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectCaveVines = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-cave-vines")
        .description("Flags chunks with suspiciously dense fully-grown cave vines with glow berries.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minBudding = sgGeneral.add(new IntSetting.Builder()
        .name("min-budding-amethyst")
        .description("Minimum budding amethyst blocks in chunk to flag geode growth.")
        .defaultValue(8)
        .range(1, 512)
        .sliderRange(1, 64)
        .visible(detectGeode::get)
        .build()
    );

    private final Setting<Integer> minClusters = sgGeneral.add(new IntSetting.Builder()
        .name("min-amethyst-growth")
        .description("Minimum amethyst growth blocks (small/medium/large/cluster) in chunk to flag geode growth.")
        .defaultValue(10)
        .range(1, 1024)
        .sliderRange(1, 128)
        .visible(detectGeode::get)
        .build()
    );

    private final Setting<Integer> minRotatedDeepslate = sgGeneral.add(new IntSetting.Builder()
        .name("min-rotated-deepslate")
        .description("Minimum rotated deepslate blocks (axis not Y) in chunk to flag.")
        .defaultValue(6)
        .range(1, 4096)
        .sliderRange(1, 256)
        .visible(detectRotatedDeepslate::get)
        .build()
    );

    private final Setting<Integer> minKelpColumns = sgGeneral.add(new IntSetting.Builder()
        .name("min-kelp-columns")
        .description("Minimum tall kelp columns in chunk to flag kelp pattern.")
        .defaultValue(10)
        .range(1, 256)
        .sliderRange(1, 64)
        .visible(detectKelp::get)
        .build()
    );

    private final Setting<Integer> minKelpHeight = sgGeneral.add(new IntSetting.Builder()
        .name("min-kelp-height")
        .description("Minimum kelp height for a column to count.")
        .defaultValue(8)
        .range(1, 64)
        .sliderRange(1, 32)
        .visible(detectKelp::get)
        .build()
    );

    private final Setting<Double> minKelpTopRatio = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-kelp-top62-ratio")
        .description("Required ratio of counted kelp columns with top at Y=62.")
        .defaultValue(0.6)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(detectKelp::get)
        .build()
    );

    private final Setting<Integer> minCaveVineColumns = sgGeneral.add(new IntSetting.Builder()
        .name("min-cave-vine-columns")
        .description("Minimum fully-grown cave vine columns to flag a chunk.")
        .defaultValue(8)
        .range(1, 256)
        .sliderRange(1, 64)
        .visible(detectCaveVines::get)
        .build()
    );

    private final Setting<Integer> minCaveVineHeight = sgGeneral.add(new IntSetting.Builder()
        .name("min-cave-vine-height")
        .description("Minimum cave vine column height to count as fully-grown.")
        .defaultValue(4)
        .range(1, 64)
        .sliderRange(1, 16)
        .visible(detectCaveVines::get)
        .build()
    );

    private final Setting<Integer> minGlowBerries = sgGeneral.add(new IntSetting.Builder()
        .name("min-glow-berries")
        .description("Minimum cave vine blocks with berries in a chunk to flag.")
        .defaultValue(12)
        .range(1, 512)
        .sliderRange(1, 64)
        .visible(detectCaveVines::get)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgGeneral.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("How suspicious chunks are rendered.")
        .defaultValue(RenderMode.TopPlane)
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
    private final Map<ChunkPos, String> reasons = new HashMap<>();

    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "sus-chunks", "Flags suspicious chunks using geode growth, rotated deepslate, kelp, and cave vine/glow berry patterns.");
    }

    @Override
    public void onActivate() {
        flaggedChunks.clear();
        reasons.clear();
        if (mc.world == null) return;

        for (Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) scanChunk(worldChunk);
        }
    }

    @Override
    public void onDeactivate() {
        flaggedChunks.clear();
        reasons.clear();
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
        int rotated = 0;

        int kelpColumns = 0;
        int kelpTopsAt62 = 0;
        int caveVineColumns = 0;
        int glowBerries = 0;

        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        for (int x = cpos.getStartX(); x < cpos.getStartX() + 16; x++) {
            for (int z = cpos.getStartZ(); z < cpos.getStartZ() + 16; z++) {
                int kelpBottom = -1;
                int kelpTop = -1;
                int vineBottom = -1;
                int vineTop = -1;
                int vineBerryBlocks = 0;

                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.isOf(Blocks.BUDDING_AMETHYST)) budding++;
                    if (state.getBlock() instanceof AmethystClusterBlock) growth++;

                    if (state.isOf(Blocks.DEEPSLATE)
                        && state.contains(Properties.AXIS)
                        && state.get(Properties.AXIS) != Direction.Axis.Y) {
                        rotated++;
                    }

                    Block block = state.getBlock();
                    if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
                        if (kelpBottom < 0) kelpBottom = y;
                        kelpTop = y;
                    }

                    if (state.isOf(Blocks.CAVE_VINES) || state.isOf(Blocks.CAVE_VINES_PLANT)) {
                        if (vineBottom < 0) vineBottom = y;
                        vineTop = y;

                        if (state.contains(Properties.BERRIES) && state.get(Properties.BERRIES)) {
                            vineBerryBlocks++;
                            glowBerries++;
                        }
                    }
                }

                if (kelpBottom >= 0 && (kelpTop - kelpBottom + 1) >= minKelpHeight.get()) {
                    kelpColumns++;
                    if (kelpTop == 62) kelpTopsAt62++;
                }

                if (vineBottom >= 0
                    && (vineTop - vineBottom + 1) >= minCaveVineHeight.get()
                    && vineBerryBlocks > 0) {
                    caveVineColumns++;
                }
            }
        }

        boolean geodeSus = detectGeode.get() && budding >= minBudding.get() && growth >= minClusters.get();
        boolean rotatedSus = detectRotatedDeepslate.get() && rotated >= minRotatedDeepslate.get();
        boolean kelpSus = detectKelp.get()
            && kelpColumns >= minKelpColumns.get()
            && (kelpColumns == 0 ? false : ((double) kelpTopsAt62 / kelpColumns) >= minKelpTopRatio.get());
        boolean caveVineSus = detectCaveVines.get()
            && caveVineColumns >= minCaveVineColumns.get()
            && glowBerries >= minGlowBerries.get();

        boolean suspicious = geodeSus || rotatedSus || kelpSus || caveVineSus;

        String reason = "";
        if (geodeSus) reason += "geode ";
        if (rotatedSus) reason += "rotated_deepslate ";
        if (kelpSus) reason += "kelp ";
        if (caveVineSus) reason += "cave_vines ";

        boolean added;
        if (suspicious) {
            added = flaggedChunks.add(cpos);
            reasons.put(cpos, reason.trim());
        } else {
            flaggedChunks.remove(cpos);
            reasons.remove(cpos);
            added = false;
        }

        if (added && chatFeedback.get()) {
            info("Flagged " + cpos + " [" + reasons.get(cpos) + "] (budding=" + budding + ", growth=" + growth + ", rotated=" + rotated + ", kelp=" + kelpTopsAt62 + "/" + kelpColumns + ", cave_vines=" + caveVineColumns + ", glow_berries=" + glowBerries + ")");
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        for (ChunkPos pos : flaggedChunks) {
            if (renderMode.get() == RenderMode.TopPlane) {
                double y = 63;
                event.renderer.box(
                    pos.getStartX(), y, pos.getStartZ(),
                    pos.getStartX() + 16, y + 0.05, pos.getStartZ() + 16,
                    fillColor.get(), lineColor.get(), shapeMode.get(), 0
                );
            } else {
                int minY = mc.world.getBottomY();
                int maxY = mc.world.getBottomY() + mc.world.getHeight();
                event.renderer.box(
                    pos.getStartX(), minY, pos.getStartZ(),
                    pos.getStartX() + 16, maxY, pos.getStartZ() + 16,
                    fillColor.get(), lineColor.get(), shapeMode.get(), 0
                );
            }
        }
    }
}
