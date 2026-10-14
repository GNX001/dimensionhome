package com.dimensionhome.world;

import com.dimensionhome.DHConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 《维度之家》空岛维度的区块生成器：**纯虚空**。
 *
 * <p>它不生成任何地形、矿物、结构、树木或村庄；所有方块都保持空气。
 * 唯一的门与与门等宽的落脚平台由 {@link com.dimensionhome.world.SkyLandmarkBuilder}
 * 在服务器启动时程序化放置，不依赖地形生成。</p>
 *
 * <p>生物群系仍复用主世界分布（由数据包 JSON 里的
 * {@code minecraft:multi_noise} + {@code preset: minecraft:overworld} 指定），
 * 因此空岛里的怪物种类与主世界一致，只是没有可以刷怪的地面。</p>
 */
public class VoidChunkGenerator extends ChunkGenerator {

    /**
     * 数据包 JSON 用的编解码器。
     *
     * <p>只序列化 {@code biome_source}，其余参数（本维度）全部固定为虚空语义，
     * 无需在 JSON 里配置。</p>
     */
    public static final Codec<VoidChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(VoidChunkGenerator::getBiomeSource)
    ).apply(instance, VoidChunkGenerator::new));

    public VoidChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** 返回本生成器的编解码器，供 {@code Registries.CHUNK_GENERATOR} 分派。 */
    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * 生成区块的地形噪声。
     *
     * <p>虚空实现：立刻返回已完成的 Future，不写入任何方块。
     * 同时初始化 Heightmap，否则光照/寻路在高版本会报警并出现错误高度。</p>
     */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        // 虚空：不生成任何方块，但必须初始化高度图，避免光照与寻路异常。
        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * 为区块计算生物群系。
     *
     * <p>复用 {@link BiomeSource}（即数据包里指定的主世界 multi_noise 分布），
     * 让空岛与主世界的群系分区保持一致。</p>
     */
    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState randomState, Blender blender,
                                                       StructureManager structureManager, ChunkAccess chunk) {
        chunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
        return CompletableFuture.completedFuture(chunk);
    }

    /** 虚空：不做任何洞穴雕刻。 */
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carving) {
        // 虚空：无洞穴、无峡谷。
    }

    /** 虚空：不做任何地表替换（没有地形可替换）。 */
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                             ChunkAccess chunk) {
        // 虚空：没有地表。
    }

    /** 虚空：不生成结构（不生成村庄、要塞、矿脉等）。 */
    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
                                 StructureManager structureManager, ChunkAccess chunk,
                                 StructureTemplateManager templateManager) {
        // 虚空：不放置任何结构。
    }

    /**
     * 虚空：不执行生物群系装饰。
     *
     * <p>这一步原本会放置树木、花草、矿石与结构片段；空实现保证
     * 空岛连一根草都不会长出来。</p>
     */
    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel region, ChunkAccess chunk,
                                     StructureManager structureManager) {
        // 虚空：不放置任何装饰物（树、花草、矿石、结构片段）。
    }

    /** 虚空：不生成最初的原版怪物。 */
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 虚空：平台是人工建造的，初始怪物生成交由常规刷怪规则处理。
    }

    /** 维度高度 256 格（与 sky_type.json 的 height 一致）。 */
    @Override
    public int getGenDepth() {
        return 256;
    }

    /** 维度最低 Y = 0（与 sky_type.json 的 min_y 一致）。 */
    @Override
    public int getMinY() {
        return 0;
    }

    /** 虚空没有海平面，返回 0 以满足接口要求。 */
    @Override
    public int getSeaLevel() {
        return 0;
    }

    /**
     * 玩家的默认出生高度。
     *
     * <p>返回平台上方一格，避免玩家在空岛出生时直接坠入虚空。</p>
     */
    @Override
    public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
        return DHConfig.PLATFORM_Y + 1;
    }

    /**
     * 读取某列的地表高度。
     *
     * <p>虚空里除了人工平台以外没有任何方块，因此统一返回维度最低点。</p>
     */
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor levelHeightAccessor,
                             RandomState randomState) {
        return levelHeightAccessor.getMinBuildHeight();
    }

    /**
     * 读取某列的方块数据。
     *
     * <p>返回全空气的列；{@link NoiseColumn} 对越界 Y 也会自动返回空气。</p>
     */
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        int height = levelHeightAccessor.getHeight();
        BlockState[] states = new BlockState[height];
        for (int i = 0; i < height; i++) {
            states[i] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(levelHeightAccessor.getMinBuildHeight(), states);
    }

    /** 调试屏信息：留空即可。 */
    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("DimensionHome void generator (empty)");
    }
}
