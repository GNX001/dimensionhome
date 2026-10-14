package com.dimensionhome.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 空岛传送门的存档数据：记住「这扇门是什么形状的」以及「是否已经按玩家的来源门复刻过」。
 *
 * <p>为什么要存盘：复刻形状这件事只能发生一次 —— 玩家第一次进入空岛时那一刻的来源门形状，
 * 决定了空岛里这扇门的形状。之后无论重启多少次服务器，都应该保持同一个形状，
 * 而不是每次重启都按最后一次进门的样子重做。</p>
 */
public class SkyGateData extends SavedData {

    /** 存档里使用的名字。 */
    public static final String DATA_NAME = "dimensionhome_gate";

    private SkyGateShape shape = SkyGateShape.DEFAULT;
    private boolean replicated = false;

    public SkyGateData() {
    }

    /** 从 NBT 还原。 */
    public static SkyGateData load(CompoundTag tag) {
        SkyGateData data = new SkyGateData();
        data.shape = SkyGateShape.load(tag);
        data.replicated = tag.getBoolean("GateReplicated");
        return data;
    }

    /** 从空岛维度取出（不存在则创建）这份存档数据。 */
    public static SkyGateData get(ServerLevel skyLevel) {
        return skyLevel.getDataStorage().computeIfAbsent(SkyGateData::load, SkyGateData::new, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        shape.save(tag);
        tag.putBoolean("GateReplicated", replicated);
        return tag;
    }

    /** 当前记录的门形状（还没复刻过时是默认形状）。 */
    public SkyGateShape getShape() {
        return shape;
    }

    /** 是否已经按玩家第一次进门的形状复刻过。 */
    public boolean isReplicated() {
        return replicated;
    }

    /**
     * 记录「按玩家来源门复刻」的形状，只在第一次调用时生效。
     *
     * @return true 表示这次调用真的改动了形状（= 这是第一次复刻）
     */
    public boolean replicateOnce(SkyGateShape measured) {
        if (replicated || measured == null) {
            return false;
        }
        this.shape = measured;
        this.replicated = true;
        setDirty();
        return true;
    }

    /** 记下当前实际建造的形状。 */
    public void setShape(SkyGateShape newShape) {
        if (newShape != null) {
            this.shape = newShape;
            setDirty();
        }
    }

    /** 人类可读的描述，供日志与自检使用。 */
    public String describe() {
        return shape + (replicated ? "（已按来源门复刻）" : "（默认形状）");
    }
}
