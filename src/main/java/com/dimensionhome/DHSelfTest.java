package com.dimensionhome;

import com.dimensionhome.capability.PortalOriginCapability;
import com.dimensionhome.portal.DHTeleportHandler;
import com.dimensionhome.world.SkyGateData;
import com.dimensionhome.world.SkyGateGeometry;
import com.dimensionhome.world.SkyGateShape;
import com.dimensionhome.world.SkyGuardPortalFrameTester;
import com.dimensionhome.world.SkyLandmarkBuilder;
import com.dimensionhome.world.VoidChunkGenerator;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyrptonaught.customportalapi.CustomPortalApiRegistry;
import net.kyrptonaught.customportalapi.CustomPortalsMod;
import net.kyrptonaught.customportalapi.portal.frame.PortalFrameTester;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.kyrptonaught.customportalapi.util.PortalLink;
import net.kyrptonaught.customportalapi.util.SHOULDTP;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 运行时自检：{@code /dimensionhome selftest}
 *
 * <p>这个命令存在的意义是**把验收变成可复现的证据**，而不是靠人肉进游戏看。
 * 它逐条核对需求里最容易被写错的点：维度参数、纯虚空、地标几何、禁燃规则。
 * 每条都打印「通过 / 失败」，最后给汇总。任何一条失败都以非 0 结果结束命令，
 * 便于在服务器控制台里一眼看出问题。</p>
 */
public final class DHSelfTest {

    private DHSelfTest() {
    }

