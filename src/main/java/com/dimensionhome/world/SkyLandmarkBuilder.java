package com.dimensionhome.world;

import com.dimensionhome.DHConfig;
import net.kyrptonaught.customportalapi.CustomPortalApiRegistry;
import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.kyrptonaught.customportalapi.CustomPortalsMod;
import net.kyrptonaught.customportalapi.portal.frame.PortalFrameTester;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * 空岛地标建造器：搭建 **与门等宽的落脚平台** 与 **那一扇返回门**。
 *
 * <p>因为 {@link VoidChunkGenerator} 保持纯虚空（不生成任何地形），地标不能靠地形生成，
 * 必须在服务器启动后程序化放置，才能保证坐标精确、且必定存在。</p>
 *
 * <h3>门的几何（以展开轴 AXIS = X 为例，门平面固定在 z=2）</h3>
 * <pre>
 *   y=100+h    横框 ─────────────────────
 *   ...        │ 门  门  门 │            ← 内腔：宽 w、高 h（左下角在 (spanStart, 100, 2)）
 *   y=100      │ 门  门  门 │
 *   y=99  平台 ─────────────── 横框       ← 平台与门（含两侧门框）等宽，沿门法线前后各 PLATFORM_EXTEND 格
 *              z=-1  z=0  z=1  z=2（门平面）
 * </pre>
 * <p>展开轴为 Z 时整体旋转 90 度：门平面变成 x=2，门的宽度沿 Z 方向延伸。
 * 两种朝向共用同一套坐标换算（{@link SkyGateGeometry}）。</p>
 *
 * <h3>为什么门方块必须用 flag = 18</h3>
 * <p>18 = {@code UPDATE_CLIENTS(2) | UPDATE_KNOWN_SHAPE(16)}，与 CPA 的
 * {@code lightPortal} 保持一致：该 flag 不触发邻居形状更新，否则
 * {@code CustomPortalBlock.updateShape} 会在门还没铺满时把已放置的门方块变回空气。</p>
 */
public final class SkyLandmarkBuilder {

    private SkyLandmarkBuilder() {
    }

    /** 门方块写入用的 flag：静默（不触发 updateShape）。 */
    private static final int PORTAL_FLAG = 18;
    /** 普通方块写入用的 flag。 */
    private static final int BLOCK_FLAG = 3;
    /** v1.0.0 写死的平台是「以原点为中心的 3×3」，即 x,z ∈ [-1,1]；升级时用它找出残留。 */
    private static final int LEGACY_PLATFORM_HALF = 1;

    /**
     * 服务器启动时调用：确保空岛地标存在。
     *
     * <p>逻辑分三种情况：</p>
     * <ol>
     *   <li>区域内已经有门（玩家自己点的、或上次留下的）→ 什么都不做，尊重玩家现状；</li>
     *   <li>区域内没有门 → 按 {@link SkyGateData} 记录的形状重建那扇门
     *       （这也顺手修好了「拆了门出不去」的最坏情况）；</li>
     *   <li>落脚平台永远补齐（与门等宽、沿门法线前后各延伸
     *       {@link DHConfig#PLATFORM_EXTEND} 格），避免玩家把平台拆了之后没地方站。</li>
     * </ol>
     */
    public static void ensureLandmark(ServerLevel skyLevel, Logger logger) {
        if (skyLevel == null) {
            logger.error("维度之家：空岛维度未加载，无法建造落脚平台与传送门！"
                    + "请检查数据包 data/dimensionhome/dimension/sky_dimension.json");
            return;
        }

        SkyGateData data = SkyGateData.get(skyLevel);
        SkyGateShape shape = data.getShape();

        // 平台按当前记录的门形状铺，永远与那扇门等宽。
        buildPlatform(skyLevel, shape);

        if (SkyGateGeometry.anyPortalInZone(skyLevel)) {
            logger.info("维度之家：空岛可点燃区域内已存在传送门，保持现状不予重建");
            return;
        }

        buildGate(skyLevel, shape);
        logger.info("维度之家：空岛地标已就位 —— 落脚平台 y={}（与门等宽，沿门法线前后各延伸 {} 格）"
                        + "与唯一返回门（{}）",
                DHConfig.PLATFORM_Y, DHConfig.PLATFORM_EXTEND, data.describe());
    }

