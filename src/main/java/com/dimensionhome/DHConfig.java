package com.dimensionhome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * 《维度之家》的全局常量（唯一事实来源）。
 *
 * <p>所有维度 ID、数据包 JSON 路径、空岛地标与「可点燃区域」的精确坐标都集中在这里，
 * 代码与数据包必须保持一致，改一处要同步改另一处。</p>
 *
 * <p>本 MOD 最终确定的需求（优先于 docs/开发文档.txt，文档中冲突处已作废）：
 * <ul>
 *   <li>坐标缩放 1:1（coordinateScale = 1.0，文档写的 8.0 作废）；</li>
 *   <li>维度内昼夜交替（不写 fixed_time）；</li>
 *   <li>不生成浮空岛/矿物/结构，纯虚空；</li>
 *   <li>空岛内只有一块落脚平台：与门（含门框）等宽，并以门平面为中心沿门法线前后各延伸
 *       {@link #PLATFORM_EXTEND} 格；</li>
 *   <li>主世界 / 下界 / 末地都能点燃平滑石门，返回各自来源维度与来源门；</li>
 *   <li>空岛维度内只有在「可点燃区域」内才能点火，且区域内同时只能存在一扇门；</li>
 *   <li>空岛的第一扇门按玩家首次进入时那扇来源门的形状复刻；</li>
 *   <li>玩家与其它实体（掉落物 / 怪物 / 动物）都能随传送门进出空岛。</li>
 * </ul></p>
 */
public final class DHConfig {

    private DHConfig() {
    }

    /** MOD ID，与 gradle.properties 的 mod_id 一致。 */
    public static final String MOD_ID = "dimensionhome";

    // ------------------------------------------------------------------
    // 维度标识
    // ------------------------------------------------------------------

    /** 空岛维度的 Level 键，对应数据包 data/dimensionhome/dimension/sky_dimension.json。 */
    public static final ResourceKey<Level> SKY_LEVEL = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MOD_ID, "sky_dimension"));

    /** 空岛维度类型键，对应数据包 data/dimensionhome/dimension_type/sky_type.json。 */
    public static final ResourceKey<DimensionType> SKY_DIMENSION_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "sky_type"));

    /** 空岛的 LevelStem 键（与 Level 键同名，1.20.1 中 LEVEL_STEM 与 DIMENSION 共用 "dimension" 路径）。 */
    public static final ResourceKey<LevelStem> SKY_LEVEL_STEM = ResourceKey.create(
            Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "sky_dimension"));

    // ------------------------------------------------------------------
    // 空岛地标（与门等宽的落脚平台 + 唯一的返回门）
    // ------------------------------------------------------------------

    /** 落脚平台所在的 Y 层：平台方块在 y=99，玩家站在 y=100。 */
    public static final int PLATFORM_Y = 99;

    /**
     * 落脚平台从门平面向前后各延伸的格数。
     *
     * <p>平台不再是写死的 3x3，而是随那扇门的形状变化：沿门的展开轴（宽度方向）
     * 与门（含两侧门框）等宽，沿门的法线方向（前后方向）从门平面各延伸
     * {@code PLATFORM_EXTEND} 格。</p>
     *
     * <p>取 2 格的依据：门框底部那一行本身就在 y=99（与平台同层）并横跨整个门宽，
     * 所以玩家跨出门框后立刻就有平台承接，前后各 2 格足够走到平台边缘而不至于
     * 「一出门就踩空」。</p>
     */
    public static final int PLATFORM_EXTEND = 2;

    /** 传送门内腔最下面一层的 Y（内腔从这一层开始向上长 height 格）。 */
    public static final int PORTAL_INTERIOR_Y = 100;

    /**
     * 传送门所在的平面坐标（门平面法线方向的那个坐标）。
     *
     * <p>门沿某个水平轴展开（AXIS = X 或 Z），门平面固定在「另一个水平轴 = 2」：
     * 轴向为 X 时门平面是 z=2；轴向为 Z 时门平面是 x=2。</p>
     */
    public static final int PORTAL_PLANE_OFFSET = 2;

    // ------------------------------------------------------------------
    // 可点燃区域（空岛内唯一允许点火的范围）
    // ------------------------------------------------------------------

    /**
     * 可点燃区域的水平半径：以平台中心 (0, y, 0) 为原点，x、z 各在 [-12, 12] 内。
     *
     * <p>这个半径必须能容纳「最大尺寸的传送门」（内腔 21x21，外框 23x23）：
     * 内腔沿轴向最宽时坐标范围是 [-10, 10]，外框到 [-11, 11]，都落在 [-12, 12] 内。</p>
     */
    public static final int ZONE_RADIUS = 12;

    /** 可点燃区域的最低 Y（与平台同层，门的底框就铺在这一层）。 */
    public static final int ZONE_MIN_Y = PLATFORM_Y;

    /** 可点燃区域的最高 Y（最高 21 格内腔时外框顶行在 y=121，留一格余量）。 */
    public static final int ZONE_MAX_Y = 122;

    /** 可点燃区域的最小角（含）。 */
    public static final BlockPos ZONE_MIN = new BlockPos(-ZONE_RADIUS, ZONE_MIN_Y, -ZONE_RADIUS);

    /** 可点燃区域的最大角（含）。 */
    public static final BlockPos ZONE_MAX = new BlockPos(ZONE_RADIUS, ZONE_MAX_Y, ZONE_RADIUS);

    /**
     * 判断某个方块坐标是否落在空岛的「可点燃区域」内。
     *
     * <p>这个区域与门的朝向无关（是世界的立方体），因此玩家在区域内换成另一个朝向重建门也照样允许。</p>
     */
    public static boolean isInsideZone(BlockPos pos) {
        return pos.getX() >= ZONE_MIN.getX() && pos.getX() <= ZONE_MAX.getX()
                && pos.getY() >= ZONE_MIN.getY() && pos.getY() <= ZONE_MAX.getY()
                && pos.getZ() >= ZONE_MIN.getZ() && pos.getZ() <= ZONE_MAX.getZ();
    }

    // ------------------------------------------------------------------
    // 传送门框架方块
    // ------------------------------------------------------------------

    /** 记分用的方块 ID 字符串（仅用于日志与提示）。 */
    public static final String FRAME_BLOCK_ID = "minecraft:smooth_stone";

    /**
     * 传送门玻璃的染色（金色 #FFD700）。
     *
     * <p>CPA 会把这个值注册成门方块的 block color（模型里写着 {@code tintindex: 4}），
     * 因此它同时决定三处观感：门外那层传送门方块本身、进出时的屏幕紫色蒙版、以及门的粒子。</p>
     *
     * <p>注意 CPA 的染色是**乘法**：门贴图本身是一张接近白色的灰阶图（平均 RGB 201），
     * 所以最终成色约等于这里的值乘以 0.79。取纯金 (255,215,0) 渲染出来是明确的琥珀金色。</p>
     */
    public static final int PORTAL_TINT_R = 255;
    public static final int PORTAL_TINT_G = 215;
    public static final int PORTAL_TINT_B = 0;

    /**
     * 自定义框架检测器的注册 ID。
     *
     * <p>它的职责是「按可点燃区域放行 / 拒绝点火」，因此名字用 zone（区域）而不是 blocked（禁止）：
     * 空岛内不再是「一律禁止」，而是「区域内允许、区域外禁止、区域内只允许一扇」。</p>
     */
    public static final ResourceLocation FRAME_TESTER_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "sky_zone");
}
