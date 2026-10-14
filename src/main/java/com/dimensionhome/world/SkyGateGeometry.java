package com.dimensionhome.world;

import com.dimensionhome.DHConfig;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.kyrptonaught.customportalapi.portal.frame.PortalFrameTester;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 传送门几何换算与「可点燃区域」判定工具。
 *
 * <p>一扇传送门由两个水平方向决定：</p>
 * <ul>
 *   <li><b>展开轴（span）</b>：门框横向延伸的方向，只能是 X 或 Z；</li>
 *   <li><b>平面（plane）</b>：门的法线方向，也就是另一个水平轴上的一个固定坐标。</li>
 * </ul>
 *
 * <p>把这两个方向抽象出来之后，门的建造、校验、以及「是否落在可点燃区域内」的判定
 * 都只需要一套代码，不必为 X 向门和 Z 向门各写一遍。</p>
 */
public final class SkyGateGeometry {

    private SkyGateGeometry() {
    }

    /** 把「展开轴坐标 + 高度 + 平面坐标」翻译成世界坐标。 */
    public static BlockPos at(Direction.Axis span, int spanCoord, int y, int planeCoord) {
        return span == Direction.Axis.X
                ? new BlockPos(spanCoord, y, planeCoord)
                : new BlockPos(planeCoord, y, spanCoord);
    }

    /** 取出某个坐标沿「展开轴」的分量。 */
    public static int spanCoord(Direction.Axis span, BlockPos pos) {
        return span == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    /** 取出某个坐标沿「平面轴」的分量。 */
    public static int planeCoord(Direction.Axis span, BlockPos pos) {
        return span == Direction.Axis.X ? pos.getZ() : pos.getX();
    }

    /**
     * 判断一扇被检测到的门（含外框）是否完整落在可点燃区域内。
     *
     * <p>门矩形给的是<b>内腔</b>范围：内腔宽 {@code rect.axis1Size}、高 {@code rect.axis2Size}，
     * 左下角在 {@code rect.minCorner}。外框比内腔水平方向各多一格、上下各多一格，
     * 所以校验范围是「内腔范围向外扩一圈」。</p>
     */
    public static boolean rectangleFitsZone(PortalFrameTester frame) {
        if (frame == null) {
            return false;
        }
        Direction.Axis span = frame.getAxis1();
        if (span != Direction.Axis.X && span != Direction.Axis.Z) {
            return false;
        }
        BlockUtil.FoundRectangle rect = frame.getRectangle();
        if (rect == null || rect.minCorner == null) {
            return false;
        }
        int spanStart = spanCoord(span, rect.minCorner);
        int plane = planeCoord(span, rect.minCorner);
        int yStart = rect.minCorner.getY();

        // 外框：展开轴方向比内腔各多一格，高度方向上下各多一格。
        for (int s = spanStart - 1; s <= spanStart + rect.axis1Size; s++) {
            for (int y = yStart - 1; y <= yStart + rect.axis2Size; y++) {
                if (!DHConfig.isInsideZone(at(span, s, y, plane))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 可点燃区域内是否<b>已经存在</b>传送门方块。
     *
     * <p>这就是「区域内只能有一扇门」这条规则的判定依据：只要区域里还有门方块，
     * 新的点燃一律拒绝；玩家把旧门拆干净（门方块会被
     * {@code CustomPortalBlock.updateShape} 连锁清空）之后，区域里就再没有门方块，
     * 于是可以在区域内任意位置、任意形状重新点燃。</p>
     *
     * <p>扫描按区块进行：空岛是纯虚空，跨区块的读取很便宜，
     * 而按区块读取能保证不遗漏「尚未加载但确实存在过门」的角落。</p>
     */
    public static boolean anyPortalInZone(LevelAccessor worldAccess) {
        if (!(worldAccess instanceof Level level)) {
            return false;
        }
        BlockPos min = DHConfig.ZONE_MIN;
        BlockPos max = DHConfig.ZONE_MAX;

        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                LevelChunk chunk = resolveChunk(level, cx, cz);
                if (chunk == null) {
                    continue;
                }
                int x0 = Math.max(min.getX(), cx << 4);
                int x1 = Math.min(max.getX(), (cx << 4) + 15);
                int z0 = Math.max(min.getZ(), cz << 4);
                int z1 = Math.min(max.getZ(), (cz << 4) + 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = min.getY(); y <= max.getY(); y++) {
                            if (CustomPortalHelper.isInstanceOfCustomPortal(
                                    chunk.getBlockState(new BlockPos(x, y, z)))) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 取区块：服务端允许按需加载（虚空维度生成一个区块几乎无成本），
     * 客户端只读已存在的区块，绝不触发加载。
     */
    private static LevelChunk resolveChunk(Level level, int cx, int cz) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunk(cx, cz);
        }
        ChunkAccess access = level.getChunk(cx, cz, ChunkStatus.FULL, false);
        return access instanceof LevelChunk levelChunk ? levelChunk : null;
    }
}