    /**
     * 按给定形状重建那扇门：先擦掉旧门（门框 + 门方块），再按新形状建造，
     * 并把落脚平台重新铺成与新门等宽。
     *
     * <p>用于「第一次进空岛时复刻来源门形状」。复刻来的门可能比默认门宽得多，
     * 因此平台必须跟着重铺 —— 否则门框会超出台面，玩家从门边走开就会踩空。</p>
     */
    public static void rebuildGate(ServerLevel skyLevel, SkyGateShape newShape, @Nullable SkyGateShape oldShape) {
        if (oldShape != null) {
            eraseGate(skyLevel, oldShape);
        }
        clearPortalBlocksInZone(skyLevel);

        SkyGateShape shape = newShape == null ? SkyGateShape.DEFAULT : newShape;
        buildPlatform(skyLevel, shape);
        buildGate(skyLevel, shape);
    }

    /**
     * 铺落脚平台（幂等，可重复调用）。
     *
     * <p>平台范围由门形状推导：沿展开轴取整扇门（含左右门框）的宽度，沿门法线方向
     * 从门平面向前后各延伸 {@link DHConfig#PLATFORM_EXTEND} 格。不再是写死的 3x3。</p>
     */
    public static void buildPlatform(ServerLevel skyLevel, SkyGateShape shape) {
        SkyGateShape shapeOrDefault = shape == null ? SkyGateShape.DEFAULT : shape;
        buildPlatform(skyLevel,
                shapeOrDefault.footprint(DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET));
    }

    /** 铺落脚平台（按门足迹，幂等）。 */
    public static void buildPlatform(ServerLevel skyLevel, SkyGateShape.SkyGateFootprint footprint) {
        clearLegacyPlatform(skyLevel, footprint);

        BlockState frame = Blocks.SMOOTH_STONE.defaultBlockState();
        int y = DHConfig.PLATFORM_Y;
        int planeMin = footprint.platformPlaneMin(DHConfig.PLATFORM_EXTEND);
        int planeMax = footprint.platformPlaneMax(DHConfig.PLATFORM_EXTEND);

        for (int s = footprint.platformSpanMin(); s <= footprint.platformSpanMax(); s++) {
            for (int p = planeMin; p <= planeMax; p++) {
                setBlock(skyLevel, SkyGateGeometry.at(footprint.span(), s, y, p), frame);
            }
        }
    }

    /**
     * 清理「旧版写死的 3×3 平台」遗留在当前平台之外的部分。
     *
     * <p>v1.0.0 的平台是以原点为中心的 3×3（x,z ∈ [-1,1]）；现在平台范围由门形状推导，
     * 默认形状下是 x ∈ [-1,2]、z ∈ [0,4] —— 旧平台靠门背后的那一行（z = -1，共 3 格）
     * 正好落在新平台之外。用旧 jar 的存档升级上来时，那 3 格会孤零零悬在门后，
     * 与「空岛只有一块落脚平台」的规则相矛盾。</p>
     *
     * <p>只清「旧 3×3 范围内、当前平台范围外、且当前方块确实是平滑石」的格子：
     * 门框位于门平面（属于当前平台）因此不会被碰到，玩家自建的其它方块也不会被误删。</p>
     */
    public static void clearLegacyPlatform(ServerLevel skyLevel, SkyGateShape.SkyGateFootprint footprint) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int y = DHConfig.PLATFORM_Y;
        int spanMin = footprint.platformSpanMin();
        int spanMax = footprint.platformSpanMax();
        int planeMin = footprint.platformPlaneMin(DHConfig.PLATFORM_EXTEND);
        int planeMax = footprint.platformPlaneMax(DHConfig.PLATFORM_EXTEND);

