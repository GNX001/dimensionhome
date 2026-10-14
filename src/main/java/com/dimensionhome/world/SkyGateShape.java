package com.dimensionhome.world;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 一扇传送门的「形状」——只描述尺寸与朝向，不含世界坐标。
 *
 * <p>之所以要把形状单独抽出来，是因为玩家第一次从主世界进入空岛时，
 * 我们要按他那扇门的形状在空岛里复刻一扇一模一样的门；
 * 复刻的是<b>形状</b>（多宽、多高、朝哪个方向），位置则固定在平台前的锚点上。</p>
 *
 * <p>取值合法范围与 CPA 的原版框架检测器一致：内腔宽 2..21、高 3..21。</p>
 */
public record SkyGateShape(Direction.Axis span, int width, int height) {

    /** 默认形状：X 向、内腔 2 宽 x 3 高。 */
    public static final SkyGateShape DEFAULT = new SkyGateShape(Direction.Axis.X, 2, 3);

    /** CPA 允许的最小 / 最大内腔尺寸。 */
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    public SkyGateShape {
        if (span != Direction.Axis.X && span != Direction.Axis.Z) {
            // 传送门只能是竖直的，展开轴必须落在两个水平轴之一。
            span = Direction.Axis.X;
        }
        width = clamp(width, MIN_WIDTH, MAX_WIDTH);
        height = clamp(height, MIN_HEIGHT, MAX_HEIGHT);
    }

    /** 从 NBT 读回形状；字段缺失或非法时退回 {@link #DEFAULT}。 */
    public static SkyGateShape load(CompoundTag tag) {
        if (!tag.contains("GateSpan") || !tag.contains("GateWidth") || !tag.contains("GateHeight")) {
            return DEFAULT;
        }
        Direction.Axis span = "z".equals(tag.getString("GateSpan")) ? Direction.Axis.Z : Direction.Axis.X;
        return new SkyGateShape(span, tag.getInt("GateWidth"), tag.getInt("GateHeight"));
    }

    /** 写入 NBT。 */
    public CompoundTag save(CompoundTag tag) {
        tag.putString("GateSpan", span == Direction.Axis.Z ? "z" : "x");
        tag.putInt("GateWidth", width);
        tag.putInt("GateHeight", height);
        return tag;
    }

    /** 内腔沿展开轴的第一格坐标（让门在平台前方水平居中）。 */
    public int spanStart() {
        return -((width - 1) / 2);
    }

    /** 把形状翻译成一段子类可用的门足迹描述。 */
    public SkyGateFootprint footprint(int interiorY, int plane) {
        return new SkyGateFootprint(span, spanStart(), interiorY, plane, width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "轴向=" + (span == Direction.Axis.Z ? "Z" : "X") + "，内腔 " + width + "x" + height;
    }

    /**
     * 门在世界里占据的精确范围：内腔左下角 + 宽高 + 平面坐标。
     *
     * <p>建造、擦除、校验都用它，保证三者永远对同一块区域操作。</p>
     */
    public record SkyGateFootprint(Direction.Axis span, int spanStart, int interiorY, int plane, int width,
                                   int height) {

        /** 内腔沿展开轴的最后一格（含）。 */
        public int spanEnd() {
            return spanStart + width - 1;
        }

        /** 外框底行所在高度。 */
        public int frameBottomY() {
            return interiorY - 1;
        }

        /** 外框顶行所在高度。 */
        public int frameTopY() {
            return interiorY + height;
        }

        /** 转成通用的形状（丢弃位置信息）。 */
        public SkyGateShape shape() {
            return new SkyGateShape(span, width, height);
        }

        // --------------------------------------------------------------
        // 落脚平台：不再是写死的 3x3，而是由这扇门的形状推导出来
        // --------------------------------------------------------------

        /**
         * 落脚平台沿展开轴的起始坐标（含）。
         *
         * <p>「和门等宽」按整扇门（含左右两列门框）算：内腔是
         * {@code spanStart..spanEnd}，外框是 {@code spanStart-1..spanEnd+1}。
         * 取外框宽度还有个好处 —— 门的底框那一行正好铺在平台同层（y=99），
         * 平台与门框同宽时它就被完整嵌在平台里，不会在两侧留下悬空的半截门框。</p>
         */
        public int platformSpanMin() {
            return spanStart - 1;
        }

        /** 落脚平台沿展开轴的结束坐标（含），见 {@link #platformSpanMin()}。 */
        public int platformSpanMax() {
            return spanEnd() + 1;
        }

        /** 落脚平台沿门法线方向的起始坐标（含）：从门平面向一侧延伸。 */
        public int platformPlaneMin(int extend) {
            return plane - extend;
        }

        /** 落脚平台沿门法线方向的结束坐标（含）：从门平面向另一侧延伸。 */
        public int platformPlaneMax(int extend) {
            return plane + extend;
        }

        /**
         * 玩家抵达空岛时的落点：站在门前那段平台的正中央。
         *
         * <p>沿展开轴取平台宽度的几何正中；沿法线方向取「门前那 {@code extend} 格」的中点，
         * <b>刻意避开门平面本身</b> —— 落点若落在 {@code plane} 上就正好生成在门方块里，
         * 会被立刻再传送一次。</p>
         *
         * <p>默认形状（内腔 2x3、门平面 2、extend=2）下算出来正是
         * {@code (0.5, 100.0, 0.5)}，与改造前的固定落点完全一致。</p>
         *
         * @param extend 平台从门平面向前后各延伸的格数
         */
        public Vec3 arrivalPos(int extend) {
            double spanCoord = (platformSpanMin() + platformSpanMax()) / 2.0D;
            double planeCoord = plane - (extend + 1) / 2.0D;

            // 展开轴是 Z 时，展开坐标进 z、法线坐标进 x，反之亦然。
            return span == Direction.Axis.Z
                    ? new Vec3(planeCoord, interiorY, spanCoord)
                    : new Vec3(spanCoord, interiorY, planeCoord);
        }

        @Nullable
        public static SkyGateFootprint of(SkyGateShape shape, int interiorY, int plane) {
            return shape == null ? null : shape.footprint(interiorY, plane);
        }
    }
}