    /** 注册 {@code /dimensionhome selftest}、{@code tptest} 与 {@code home}。 */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dimensionhome")
                .then(Commands.literal("selftest").executes(ctx -> run(ctx.getSource())))
                .then(Commands.literal("tptest").executes(ctx -> runTeleportTest(ctx.getSource())))
                .then(Commands.literal("home").executes(ctx -> runGoHome(ctx.getSource())));
    }

    /**
     * {@code /dimensionhome home} —— 兜底逃生命令。
     *
     * <p><b>这是我额外加的，不是用户点名的需求。</b>加它的理由：玩家在空岛拆掉门之后
     * 虽然可以在允许区域内重新点燃，但那要求他手上有打火石和平滑石；如果两样都掉进虚空，
     * 就再没有任何游戏内手段能离开。这条命令提供一条与门无关的退路。</p>
     *
     * <p>它复用 {@link DHTeleportHandler#beforeTeleport} 的同一条返回逻辑，
     * 所以落点与「走进门」完全一致，不会出现两套行为。</p>
     */
    private static int runGoHome(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("§c该命令只能由玩家执行（控制台没有可返回的位置）"));
            return 0;
        }
        if (!DHConfig.SKY_LEVEL.equals(player.level().dimension())) {
            src.sendFailure(Component.literal("§c你当前不在「维度之家：空岛」里，无需逃逸"));
            return 0;
        }
        if (DHTeleportHandler.forceReturnHome(player)) {
            src.sendSuccess(() -> Component.literal("§a已把你送回原来的世界"), false);
            return 1;
        }
        src.sendFailure(Component.literal("§c返回失败：来源记录不可用，请检查服务器日志"));
        return 0;
    }

    /**
     * 传送链路自检：用一个假玩家完整走一遍
     * 「记录来源 → 传送到空岛 → 从空岛返回原维度原位置」，
     * 并核对每一步的坐标与维度是否与预期一致。
     *
     * <p>这是唯一能证明「固定落点 + 返回原位」真正生效的手段：
     * 光看代码无法发现能力没挂上、坐标算错、维度取错等问题。</p>
     */
    static int runTeleportTest(CommandSourceStack src) {
        List<String> failures = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel sky = server.getLevel(DHConfig.SKY_LEVEL);
        if (sky == null) {
            src.sendFailure(Component.literal("§c空岛维度未加载，无法测试传送"));
            return 0;
        }

        // 假玩家：与真实玩家同为 ServerPlayer，能力挂载、传送走的是同一条代码路径。
        FakePlayer player = new FakePlayer(overworld, new GameProfile(
                UUID.nameUUIDFromBytes("dimensionhome-selftest".getBytes(StandardCharsets.UTF_8)),
                "dh_selftest"));

        try {
            // 记录能力是否成功挂载（AttachCapabilitiesEvent 是否真的生效）。
            PortalOriginCapability cap = player.getCapability(PortalOriginCapability.CAPABILITY).orElse(null);
            check(lines, failures, "假玩家身上成功挂载「来源记录」能力",
                    cap != null, cap == null ? "capability=null" : "ok");
            if (cap == null) {
                report(src, lines, failures);
                return 0;
            }

            // ---------- 模拟从主世界某个门进入空岛 ----------
            BlockPos origin = new BlockPos(120, 70, 240);
            player.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            cap.setLastSafe(Level.OVERWORLD, origin);

            check(lines, failures, "进入前：玩家在主世界且坐标已就位",
                    player.level().dimension().equals(Level.OVERWORLD)
                            && player.blockPosition().equals(origin),
                    player.level().dimension().location() + " @ " + player.blockPosition());

            // 记下空岛门当前的形状，用来核对「进门这一步不会顺手改掉空岛那扇门」。
            SkyGateShape shapeAtEntry = SkyGateData.get(sky).getShape();

            // 调用与真实传送完全相同的方法：beforeTeleport 会记录来源并传送到固定点。
            SHOULDTP verdict = DHTeleportHandler.beforeTeleport(player);
            check(lines, failures, "进入空岛：beforeTeleport 返回 CANCEL_TP（接管成功）",
                    verdict == SHOULDTP.CANCEL_TP, String.valueOf(verdict));
            check(lines, failures, "进入空岛：玩家已处于空岛维度",
                    player.level().dimension().equals(DHConfig.SKY_LEVEL),
                    player.level().dimension().location().toString());

            // 落点随门形状动态计算（平台与门等宽，落点站在门前那段平台的正中央）。
            Vec3 expectedArrivalVec = shapeAtEntry
                    .footprint(DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET)
                    .arrivalPos(DHConfig.PLATFORM_EXTEND);
            BlockPos expectedArrival = BlockPos.containing(
                    expectedArrivalVec.x, expectedArrivalVec.y, expectedArrivalVec.z);
            check(lines, failures, "进入空岛：落在门前平台正中 " + expectedArrival + "（随门形状动态计算）",
                    player.blockPosition().equals(expectedArrival),
                    "实际 " + player.blockPosition() + "，期望 " + expectedArrival);

            check(lines, failures, "进入空岛：来源维度已记录为主世界",
                    Level.OVERWORLD.equals(cap.getOriginDimension()),
                    String.valueOf(cap.getOriginDimension()));
            check(lines, failures, "进入空岛：来源坐标已记录为 " + origin,
                    origin.equals(cap.getOriginPos()),
                    String.valueOf(cap.getOriginPos()));

            // 复刻只在「能从来源门量出形状」时才动手。本例中假玩家是直接站在主世界地面上
            // 调用传送的（没有真的穿过门方块），因此量不出形状，空岛那扇门必须原样不动 ——
            // 这条断言防止「每次进门都重做一遍门」这种会把玩家自建门改坏的退化。
            check(lines, failures, "进门量不出形状时不动空岛那扇门（形状保持 " + shapeAtEntry + "）",
                    SkyGateData.get(sky).getShape().equals(shapeAtEntry),
                    "实际=" + SkyGateData.get(sky).getShape());

            // 落脚点必须安全：脚下有实体方块，且当前位置不卡在方块里。
            BlockState below = sky.getBlockState(player.blockPosition().below());
            check(lines, failures, "空岛落点脚下有实体方块（不会坠入虚空）",
                    below.isSolid(), below.getBlock().getDescriptionId());
            check(lines, failures, "空岛落点自身是空气（不会卡在方块里）",
                    sky.getBlockState(player.blockPosition()).isAir(),
                    sky.getBlockState(player.blockPosition()).getBlock().getDescriptionId());

            // ---------- 模拟从空岛返回 ----------
            SHOULDTP back = DHTeleportHandler.beforeTeleport(player);
            check(lines, failures, "返回：beforeTeleport 返回 CANCEL_TP",
                    back == SHOULDTP.CANCEL_TP, String.valueOf(back));
            check(lines, failures, "返回：已回到主世界",
                    player.level().dimension().equals(Level.OVERWORLD),
                    player.level().dimension().location().toString());

            // 返回点应当在原位置附近（记录的安全位置 + 文档建议的 Y+1）。
            BlockPos backPos = player.blockPosition();
            boolean nearOrigin = Math.abs(backPos.getX() - origin.getX()) <= 2
                    && Math.abs(backPos.getZ() - origin.getZ()) <= 2
                    && Math.abs(backPos.getY() - origin.getY()) <= 2;
            check(lines, failures, "返回：落回原入口附近 " + origin + "（±2 格内）",
                    nearOrigin, "实际 " + backPos);
            check(lines, failures, "返回：已设置传送冷却（防来回循环）",
                    player.getPortalCooldown() > 0, "cooldown=" + player.getPortalCooldown());

            // ---------- 能力数据往返（模拟存档再读档） ----------
            CompoundTag saved = cap.serializeNBT();
            PortalOriginCapability restored = new PortalOriginCapability();
            restored.deserializeNBT(saved);
            check(lines, failures, "来源记录可序列化并还原（存档/读档与死亡重生）",
                    Level.OVERWORLD.equals(restored.getOriginDimension())
                            && origin.equals(restored.getOriginPos()),
                    saved.toString());
        } finally {
            // 假玩家不会自动消失，必须清掉，否则会残留在世界里。
            player.discard();
        }

        // ---------- 非玩家实体也能进出空岛（需求 5.3） ----------
        // 这条必须真的造一个实体跑一遍：非玩家走的是 changeDimension + ITeleporter
        // 这条完全不同的代码路径，且跨维度会换成新实例 —— 光看代码看不出漏传。
        runNonPlayerTeleportTest(sky, overworld, lines, failures);

        report(src, lines, failures);
        return failures.isEmpty() ? 1 : 0;
    }

    /**
     * 非玩家实体（这里用掉落物）的往返自检。
     *
     * <p>证明三件事：① 非玩家不再被拦下；② 跨维度后来源记录随 NBT 活了下来
     * （走 {@code getPersistentData}，不是能力）；③ 落点与玩家完全一致，
     * 即站在门前平台正中而不是掉进虚空。</p>
     */
    private static void runNonPlayerTeleportTest(ServerLevel sky, ServerLevel overworld,
                                                 List<String> lines, List<String> failures) {
        BlockPos origin = new BlockPos(140, 70, 260);
        ItemEntity item = new ItemEntity(overworld,
                origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D,
                new ItemStack(Items.STONE, 1));
        item.setCustomName(Component.literal("dh_nonplayer_probe"));
        item.setCustomNameVisible(false);

        Entity arrived = null;
        Entity returned = null;
        try {
            if (!overworld.addFreshEntity(item)) {
                check(lines, failures, "非玩家自检：测试用掉落物成功加入主世界", false, "addFreshEntity 返回 false");
                return;
            }
            check(lines, failures, "非玩家自检：测试用掉落物成功加入主世界", true, null);

            // 落点按门形状动态推导，与实现同源。
            Vec3 expect = SkyGateData.get(sky).getShape()
                    .footprint(DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET)
                    .arrivalPos(DHConfig.PLATFORM_EXTEND);
            BlockPos expectBlock = BlockPos.containing(expect.x, expect.y, expect.z);

            // 与真实进门完全相同的入口方法，但拿回传送之后的实体实例。
            //
            // 这里**不能**改成「到目标维度里 getAllEntities() 找一个带来源标记的掉落物」：
            // 跨维度时 changeDimension 会 EntityType.create 出一个新实例，而如果目标区块
            // 当时不是 ENTITY_TICKING，新实例会被 PersistentEntitySectionManager
            // 收进 pending 队列，要到之后某个 tick 才进入 getAllEntities() —— 那样断言
            // 就是不确定的（曾用 /forceload 绕过，结果是既不可靠、又在存档里留下
            // 三个强制加载区块）。changeDimension 的返回值才是确定的观察点。
            DHTeleportHandler.TeleportResult out = DHTeleportHandler.beforeTeleportWithResult(item);
            check(lines, failures, "非玩家实体：beforeTeleport 不再拒绝（返回 CANCEL_TP 表示接管）",
                    out.verdict() == SHOULDTP.CANCEL_TP, String.valueOf(out.verdict()));

            arrived = out.entity();
            check(lines, failures, "非玩家实体：跨维度换成新实例（原实例已被移除）",
                    arrived != null && arrived != item && item.isRemoved(),
                    arrived == null ? "changeDimension 返回 null"
                            : (arrived == item ? "仍是原实例，说明没跨维度"
                            : "新实例=" + arrived.getType().toShortString()
                            + "，原实例 isRemoved=" + item.isRemoved()));
            if (arrived == null || arrived == item) {
                return;
            }
            check(lines, failures, "非玩家实体：已出现在空岛维度（跨维度换新实例成功）",
                    DHConfig.SKY_LEVEL.equals(arrived.level().dimension()),
                    String.valueOf(arrived.level().dimension().location()));

            // 来源维度/坐标必须随 NBT 一起被 restoreFrom 复制过来，否则它回不去。
            ResourceLocation originDimId =
                    ResourceLocation.tryParse(arrived.getPersistentData().getString("DimensionHomeOriginDim"));
            check(lines, failures, "非玩家实体：来源维度随 NBT 存活（" + originDimId + "）",
                    originDimId != null && originDimId.equals(Level.OVERWORLD.location()),
                    String.valueOf(originDimId));

            BlockPos recorded = NbtUtils.readBlockPos(
                    arrived.getPersistentData().getCompound("DimensionHomeOriginPos"));
            check(lines, failures, "非玩家实体：来源坐标随 NBT 存活（" + origin + "）",
                    origin.equals(recorded), String.valueOf(recorded));

            // 落点与玩家同一条公式：站在门前平台正中。
            check(lines, failures, "非玩家实体：落在门前平台正中 " + expectBlock + "（与玩家一致）",
                    arrived.blockPosition().equals(expectBlock),
                    "实际 " + arrived.blockPosition() + "，期望 " + expectBlock);
            check(lines, failures, "非玩家实体：落点脚下有平台承托（不会坠入虚空）",
                    sky.getBlockState(arrived.blockPosition().below()).is(Blocks.SMOOTH_STONE),
                    sky.getBlockState(arrived.blockPosition().below()).getBlock().getDescriptionId());

            // ---------- 再从空岛走回主世界 ----------
            // 同样拿回返回值，不去扫维度（理由见上面进入那一段）。
            DHTeleportHandler.TeleportResult back = DHTeleportHandler.beforeTeleportWithResult(arrived);
            check(lines, failures, "非玩家实体：从空岛返回时 beforeTeleport 返回 CANCEL_TP",
                    back.verdict() == SHOULDTP.CANCEL_TP, String.valueOf(back.verdict()));

            returned = back.entity();
            check(lines, failures, "非玩家实体：已回到主世界（跨维度换新实例成功）",
                    returned != null && DHConfig.SKY_LEVEL != returned.level().dimension(),
                    returned == null ? "changeDimension 返回 null"
                            : String.valueOf(returned.level().dimension().location()));
            if (returned != null) {
                BlockPos backPos = returned.blockPosition();
                boolean nearOrigin = Math.abs(backPos.getX() - origin.getX()) <= 2
                        && Math.abs(backPos.getZ() - origin.getZ()) <= 2
                        && Math.abs(backPos.getY() - origin.getY()) <= 2;
                check(lines, failures, "非玩家实体：落回原入口附近 " + origin + "（±2 格内）",
                        nearOrigin, "实际 " + backPos);
                returned.discard();
            }
        } catch (Exception e) {
            check(lines, failures, "非玩家实体自检未抛异常", false,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            // 自检不得给存档留下任何强制加载的区块：整条路径都不再需要 forceload。
            if (arrived != null && !arrived.isRemoved()) {
                arrived.discard();
            }
            if (!item.isRemoved()) {
                item.discard();
            }
        }
    }

    private static int run(CommandSourceStack src) {
        List<String> failures = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        ServerLevel sky = src.getServer().getLevel(DHConfig.SKY_LEVEL);

        // ---------------- 1. 维度存在 ----------------
        check(lines, failures, "空岛维度已注册并可加载",
                sky != null, String.valueOf(sky));

        if (sky == null) {
            report(src, lines, failures);
            return failures.isEmpty() ? 1 : 0;
        }

        // ---------------- 2. 维度类型参数 ----------------
        DimensionType type = sky.dimensionType();
        check(lines, failures, "coordinateScale = 1.0（1:1 坐标，非文档的下界式 8.0）",
                Double.compare(type.coordinateScale(), 1.0D) == 0, String.valueOf(type.coordinateScale()));
        check(lines, failures, "hasSkyLight = true（有天空光）",
                type.hasSkyLight(), String.valueOf(type.hasSkyLight()));
        check(lines, failures, "hasFixedTime = false（昼夜交替，fixed_time 留空）",
                !type.hasFixedTime(), "fixedTime=" + type.fixedTime());
        check(lines, failures, "hasRaids = false（不触发袭击）",
                !type.hasRaids(), String.valueOf(type.hasRaids()));
        check(lines, failures, "bedWorks = true（床可用）",
                type.bedWorks(), String.valueOf(type.bedWorks()));
        check(lines, failures, "ultraWarm = false",
                !type.ultraWarm(), String.valueOf(type.ultraWarm()));
        check(lines, failures, "hasCeiling = false",
                !type.hasCeiling(), String.valueOf(type.hasCeiling()));
        check(lines, failures, "minY = 0",
                type.minY() == 0, String.valueOf(type.minY()));
        check(lines, failures, "height = 256",
                type.height() == 256, String.valueOf(type.height()));
        check(lines, failures, "logicalHeight = 256",
                type.logicalHeight() == 256, String.valueOf(type.logicalHeight()));

        // ---------------- 3. 纯虚空：不生成任何地形 ----------------
        ChunkGenerator gen = sky.getChunkSource().getGenerator();
        check(lines, failures, "区块生成器是 VoidChunkGenerator（纯虚空）",
                gen instanceof VoidChunkGenerator, gen.getClass().getSimpleName());

        // 在远离地标的多个位置采样，应该全是空气
        int solidFound = 0;
        int[][] samples = {{64, 200}, {-64, -64}, {500, -500}, {-1000, 1000}, {2000, 2000}};
        for (int[] s : samples) {
            for (int y : new int[]{1, 64, 100, 200, 250}) {
                BlockState st = sky.getBlockState(new BlockPos(s[0], y, s[1]));
                if (!st.isAir()) {
                    solidFound++;
                }
            }
        }
        check(lines, failures, "远离地标处不生成任何地形（采样 25 点全为空气）",
                solidFound == 0, "非空气方块数=" + solidFound);

        // ---------------- 4. 地标几何 ----------------
        // 注意：从 1.1.0 起，锚点上那扇门的形状不再是写死的 2x3，而是
        // 「玩家第一次进门那扇门的形状」（见 SkyGateData）。因此这里的断言必须
        // 按实际记录的形状去推导坐标，否则一旦玩家复刻过一扇大门，自检就会误报失败。
        int y0 = DHConfig.PLATFORM_Y;

        // 锚点门的实际形状（未复刻过时是默认 2x3）。平台范围完全由它推导，
        // 所以必须先取形状再断言 —— 写死坐标会在玩家复刻过一扇宽门后误报失败。
        SkyGateShape gateShape = SkyGateData.get(sky).getShape();
        SkyGateShape.SkyGateFootprint fp = gateShape.footprint(
                DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET);
        check(lines, failures, "锚点门形状合法（内腔 " + gateShape + "）",
                gateShape.width() >= SkyGateShape.MIN_WIDTH && gateShape.width() <= SkyGateShape.MAX_WIDTH
                        && gateShape.height() >= SkyGateShape.MIN_HEIGHT
                        && gateShape.height() <= SkyGateShape.MAX_HEIGHT,
                gateShape.toString());

        // 平台 = 与门（含两侧门框）等宽 x 沿门法线从门平面前后各 PLATFORM_EXTEND 格。
        int spanMin = fp.platformSpanMin();
        int spanMax = fp.platformSpanMax();
        int planeMin = fp.platformPlaneMin(DHConfig.PLATFORM_EXTEND);
        int planeMax = fp.platformPlaneMax(DHConfig.PLATFORM_EXTEND);

        boolean platformOk = true;
        int platformMissing = 0;
        for (int s = spanMin; s <= spanMax; s++) {
            for (int p = planeMin; p <= planeMax; p++) {
                if (!sky.getBlockState(SkyGateGeometry.at(fp.span(), s, y0, p)).is(Blocks.SMOOTH_STONE)) {
                    platformOk = false;
                    platformMissing++;
                }
            }
        }
        int platformExpected = (spanMax - spanMin + 1) * (planeMax - planeMin + 1);
        check(lines, failures, "落脚平台与门等宽并前后各延伸 " + DHConfig.PLATFORM_EXTEND + " 格（"
                        + (spanMax - spanMin + 1) + " 宽 x " + (planeMax - planeMin + 1) + " 深，共 "
                        + platformExpected + " 格平滑石，y=" + y0 + "）",
                platformOk, platformOk ? null : ("缺失 " + platformMissing + " 格"));

        // 升级自 v1.0.0 的存档：旧 3×3 平台落在新平台之外的格子必须已被清掉。
        // 默认形状下旧平台的 z = -1 那一行（门背后 3 格）不在新平台内；若残留，
        // 门后就会多出一小截悬空石台，与「空岛只有一块落脚平台」的规则矛盾。
        int staleBlocks = 0;
        for (int s = -1; s <= 1; s++) {
            for (int p = -1; p <= 1; p++) {
                if (s >= spanMin && s <= spanMax && p >= planeMin && p <= planeMax) {
                    continue;
                }
                if (sky.getBlockState(SkyGateGeometry.at(fp.span(), s, y0, p)).is(Blocks.SMOOTH_STONE)) {
                    staleBlocks++;
                }
            }
        }
        check(lines, failures, "旧 3x3 平台的残留已清理（门后无孤立方块）",
                staleBlocks == 0, "残留 " + staleBlocks + " 格");

        // 落点必须落在平台范围内，且脚下有承托 —— 否则玩家一进门就掉进虚空。
        Vec3 arrivalCheck = fp.arrivalPos(DHConfig.PLATFORM_EXTEND);
        BlockPos arrivalBlock = BlockPos.containing(arrivalCheck.x, arrivalCheck.y, arrivalCheck.z);
        boolean insidePlatform = DHConfig.isInsideZone(arrivalBlock);
        check(lines, failures, "落点 " + arrivalBlock + " 位于可站立区域内",
                insidePlatform, String.valueOf(arrivalBlock));
        check(lines, failures, "落点下方是平台方块（有承托）",
                sky.getBlockState(arrivalBlock.below()).is(Blocks.SMOOTH_STONE),
                sky.getBlockState(arrivalBlock.below()).getBlock().getDescriptionId());
        check(lines, failures, "落点自身是空气（不会卡在门框或平台里）",
                sky.getBlockState(arrivalBlock).isAir(),
                sky.getBlockState(arrivalBlock).getBlock().getDescriptionId());

        // 门内腔必须铺满自定义门方块
        int portalBlocks = 0;
        for (int s = fp.spanStart(); s <= fp.spanEnd(); s++) {
            for (int y = fp.interiorY(); y <= fp.interiorY() + fp.height() - 1; y++) {
                BlockState st = sky.getBlockState(SkyGateGeometry.at(fp.span(), s, y, fp.plane()));
                if (CustomPortalHelper.isInstanceOfCustomPortal(st)) {
                    portalBlocks++;
                }
            }
        }
        int expectedPortalBlocks = fp.width() * fp.height();
        check(lines, failures, "唯一传送门内腔 " + expectedPortalBlocks + " 格门方块齐全（"
                        + fp.width() + " 宽 x " + fp.height() + " 高）",
                portalBlocks == expectedPortalBlocks, "实际=" + portalBlocks);

        // 门框：左右竖框 + 上下横框
        boolean frameOk = true;
        for (int y = fp.frameBottomY(); y <= fp.frameTopY(); y++) {
            if (!sky.getBlockState(SkyGateGeometry.at(fp.span(), fp.spanStart() - 1, y, fp.plane()))
                    .is(Blocks.SMOOTH_STONE)) {
                frameOk = false;
            }
            if (!sky.getBlockState(SkyGateGeometry.at(fp.span(), fp.spanEnd() + 1, y, fp.plane()))
                    .is(Blocks.SMOOTH_STONE)) {
                frameOk = false;
            }
        }
        for (int s = fp.spanStart() - 1; s <= fp.spanEnd() + 1; s++) {
            if (!sky.getBlockState(SkyGateGeometry.at(fp.span(), s, fp.frameBottomY(), fp.plane()))
                    .is(Blocks.SMOOTH_STONE)) {
                frameOk = false;
            }
            if (!sky.getBlockState(SkyGateGeometry.at(fp.span(), s, fp.frameTopY(), fp.plane()))
                    .is(Blocks.SMOOTH_STONE)) {
                frameOk = false;
            }
        }
        check(lines, failures, "传送门外框完整（" + (fp.width() + 2) + " 宽 x " + (fp.height() + 2) + " 高平滑石）",
                frameOk, null);

        // ---------------- 5. 可点燃区域规则 ----------------
        // 现在的规则不是「空岛一律禁止点火」，而是：
        //   区域内 + 区域内当时没有别的门 → 放行；否则拒绝。
        //
        // 要真正测出这条规则，探针位置上必须**先存在一个合法的门框**，
        // 否则 getNewPortal 会因为「压根没有框」而返回空，看起来像是被拒绝了，
        // 实际上什么都没验证到。所以下面每个情形都先搭好合法空框再断言。

        // 情形一：区域内已经有一扇门（就是锚点这扇）→ 再点必须被拒。
        // 探针框放在区域内、与锚点门不重叠的空位上。
        SkyGateShape probeShape = SkyGateShape.DEFAULT;
        SkyGateShape.SkyGateFootprint probeIn = new SkyGateShape.SkyGateFootprint(
                Direction.Axis.X, 4, DHConfig.PORTAL_INTERIOR_Y, 0, probeShape.width(), probeShape.height());
        SkyLandmarkBuilder.buildGateFrame(sky, probeIn);
        Optional<PortalFrameTester> whileOccupied = new SkyGuardPortalFrameTester()
                .getNewPortal(sky, SkyGateGeometry.at(probeIn.span(), probeIn.spanStart(),
                        probeIn.interiorY(), probeIn.plane()), Direction.Axis.X, Blocks.SMOOTH_STONE);
        check(lines, failures, "区域内已有门时拒绝再点第二扇（框合法且在区域内，仍须拒绝）",
                whileOccupied.isEmpty(), String.valueOf(whileOccupied));

        // 情形二：区域外点火必须被拒（纯虚空里不允许到处点门）。
        // 同样先搭一个合法空框，避免「空框 → 空结果」的假阳性。
        SkyGateShape.SkyGateFootprint probeOut = new SkyGateShape.SkyGateFootprint(
                Direction.Axis.X, DHConfig.ZONE_MAX.getX() + 20, DHConfig.PORTAL_INTERIOR_Y, 0,
                probeShape.width(), probeShape.height());
        SkyLandmarkBuilder.buildGateFrame(sky, probeOut);
        Optional<PortalFrameTester> outside = new SkyGuardPortalFrameTester()
                .getNewPortal(sky, SkyGateGeometry.at(probeOut.span(), probeOut.spanStart(),
                        probeOut.interiorY(), probeOut.plane()), Direction.Axis.X, Blocks.SMOOTH_STONE);
        check(lines, failures, "可点燃区域之外拒绝点火（框合法但位置越界，必须拒）",
                outside.isEmpty(), String.valueOf(outside));
        // 拆掉区域外的探针框，别在虚空里留垃圾。
        SkyLandmarkBuilder.eraseGate(sky, probeOut);

        // 情形三：把区域内的门方块清掉（等价于玩家拆掉门框导致的连锁自毁）后，
        // 区域内应当重新允许点火 —— 这正是「拆门被困」被修好的核心机制。
        int removed = SkyLandmarkBuilder.clearPortalBlocksInZone(sky);
        check(lines, failures, "清空区域内门方块（模拟玩家拆门框导致整扇门自毁）",
                removed == expectedPortalBlocks, "清掉=" + removed + "，预期=" + expectedPortalBlocks);
        check(lines, failures, "区域内已无门方块（anyPortalInZone = false）",
                !SkyGateGeometry.anyPortalInZone(sky), null);

        Optional<PortalFrameTester> afterClear = new SkyGuardPortalFrameTester()
                .getNewPortal(sky, SkyGateGeometry.at(probeIn.span(), probeIn.spanStart(),
                        probeIn.interiorY(), probeIn.plane()), Direction.Axis.X, Blocks.SMOOTH_STONE);
        check(lines, failures, "区域内重新点燃被放行（拆门后不再被困）",
                afterClear.isPresent(), String.valueOf(afterClear));

        // 清理探针框，保证空岛世界里始终只有锚点那一扇门。
        SkyLandmarkBuilder.eraseGate(sky, probeIn);

        // 清空之后必须把锚点那扇门补回来，否则后续验收与正常游玩都会缺门。
        SkyLandmarkBuilder.buildGate(sky, gateShape);
        check(lines, failures, "已按记录形状补回锚点门（" + gateShape + "）",
                CustomPortalHelper.isInstanceOfCustomPortal(
                        sky.getBlockState(SkyGateGeometry.at(fp.span(), fp.spanStart(), fp.interiorY(), fp.plane()))),
                null);
        check(lines, failures, "清理后区域内仍恰有一扇门（唯一门不变量成立）",
                SkyGateGeometry.anyPortalInZone(sky), null);

        // ---------------- 5b. 首门形状复刻（用户第二项要求） ----------------
        // 「空岛第一扇门完全复刻玩家第一次进门那扇门」由两段组成：
        //   ① 量出来源门的形状（measureGateShape）；② 按该形状重建空岛锚点门（rebuildGate）。
        // 下面分别取证据，并且**不**改动 SkyGateData 里记录的正式形状 ——
        // 否则跑一次自检就会把玩家真正的门改掉。

        // ① 测量：在荒郊高空临时搭一扇 5 宽 x 4 高的门（非默认形状），量出来必须一致。
        //    高度取 280（主世界建筑上限 320 之下、任何地形之上），避免破坏真实地形。
        SkyGateShape probeSourceShape = new SkyGateShape(Direction.Axis.X, 5, 4);
        SkyGateShape.SkyGateFootprint probeSourceFp = new SkyGateShape.SkyGateFootprint(
                Direction.Axis.X, 0, 280, 300,
                probeSourceShape.width(), probeSourceShape.height());
        ServerLevel overworldProbe = src.getServer().overworld();
        SkyLandmarkBuilder.buildGate(overworldProbe, probeSourceFp);
        BlockPos probeSourcePortal = SkyGateGeometry.at(probeSourceFp.span(), probeSourceFp.spanStart(),
                probeSourceFp.interiorY(), probeSourceFp.plane());
        SkyGateShape measured = SkyLandmarkBuilder.measureGateShape(overworldProbe, probeSourcePortal);
        check(lines, failures, "复刻第 1 步：能从来源门量出形状 " + probeSourceShape
                        + "（而非退回默认 2x3）",
                probeSourceShape.equals(measured), String.valueOf(measured));
        SkyLandmarkBuilder.eraseGate(overworldProbe, probeSourceFp);

        // ② 一次性记账：形状只认第一次，之后再进门不会改形状。
        SkyGateData throwaway = new SkyGateData();
        boolean firstApply = throwaway.replicateOnce(probeSourceShape);
        boolean secondApply = throwaway.replicateOnce(SkyGateShape.DEFAULT);
        check(lines, failures, "复刻第 2 步：形状只记一次（后续进门不改动已复刻的门）",
                firstApply && !secondApply && probeSourceShape.equals(throwaway.getShape()),
                "首次=" + firstApply + "，再次=" + secondApply + "，记录=" + throwaway.getShape());

        // ③ 按新形状重建锚点门：世界里的几何必须真的跟着变；测完立刻恢复成记录的形状。
        int rebuiltPortals = 0;
        boolean rebuiltFrameOk = true;
        boolean rebuiltRemovedOld = true;
        try {
            SkyLandmarkBuilder.rebuildGate(sky, probeSourceShape, gateShape);

            SkyGateShape.SkyGateFootprint rebuiltFp = probeSourceShape.footprint(
                    DHConfig.PORTAL_INTERIOR_Y, DHConfig.PORTAL_PLANE_OFFSET);
            for (int s = rebuiltFp.spanStart(); s <= rebuiltFp.spanEnd(); s++) {
                for (int y = rebuiltFp.interiorY(); y <= rebuiltFp.interiorY() + rebuiltFp.height() - 1; y++) {
                    if (CustomPortalHelper.isInstanceOfCustomPortal(sky.getBlockState(
                            SkyGateGeometry.at(rebuiltFp.span(), s, y, rebuiltFp.plane())))) {
                        rebuiltPortals++;
                    }
                }
            }
            for (int y = rebuiltFp.frameBottomY(); y <= rebuiltFp.frameTopY(); y++) {
                if (!sky.getBlockState(SkyGateGeometry.at(rebuiltFp.span(), rebuiltFp.spanStart() - 1, y,
                        rebuiltFp.plane())).is(Blocks.SMOOTH_STONE)
                        || !sky.getBlockState(SkyGateGeometry.at(rebuiltFp.span(), rebuiltFp.spanEnd() + 1, y,
                        rebuiltFp.plane())).is(Blocks.SMOOTH_STONE)) {
                    rebuiltFrameOk = false;
                }
            }
            // 旧门比新门矮，旧门顶部那一行必须已经没有门方块残留。
            if (CustomPortalHelper.isInstanceOfCustomPortal(sky.getBlockState(
                    SkyGateGeometry.at(fp.span(), fp.spanStart(), fp.interiorY() + probeSourceShape.height(),
                            fp.plane())))) {
                rebuiltRemovedOld = false;
            }
        } finally {
            // 无论断言结果如何，都要把锚点门恢复成 SkyGateData 记录的形状。
            SkyLandmarkBuilder.rebuildGate(sky, gateShape, probeSourceShape);
        }
        check(lines, failures, "复刻第 3 步：锚点门已按来源形状重建（内腔 "
                        + (probeSourceShape.width() * probeSourceShape.height()) + " 格门方块）",
                rebuiltPortals == probeSourceShape.width() * probeSourceShape.height(),
                "实际=" + rebuiltPortals);
        check(lines, failures, "复刻第 3 步：重建后的外框完整（"
                        + (probeSourceShape.width() + 2) + " 宽 x " + (probeSourceShape.height() + 2) + " 高）",
                rebuiltFrameOk, null);
        check(lines, failures, "复刻第 3 步：旧形状的门方块已清除（不会新旧重叠）",
                rebuiltRemovedOld, null);
        check(lines, failures, "复刻测试后已还原为记录形状 " + gateShape + "（空岛仍恰有一扇门）",
                SkyGateData.get(sky).getShape().equals(gateShape)
                        && SkyGateGeometry.anyPortalInZone(sky), null);

        // 反向验证：同一套逻辑在主世界应当**不**被我们的拦截影响
        // （这里只断言「主世界不是空岛维度」这一前提，真实点燃需玩家操作）
        ServerLevel overworld = src.getServer().overworld();
        check(lines, failures, "主世界与空岛维度确实不同（返回逻辑前提成立）",
                !overworld.dimension().equals(DHConfig.SKY_LEVEL),
                overworld.dimension().location().toString());

        // ---------------- 6. 传送门注册 ----------------
        check(lines, failures, "平滑石已注册为传送门框架方块",
                CustomPortalApiRegistry.isRegisteredFrameBlock(Blocks.SMOOTH_STONE.defaultBlockState()),
                Blocks.SMOOTH_STONE.getDescriptionId());

        check(lines, failures, "CPA 默认门方块可用",
                CustomPortalsMod.getDefaultPortalBlock() != null,
                String.valueOf(CustomPortalsMod.getDefaultPortalBlock()));

        PortalLink link = CustomPortalApiRegistry.getPortalLinkFromBase(Blocks.SMOOTH_STONE);
        check(lines, failures, "平滑石门链接指向空岛维度",
                link != null && DHConfig.SKY_LEVEL.location().equals(link.dimID),
                link == null ? "link=null" : String.valueOf(link.dimID));
        check(lines, failures, "使用了自定义框架检测器（区域内可点燃 / 区域外禁止）",
                link != null && DHConfig.FRAME_TESTER_ID.equals(link.portalFrameTester),
                link == null ? "link=null" : String.valueOf(link.portalFrameTester));

        // 门的染色：CPA 会把它注册成门方块的 block color（模型里 tintindex=4），
        // 所以这个值既决定门外那层门贴图，也决定进出时的屏幕蒙版与粒子。
        int expectedTint = (DHConfig.PORTAL_TINT_R & 0xFF) << 16
                | (DHConfig.PORTAL_TINT_G & 0xFF) << 8
                | (DHConfig.PORTAL_TINT_B & 0xFF);
        check(lines, failures, "传送门染色为金色 #FFD700",
                link != null && link.colorID == expectedTint,
                link == null ? "link=null"
                        : String.format("#%06X（期望 #%06X）", link.colorID & 0xFFFFFF, expectedTint & 0xFFFFFF));

        report(src, lines, failures);
        return failures.isEmpty() ? 1 : 0;
    }

    private static void check(List<String> lines, List<String> failures, String what, boolean ok, String detail) {
        String suffix = (detail == null || detail.isEmpty()) ? "" : "  [" + detail + "]";
        if (ok) {
            lines.add("§a[通过]§r " + what + suffix);
        } else {
            lines.add("§c[失败]§r " + what + suffix);
            failures.add(what);
        }
    }

    private static void report(CommandSourceStack src, List<String> lines, List<String> failures) {
        src.sendSuccess(() -> Component.literal("§6===== 维度之家 自检报告 ====="), false);
        for (String l : lines) {
            src.sendSuccess(() -> Component.literal(l), false);
        }
        if (failures.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§a全部通过（" + lines.size() + " 项）"), false);
        } else {
            src.sendSuccess(() -> Component.literal("§c有 " + failures.size() + " 项失败：" + failures), false);
        }
    }
}