        for (int s = -LEGACY_PLATFORM_HALF; s <= LEGACY_PLATFORM_HALF; s++) {
            for (int p = -LEGACY_PLATFORM_HALF; p <= LEGACY_PLATFORM_HALF; p++) {
                if (s >= spanMin && s <= spanMax && p >= planeMin && p <= planeMax) {
                    continue;
                }
                BlockPos pos = SkyGateGeometry.at(footprint.span(), s, y, p);
                if (skyLevel.getBlockState(pos).is(Blocks.SMOOTH_STONE)) {
                    setBlock(skyLevel, pos, air);
                }
            }
        }
    }

    /**
     * 按形状建造一扇门：外框 + 内腔门方块。
     *
     * <p>外框底行（y=99）同时充当地板，所以玩家从平台走到任何宽度的门前都有落脚点。</p>
     */
    public static void buildGate(ServerLevel skyLevel, SkyGateShape shape) {
        SkyGateShape shapeOrDefault = shape == null ? SkyGateShape.DEFAULT : shape;
        buildGate(skyLevel, shapeOrDefault.footprint(DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET));
    }

    /**
     * 按给定的门足迹建造一扇门。
     *
     * <p>把足迹单独提出来作为参数，是为了让「锚点处那扇唯一的门」与「自检里
     * 临时搭的测试门框」走同一段建造代码 —— 否则测试用的几何与实际实现会各写一遍，
     * 一旦偏差，自检就失去意义。</p>
     */
    public static void buildGate(ServerLevel skyLevel, SkyGateShape.SkyGateFootprint fp) {
        buildGateFrame(skyLevel, fp);

        BlockState portal = CustomPortalHelper.blockWithAxis(
                CustomPortalsMod.getDefaultPortalBlock().defaultBlockState(), fp.span());

        // 内腔：门方块用 flag=18 静默写入。
        for (int s = fp.spanStart(); s <= fp.spanEnd(); s++) {
            for (int y = fp.interiorY(); y <= fp.interiorY() + fp.height() - 1; y++) {
                BlockPos pos = at(fp, s, y);
                skyLevel.getChunkAt(pos);
                skyLevel.setBlock(pos, portal, PORTAL_FLAG);
            }
        }
    }

    /**
     * 只搭门框、不点门。
     *
     * <p>自检需要「一个完全合法但尚未点燃的门框」来验证点燃规则，
     * 而 {@link #buildGate} 会顺手把门点亮，就测不出「放行」这一步了。
     * 所以把门框铺设备单独提出来，建造与自检共用同一段几何代码。</p>
     */
    public static void buildGateFrame(ServerLevel skyLevel, SkyGateShape.SkyGateFootprint fp) {
        BlockState frame = Blocks.SMOOTH_STONE.defaultBlockState();

        // 左右两列竖框（含顶端与底端各多一格）。
        for (int y = fp.frameBottomY(); y <= fp.frameTopY(); y++) {
            setBlock(skyLevel, at(fp, fp.spanStart() - 1, y), frame);
            setBlock(skyLevel, at(fp, fp.spanEnd() + 1, y), frame);
        }
        // 上下两行横框（跨过两侧竖框）。
        for (int s = fp.spanStart() - 1; s <= fp.spanEnd() + 1; s++) {
            setBlock(skyLevel, at(fp, s, fp.frameBottomY()), frame);
            setBlock(skyLevel, at(fp, s, fp.frameTopY()), frame);
        }
    }

    /** 按足迹擦掉一扇门（门框与门方块都置为空气）。 */
    public static void eraseGate(ServerLevel skyLevel, SkyGateShape.SkyGateFootprint fp) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int s = fp.spanStart() - 1; s <= fp.spanEnd() + 1; s++) {
            for (int y = fp.frameBottomY(); y <= fp.frameTopY(); y++) {
                setBlock(skyLevel, at(fp, s, y), air);
            }
        }
    }

    /** 擦掉某个形状的门（门框与门方块都置为空气）。 */
    public static void eraseGate(ServerLevel skyLevel, SkyGateShape shape) {
        if (shape == null) {
            return;
        }
        eraseGate(skyLevel, shape.footprint(DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET));
    }

    /** 清空空岛可点燃区域内的**所有**门方块（不动门框）。 */
    public static int clearPortalBlocksInZone(ServerLevel skyLevel) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(DHConfig.ZONE_MIN.immutable(), DHConfig.ZONE_MAX.immutable())) {
            BlockPos immutable = pos.immutable();
            if (CustomPortalHelper.isInstanceOfCustomPortal(skyLevel.getBlockState(immutable))) {
                skyLevel.getChunkAt(immutable);
                skyLevel.setBlock(immutable, air, PORTAL_FLAG);
                removed++;
            }
        }
        return removed;
    }

    /**
     * 测量「玩家进来那一扇门」的形状，用于在空岛复刻。
     *
     * <p>直接借用 CPA 的框架检测器：它本来就实现了「沿轴与高度找出内腔尺寸」的逻辑，
     * 复用它比自己再写一遍扫描要可靠（尺寸上限、朝向翻转的处理都已经被验证过）。</p>
     *
     * @param sourceLevel  来源维度（玩家进门时所在的维度）
     * @param inPortalPos  玩家进门时那一刻所站的门方块坐标
     * @return 测量到的形状；测不出来时返回 null（调用方退回默认形状）
     */
    @Nullable
    public static SkyGateShape measureGateShape(LevelAccessor sourceLevel, @Nullable BlockPos inPortalPos) {
        if (!(sourceLevel instanceof Level level) || inPortalPos == null) {
            return null;
        }
        try {
            BlockState state = level.getBlockState(inPortalPos);
            if (!CustomPortalHelper.isInstanceOfCustomPortal(state)) {
                return null;
            }
            Direction.Axis axis = CustomPortalHelper.getAxisFrom(state);
            Block frameBlock = CustomPortalHelper.getPortalBase(level, inPortalPos);
            if (frameBlock == null || frameBlock == Blocks.AIR) {
                return null;
            }
            PortalFrameTester.PortalFrameTesterFactory factory =
                    CustomPortalApiRegistry.getPortalFrameTester(DHConfig.FRAME_TESTER_ID);
            if (factory == null) {
                return null;
            }
            PortalFrameTester tester = factory.createInstanceOfPortalFrameTester()
                    .init(level, inPortalPos, axis, frameBlock);
            if (!tester.isValidFrame()) {
                return null;
            }
            BlockUtil.FoundRectangle rect = tester.getRectangle();
            Direction.Axis span = tester.getAxis1();
            if (span != Direction.Axis.X && span != Direction.Axis.Z) {
                span = axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X;
            }
            return new SkyGateShape(span, rect.axis1Size, rect.axis2Size);
        } catch (RuntimeException e) {
            // 测量失败不应影响传送本身：退回默认形状即可。
            return null;
        }
    }

    /** 把（展开轴坐标，高度）翻译到门的锚点平面上。 */
    private static BlockPos at(SkyGateShape.SkyGateFootprint fp, int spanCoord, int y) {
        return SkyGateGeometry.at(fp.span(), spanCoord, y, fp.plane());
    }

    /** 安全放置方块：先确保区块已加载，避免在未加载区块上写方块被静默丢弃。 */
    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.getChunkAt(pos);
        level.setBlock(pos, state, BLOCK_FLAG);
    }

    /** 供自检与日志使用的门方块状态。 */
    public static BlockState portalStateFor(Direction.Axis span) {
        return CustomPortalHelper.blockWithAxis(CustomPortalsMod.getDefaultPortalBlock().defaultBlockState(), span);
    }

    /** 判断某个方块状态是不是 CPA 的自定义门方块。 */
    public static boolean isPortalBlock(BlockState state) {
        return state.getBlock() instanceof CustomPortalBlock;
    }
}
