package com.dimensionhome.capability;

import com.dimensionhome.DHConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * 玩家/实体身上的「来源记录」能力。
 *
 * <p>它保存两样东西：</p>
 * <ol>
 *   <li><b>来源维度 + 来源位置</b>：玩家从哪个维度、哪个位置进入空岛的。
 *       返回时用它会到「原维度、原入口附近」，从而支持主世界 / 下界 / 末地
 *       各自回到各自的门（CPA 自带的 {@code returnDimID} 只能写死一个维度，
 *       表达不了这种情况，所以必须自己记录）。</li>
 *   <li><b>最后安全位置</b>：玩家最后一次「没有站在传送门方块里」的位置。
 *       进门的瞬间玩家必定在门方块内部，如果直接记录那一刻的坐标，
 *       返回时就会落在门里而立刻被再次传送（死循环）。
 *       因此进门时改用此前缓存的安全位置，从根本上避免循环。</li>
 * </ol>
 *
 * <p>该能力挂在 {@code Entity} 上（玩家是 Entity 的子类），
 * 通过 {@link ICapabilitySerializable} 自动随实体 NBT 存取。</p>
 */
public class PortalOriginCapability implements ICapabilitySerializable<CompoundTag> {

    /** 能力标识，用于 AttachCapabilitiesEvent。 */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(DHConfig.MOD_ID, "portal_origin");

    /** 能力令牌，1.20.1 通过 CapabilityManager 取得。 */
    public static final Capability<PortalOriginCapability> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private static final String KEY_ORIGIN_DIM = "OriginDimension";
    private static final String KEY_ORIGIN_X = "OriginX";
    private static final String KEY_ORIGIN_Y = "OriginY";
    private static final String KEY_ORIGIN_Z = "OriginZ";
    private static final String KEY_SAFE_DIM = "SafeDimension";
    private static final String KEY_SAFE_X = "SafeX";
    private static final String KEY_SAFE_Y = "SafeY";
    private static final String KEY_SAFE_Z = "SafeZ";

    /** 来源维度；null 表示还没有进过空岛。 */
    @Nullable
    private ResourceKey<Level> originDimension;
    /** 来源位置（进门之前所站的位置）；null 表示没有记录。 */
    @Nullable
    private BlockPos originPos;

    /** 最后一次「不在传送门方块内」的维度。 */
    @Nullable
    private ResourceKey<Level> lastSafeDimension;
    /** 最后一次「不在传送门方块内」的位置。 */
    @Nullable
    private BlockPos lastSafePos;

    private final LazyOptional<PortalOriginCapability> holder = LazyOptional.of(() -> this);

    // ------------------------------------------------------------------
    // 来源记录
    // ------------------------------------------------------------------

    /** 记录来源（玩家即将从 originDimension 的 originPos 进入空岛）。 */
    public void setOrigin(ResourceKey<Level> originDimension, BlockPos originPos) {
        this.originDimension = originDimension;
        this.originPos = originPos.immutable();
    }

    /** 是否已有可用的来源记录。 */
    public boolean hasOrigin() {
        return this.originDimension != null && this.originPos != null;
    }

    @Nullable
    public ResourceKey<Level> getOriginDimension() {
        return this.originDimension;
    }

    @Nullable
    public BlockPos getOriginPos() {
        return this.originPos;
    }

    /** 清除来源记录（例如记录指向的维度已不存在）。 */
    public void clearOrigin() {
        this.originDimension = null;
        this.originPos = null;
    }

    // ------------------------------------------------------------------
    // 最后安全位置
    // ------------------------------------------------------------------

    /** 更新「最后安全位置」。 */
    public void setLastSafe(ResourceKey<Level> dimension, BlockPos pos) {
        this.lastSafeDimension = dimension;
        this.lastSafePos = pos.immutable();
    }

    /** 是否已有可用的安全位置。 */
    public boolean hasLastSafe() {
        return this.lastSafeDimension != null && this.lastSafePos != null;
    }

    @Nullable
    public ResourceKey<Level> getLastSafeDimension() {
        return this.lastSafeDimension;
    }

    @Nullable
    public BlockPos getLastSafePos() {
        return this.lastSafePos;
    }

    /** 把另一个能力实例的数据复制过来（用于死亡重生时保留记录）。 */
    public void copyFrom(PortalOriginCapability other) {
        this.originDimension = other.originDimension;
        this.originPos = other.originPos;
        this.lastSafeDimension = other.lastSafeDimension;
        this.lastSafePos = other.lastSafePos;
    }

    // ------------------------------------------------------------------
    // ICapabilityProvider / ICapabilitySerializable
    // ------------------------------------------------------------------

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == CAPABILITY ? this.holder.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (this.originDimension != null && this.originPos != null) {
            tag.putString(KEY_ORIGIN_DIM, this.originDimension.location().toString());
            tag.putInt(KEY_ORIGIN_X, this.originPos.getX());
            tag.putInt(KEY_ORIGIN_Y, this.originPos.getY());
            tag.putInt(KEY_ORIGIN_Z, this.originPos.getZ());
        }
        if (this.lastSafeDimension != null && this.lastSafePos != null) {
            tag.putString(KEY_SAFE_DIM, this.lastSafeDimension.location().toString());
            tag.putInt(KEY_SAFE_X, this.lastSafePos.getX());
            tag.putInt(KEY_SAFE_Y, this.lastSafePos.getY());
            tag.putInt(KEY_SAFE_Z, this.lastSafePos.getZ());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains(KEY_ORIGIN_DIM)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_ORIGIN_DIM));
            if (id != null) {
                this.originDimension = ResourceKey.create(Registries.DIMENSION, id);
                this.originPos = new BlockPos(tag.getInt(KEY_ORIGIN_X), tag.getInt(KEY_ORIGIN_Y), tag.getInt(KEY_ORIGIN_Z));
            }
        }
        if (tag.contains(KEY_SAFE_DIM)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_SAFE_DIM));
            if (id != null) {
                this.lastSafeDimension = ResourceKey.create(Registries.DIMENSION, id);
                this.lastSafePos = new BlockPos(tag.getInt(KEY_SAFE_X), tag.getInt(KEY_SAFE_Y), tag.getInt(KEY_SAFE_Z));
            }
        }
    }
}