package com.dimensionhome.world;

import com.dimensionhome.DHConfig;
import net.kyrptonaught.customportalapi.portal.frame.PortalFrameTester;
import net.kyrptonaught.customportalapi.portal.frame.VanillaPortalAreaHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * 传送门框架检测器：**主世界 / 下界 / 末地照常点燃；空岛里只有在「可点燃区域」内才能点燃，
 * 且区域内同时只允许存在一扇门**。
 *
 * <h3>为什么必须在这里拦</h3>
 * <p>打火石点火走的是 CPA 自己的 Mixin（{@code AbstractFireMixin} 注入
 * {@code BaseFireBlock.onPlace}），Forge 的 {@code PlayerInteractEvent} 取消对它无效；
 * 而 CPA 的 {@code PortalLink.canLightInDim} 语义上无法排除维度自身
 * （它恒为 {@code dim.equals(returnDimID) || dim.equals(dimID)}）。</p>
 *
 * <p>CPA 的点燃判定链是
 * {@code PortalPlacer.attemptPortalLight} → {@code createPortal} →
 * {@code getFrameTester().createInstanceOfPortalFrameTester().getNewPortal(...)}，
 * 只有返回值非空才会真正建门，且 {@code createPortal} 内部**没有**二次校验。
 * 因此这里是唯一可靠的拦截点。</p>
 *
 * <h3>放行规则（仅空岛维度）</h3>
 * <ol>
 *   <li>门的整体（含外框）必须落在可点燃区域内 —— 否则拒绝，玩家没法在虚空里到处点门；</li>
 *   <li>区域内当时不能已经存在别的门方块 —— 否则拒绝，这就是「区域内只能有一扇门」。</li>
 * </ol>
 *
 * <h3>为什么这样就修好了「拆门被困」</h3>
 * <p>门方块本身硬度为 -1（生存拆不动），但**平滑石门框是可以拆的**。玩家拆掉框上任意一格后，
 * {@code CustomPortalBlock.updateShape} 会因为框架不再完整而把整扇门的门方块连锁清空。
 * 此时区域里不再有任何门方块，第 2 条规则自然放行，玩家就能在区域内任意位置、
 * 任意形状重新点燃一扇门 —— 这正是用户要求的「区域内可随时点燃」。</p>
 *
 * <p>只覆写 {@code getNewPortal} 是经过确认的：CPA 只在
 * {@code PortalPlacer.createPortal} 里调用它来决定「要不要建门」；
 * 而 {@code CustomPortalBlock.updateShape} 走的是 {@code init} + {@code isAlreadyLitPortalFrame}，
 * 完全不经过本方法，所以既有门的存活判定不受影响（否则已点亮的门会自己塌掉）。</p>
 */
public class SkyGuardPortalFrameTester extends VanillaPortalAreaHelper {

    @Override
    public Optional<PortalFrameTester> getNewPortal(LevelAccessor worldAccess, BlockPos blockPos, Direction.Axis axis,
                                                    Block... foundations) {
        Optional<PortalFrameTester> found = super.getNewPortal(worldAccess, blockPos, axis, foundations);
        if (found.isEmpty() || !isSkyLevel(worldAccess)) {
            // 其它维度一律照常：主世界 / 下界 / 末地点燃平滑石门不受影响。
            return found;
        }
        return isAllowedInSky(worldAccess, found.get()) ? found : Optional.empty();
    }

    /**
     * 空岛内的放行判定。
     *
     * <p>判定顺序很重要：先看形状是否落在区域内，再看区域里是否已有别的门。
     * 点燃的瞬间内腔还是空气（CPA 会在本方法返回之后才铺门方块），
     * 所以新门自己不会把自己挡住。</p>
     */
    private static boolean isAllowedInSky(LevelAccessor worldAccess, PortalFrameTester frame) {
        if (!SkyGateGeometry.rectangleFitsZone(frame)) {
            return false;
        }
        // 区域内已有门方块 → 拒绝，保证区域内只有一扇门。
        return !SkyGateGeometry.anyPortalInZone(worldAccess);
    }

    /** 判断当前世界是否是《维度之家》的空岛维度。 */
    private static boolean isSkyLevel(LevelAccessor worldAccess) {
        if (worldAccess instanceof Level level) {
            return level.dimension().equals(DHConfig.SKY_LEVEL);
        }
        return false;
    }
}
