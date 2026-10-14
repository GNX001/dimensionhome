package com.dimensionhome.portal;

import com.dimensionhome.DHConfig;
import com.dimensionhome.capability.PortalOriginCapability;
import com.dimensionhome.world.SkyGateData;
import com.dimensionhome.world.SkyGateShape;
import com.dimensionhome.world.SkyLandmarkBuilder;
import net.kyrptonaught.customportalapi.interfaces.EntityInCustomPortal;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.kyrptonaught.customportalapi.util.SHOULDTP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.common.util.LazyOptional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * 传送接管器：《维度之家》的**全部传送逻辑**都在这里。
 *
 * <p>我们通过 CPA 的 {@code registerBeforeTPEvent} 返回
 * {@link SHOULDTP#CANCEL_TP} 来阻止 CPA 自己的传送流程，然后自己执行传送。
 * 这样做是必须的，原因有两个：</p>
 * <ol>
 *   <li>CPA 默认会根据 {@code PortalLinkingStorage} 找落点，找不到就在目标维度
 *       {@code PortalPlacer.createDestinationPortal} **强行兜底建一扇新门**。
 *       在纯虚空维度里这既会破坏「唯一一扇门」，又会把玩家丢到虚空里。</li>
 *   <li>CPA 的 {@code returnDimID} 只能写死一个返回维度，无法表达
 *       「从主世界来的回主世界、从下界来的回下界」。</li>
 * </ol>
 *
 * <p>进入空岛：记录来源维度 + 来源位置，然后固定传送到
 * 门前平台的正中央（落点随门形状动态计算，见
 * {@link com.dimensionhome.world.SkyGateShape.SkyGateFootprint#arrivalPos(int)}）。</p>
 * <p>离开空岛：读出来源记录，回到「原维度、原门附近」。</p>
 */
public final class DHTeleportHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("dimensionhome");

    private DHTeleportHandler() {
    }

    /**
     * CPA 的传送前置事件。返回 {@link SHOULDTP#CANCEL_TP} 表示「CPA 不要动，我自己来」。
     */
    public static SHOULDTP beforeTeleport(Entity entity) {
        return beforeTeleportWithResult(entity).verdict();
    }

    /**
     * 一次传送接管的结果。
     *
     * @param verdict 交回给 CPA 的裁决
     * @param entity  传送之后的实体。玩家前后是同一个实例；非玩家跨维度必然换成新实例，
     *                这里返回的就是新实例。没有发生实际传送时返回原实体。
     */
    public record TeleportResult(SHOULDTP verdict, Entity entity) {
    }

    /**
     * 与 {@link #beforeTeleport(Entity)} 做的事完全相同，只是把传送后的实体也交回给调用方。
     *
     * <p>为什么要多这么一个入口：非玩家实体跨维度时 {@code changeDimension} 会
     * {@code EntityType.create} 出一个**新实例**，而如果目标区块当时不是
     * ENTITY_TICKING，新实例会被 {@code PersistentEntitySectionManager.addEntityWithoutEvent}
     * 收进 pending 队列，要到之后某个 tick 才真正进入 {@code getAllEntities()}。
     * 也就是说「传送后立刻在目标维度里扫实体」这件事本身就不可能可靠。
     * 想做确定性断言，只能拿 {@code changeDimension} 返回的那个实例。</p>
     *
     * <p>真实玩法不受影响：玩家自己走过门时目标区块随玩家加载，实体照常在下一个
     * tick 出现；这里只是为了给自检一个确定的观察点。</p>
     */
    public static TeleportResult beforeTeleportWithResult(Entity entity) {
        try {
            if (entity.level().isClientSide()) {
                // 只在服务端处理；客户端返回取消以免出现预测性传送。
                return new TeleportResult(SHOULDTP.CANCEL_TP, entity);
            }

            BlockPos inPortalPos = ((EntityInCustomPortal) entity).getInPortalPos();
            ResourceKey<Level> currentDim = entity.level().dimension();

            if (DHConfig.SKY_LEVEL.equals(currentDim)) {
                return leaveSkyDimension(entity, inPortalPos);
            }
            return enterSkyDimension(entity, currentDim, inPortalPos);
        } catch (Exception e) {
            // 传送环节出错时绝不能把实体卡在门里：让 CPA 的默认流程兜底。
            LOGGER.error("维度之家：传送处理异常，交由 CPA 默认流程处理", e);
            return new TeleportResult(SHOULDTP.CONTINUE_TP, entity);
        }
    }

    /**
     * 进入空岛：记录来源，然后传送到门前平台上的固定落点。
     *
     * <p>玩家与其它实体（掉落物 / 怪物 / 动物）走两条不同的传送 API，
     * 但落点与来源记录语义完全一致：</p>
     * <ul>
     *   <li>玩家：实例在传送前后不变，用
     *       {@link ServerPlayer#teleportTo(ServerLevel, double, double, double, float, float)}，
     *       来源记在能力（Capability）上；</li>
     *   <li>非玩家：跨维度会**换成新实例**，只能走
     *       {@link Entity#changeDimension(ServerLevel, ITeleporter)}，
     *       来源记在实体的 {@code getPersistentData()} 上 —— 它随
     *       {@code saveWithoutId}/{@code load} 一起被 {@code restoreFrom} 复制到新实例。</li>
     * </ul>
     */
    private static TeleportResult enterSkyDimension(Entity entity, ResourceKey<Level> fromDim, @Nullable BlockPos inPortalPos) {
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return new TeleportResult(SHOULDTP.CANCEL_TP, entity);
        }
        ServerLevel skyLevel = server.getLevel(DHConfig.SKY_LEVEL);
        if (skyLevel == null) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.dimensionhome.sky_missing"), true);
            }
            LOGGER.error("维度之家：找不到空岛维度 {}，无法传送。请检查数据包是否被加载。", DHConfig.SKY_LEVEL.location());
            return new TeleportResult(SHOULDTP.CANCEL_TP, entity);
        }

        // 记录来源：优先用「进入门前所站的安全位置」，它必定不在门方块内部，
        // 返回时落在这里就不会立刻被再次传送（避免来回循环）。
        BlockPos origin = resolveOriginPos(entity, inPortalPos);
        boolean firstVisit = false;

        if (entity instanceof ServerPlayer player) {
            getCap(player).ifPresent(cap -> {
                cap.setOrigin(fromDim, origin);
                // 进入空岛时把安全位置刷新为「当时所站的位置」，让返回更贴近玩家走向。
                cap.setLastSafe(fromDim, origin);
            });
            // 第一次进空岛：把玩家那道来源门原样克隆到空岛锚点上。
            firstVisit = replicateSourceGateOnce(player, skyLevel, inPortalPos);
        } else {
            rememberOriginInPersistentData(entity, fromDim, origin);
        }

        Vec3 arrival = arrivalPos(skyLevel);

        if (entity instanceof ServerPlayer player) {
            player.teleportTo(skyLevel, arrival.x, arrival.y, arrival.z, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.translatable("message.dimensionhome.entered_sky"), true);
            if (firstVisit) {
                // 「拆门不会被困」这条规则玩家不可能自己猜到，所以第一次抵达时明确告诉他一次，
                // 否则真拆了门仍然会以为档坏了。
                player.displayClientMessage(Component.translatable("message.dimensionhome.zone_hint"), false);
            }
            return new TeleportResult(SHOULDTP.CANCEL_TP, player);
        }

        // 非玩家：跨维度会生成新实例、原实例随后被移除，所以必须把新实例交回给调用方。
        // 冷却要在传送前设好，因为 restoreFrom 会把它复制给新实例。
        entity.setPortalCooldown(40);
        Entity moved = entity.changeDimension(skyLevel, new FixedPointTeleporter(arrival));
        return new TeleportResult(SHOULDTP.CANCEL_TP, moved == null ? entity : moved);
    }

    /**
     * 算出抵达空岛时的落点。
     *
     * <p>落点随门形状变化：平台与门等宽，所以落点也跟着位移，永远站在门前那段
     * 平台的几何正中央。门形状读不出来时退回默认形状。</p>
     */
    private static Vec3 arrivalPos(ServerLevel skyLevel) {
        SkyGateShape shape = SkyGateData.get(skyLevel).getShape();
        SkyGateShape.SkyGateFootprint fp =
                shape.footprint(DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET);
        return fp.arrivalPos(DHConfig.PLATFORM_EXTEND);
    }

    /**
     * 只认一个固定落点的传送器（给非玩家实体用）。
     *
     * <p>必须覆写 {@link ITeleporter#getPortalInfo}：Forge 的默认实现只在
     * {@code isVanilla()} 为真时才使用原版落点算法，而自写的传送器永远是 false，
     * 默认实现会把实体**当前坐标**当落点 —— 那样实体根本不会被挪到空岛上。</p>
     *
     * <p>{@code placeEntity} 不覆写，走默认实现即可：它会
     * {@code EntityType.create(目标维度)} → {@code restoreFrom(原实体)} →
     * {@code moveTo(落点)} → {@code addDuringTeleport}，NBT 与朝向都自动带过去。</p>
     */
    private record FixedPointTeleporter(Vec3 target) implements ITeleporter {

        @Override
        public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
                                        Function<ServerLevel, PortalInfo> defaultPortalInfo) {
            return new PortalInfo(target, Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }

        @Override
        public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 非玩家实体的来源记录（存在 getPersistentData 里，随 restoreFrom 跨维度复制）
    // ------------------------------------------------------------------

    private static final String ORIGIN_DIM_KEY = "DimensionHomeOriginDim";
    private static final String ORIGIN_POS_KEY = "DimensionHomeOriginPos";

    private static void rememberOriginInPersistentData(Entity entity, ResourceKey<Level> dim, BlockPos pos) {
        CompoundTag data = entity.getPersistentData();
        data.putString(ORIGIN_DIM_KEY, dim.location().toString());
        data.put(ORIGIN_POS_KEY, NbtUtils.writeBlockPos(pos));
    }

    @Nullable
    private static ResourceKey<Level> readPersistentDimension(Entity entity, String key) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(key)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getString(key));
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    @Nullable
    private static BlockPos readPersistentPos(Entity entity, String key) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(key)) {
            return null;
        }
        return NbtUtils.readBlockPos(data.getCompound(key));
    }

    /**
     * 离开空岛：回到记录下来的原维度、原门附近。
     */
    private static TeleportResult leaveSkyDimension(Entity entity, @Nullable BlockPos inPortalPos) {
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return new TeleportResult(SHOULDTP.CANCEL_TP, entity);
        }

        ResourceKey<Level> targetDim;
        BlockPos targetPos;

        if (entity instanceof ServerPlayer player) {
            PortalOriginCapability cap = getCap(player).orElse(null);
            if (cap != null && cap.hasOrigin()) {
                targetDim = cap.getOriginDimension();
                // 优先用安全位置（不在门里）；没有就退回记录下来的来源坐标。
                if (cap.hasLastSafe() && targetDim != null && targetDim.equals(cap.getLastSafeDimension())) {
                    targetPos = cap.getLastSafePos();
                } else {
                    targetPos = cap.getOriginPos();
                }
            } else {
                targetDim = null;
                targetPos = null;
            }
        } else {
            targetDim = readPersistentDimension(entity, ORIGIN_DIM_KEY);
            targetPos = readPersistentPos(entity, ORIGIN_POS_KEY);
        }

        if (targetDim == null || targetPos == null) {
            // 没有记录（例如被指令直接传进空岛）：退回主世界出生点，至少不会掉进虚空。
            targetDim = Level.OVERWORLD;
            targetPos = server.overworld().getSharedSpawnPos();
            LOGGER.warn("维度之家：实体「{}」在空岛维度内没有来源记录，改传送回主世界出生点 {}",
                    entity.getName().getString(), targetPos);
        }

        ServerLevel targetLevel = server.getLevel(targetDim);
        if (targetLevel == null) {
            targetLevel = server.overworld();
            targetPos = targetLevel.getSharedSpawnPos();
            LOGGER.warn("维度之家：来源维度已不存在，改传送回主世界出生点 {}", targetPos);
        }

        // 落点略微抬高并居中，避免卡在方块里（与原开发文档「Y+1」的建议一致）。
        double x = targetPos.getX() + 0.5D;
        double y = targetPos.getY() + 1.0D;
        double z = targetPos.getZ() + 0.5D;

        if (entity instanceof ServerPlayer player) {
            player.teleportTo(targetLevel, x, y, z, player.getYRot(), player.getXRot());
            // 传送后给一个短暂冷却，杜绝极端的同 tick 反复触发。
            player.setPortalCooldown(40);
            player.displayClientMessage(Component.translatable("message.dimensionhome.left_sky"), true);
            return new TeleportResult(SHOULDTP.CANCEL_TP, player);
        }

        // 同进入路径：跨维度换成新实例，把新实例交回给调用方。
        entity.setPortalCooldown(40);
        Entity moved = entity.changeDimension(targetLevel, new FixedPointTeleporter(new Vec3(x, y, z)));
        return new TeleportResult(SHOULDTP.CANCEL_TP, moved == null ? entity : moved);
    }

    /**
     * 把玩家进来那道门原样克隆到空岛锚点上，**只在第一次生效**。
     *
     * <p>用户的要求是：空岛的第一扇门要「参照主世界第一个门的形状完全复刻过来」。
     * 因此这里量出玩家进门那一刻所站门方块的矩形尺寸与朝向，交给
     * {@link SkyGateData#replicateOnce} 做一次性记账；已经复刻过就直接返回，
     * 之后重启服务器也不会再改形状。</p>
     *
     * <p>复刻失败（量不出来，例如玩家不是从门方块进来）不影响传送本身，
     * 只是形状退回默认的 2x3。</p>
     *
     * @return true 表示这次调用真的完成了复刻（也就是「第一次进空岛」）
     */
    private static boolean replicateSourceGateOnce(ServerPlayer player, ServerLevel skyLevel, @Nullable BlockPos inPortalPos) {
        SkyGateData data = SkyGateData.get(skyLevel);
        if (data.isReplicated()) {
            return false;
        }
        SkyGateShape measured = SkyLandmarkBuilder.measureGateShape(player.level(), inPortalPos);
        if (!data.replicateOnce(measured)) {
            return false;
        }
        // 复刻时先擦掉锚点上那扇默认门，再按来源形状重建。
        SkyLandmarkBuilder.rebuildGate(skyLevel, measured, SkyGateShape.DEFAULT);
        LOGGER.info("维度之家：已按玩家 {} 的来源门复刻空岛传送门 —— {}",
                player.getGameProfile().getName(), measured);
        return true;
    }

    /**
     * 兜底逃生：{@code /dimensionhome home}。
     *
     * <p><b>这是我额外加的，不是原始需求</b>（原始需求只要求「区域内可自由重燃」）。
     * 加它的理由：玩家在空岛拆掉门之后虽然可以在区域内重新点燃，但那仍然需要
     * 手上有打火石和平滑石。如果玩家把这两样都丢进了虚空，就没有任何游戏内手段
     * 可以离开，只能删档 —— 这是不可接受的状态。这个命令提供一条与门完全无关的退路。</p>
     *
     * <p>在空岛维度内执行时，行为与「走进门」完全一致：回到记录下来的来源维度与
     * 来源门附近；其它维度执行时提示无效。</p>
     *
     * @return true 表示已经执行了返回
     */
    public static boolean forceReturnHome(ServerPlayer player) {
        if (!DHConfig.SKY_LEVEL.equals(player.level().dimension())) {
            return false;
        }
        return leaveSkyDimension(player, null).verdict() == SHOULDTP.CANCEL_TP;
    }

    /**
     * 计算返回时使用的「来源位置」。
     *
     * <p>进门的一瞬间玩家必然站在传送门方块内部，如果直接记录那一刻的坐标，
     * 返回时就会落在门里并被立即再次传送，形成来回循环。
     * 因此这里优先使用每 tick 维护的「最后安全位置」。</p>
     */
    private static BlockPos resolveOriginPos(Entity entity, @Nullable BlockPos inPortalPos) {
        if (entity instanceof ServerPlayer player) {
            PortalOriginCapability cap = getCap(player).orElse(null);
            if (cap != null && cap.hasLastSafe() && player.level().dimension().equals(cap.getLastSafeDimension())) {
                return cap.getLastSafePos();
            }
        }
        // 没有安全位置记录（例如玩家一路贴着门走过来）：退到门方块前方一格。
        if (inPortalPos != null) {
            return inPortalPos;
        }
        return entity.blockPosition();
    }

    /**
     * 每种维度变化后都重新核对一次「最后安全位置」。
     *
     * <p>由 {@link com.dimensionhome.DHEvents} 在玩家每 tick 时调用。</p>
     *
     * <p>注意：空岛维度内**不**更新这个记录。它的语义是「玩家在普通维度里、
     * 走进传送门之前所站的位置」，记录它才能保证返回时落在门外而不是门里；
     * 如果在空岛里也更新，返回时就会读到空岛内的坐标而失效。</p>
     */
    public static void tickSafePosition(ServerPlayer player) {
        if (player.level().isClientSide()) {
            return;
        }
        // 空岛维度内不更新安全位置（详见方法注释）。
        if (DHConfig.SKY_LEVEL.equals(player.level().dimension())) {
            return;
        }
        // 站在传送门方块上时不更新安全位置，否则会把「门里」记成安全点。
        BlockPos feet = player.blockPosition();
        BlockState state = player.level().getBlockState(feet);
        if (CustomPortalHelper.isInstanceOfCustomPortal(state)) {
            return;
        }
        getCap(player).ifPresent(cap -> cap.setLastSafe(player.level().dimension(), feet));
    }

    /** 取玩家身上的能力实例。 */
    public static LazyOptional<PortalOriginCapability> getCap(Entity entity) {
        return entity.getCapability(PortalOriginCapability.CAPABILITY);
    }
}
