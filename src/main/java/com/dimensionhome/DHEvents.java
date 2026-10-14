package com.dimensionhome;

import com.dimensionhome.capability.PortalOriginCapability;
import com.dimensionhome.portal.DHTeleportHandler;
import com.dimensionhome.world.SkyLandmarkBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 游戏事件总线（{@code MinecraftForge.EVENT_BUS}）订阅者。
 *
 * <p>注意：mod 事件总线的事件（例如能力注册）放在 {@link DHModEvents} 里，
 * 两类事件不能混在同一个类上，否则事件不会被派发。</p>
 */
@Mod.EventBusSubscriber(modid = DHConfig.MOD_ID)
public final class DHEvents {

    private DHEvents() {
    }

    /**
     * 把「来源记录」能力挂到**玩家**身上。
     *
     * <p>只有玩家需要跨维度记录来源，因此这里用 {@code instanceof Player}
     * 过滤，避免给世界里每一个实体都挂一个无用的能力实例。</p>
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PortalOriginCapability.ID, new PortalOriginCapability());
        }
    }

    /**
     * 死亡重生时把来源记录复制到新玩家实体上。
     *
     * <p>Forge 的能力不会自动跨死亡保留，必须手动复制，否则玩家死在空岛后
     * 回不去原来的门。</p>
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PortalOriginCapability.CAPABILITY).ifPresent(oldCap ->
                event.getEntity().getCapability(PortalOriginCapability.CAPABILITY).ifPresent(newCap ->
                        newCap.copyFrom(oldCap)));
        event.getOriginal().invalidateCaps();
    }

    /**
     * 每 tick 维护「最后安全位置」。
     *
     * <p>玩家站在传送门方块里时不更新，这样进门瞬间记录的来源位置
     * 就是「走进门之前站的地方」，返回时不会落回门里造成死循环。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ServerPlayer serverPlayer) {
            DHTeleportHandler.tickSafePosition(serverPlayer);
        }
    }

    /**
     * 服务器启动后建造空岛地标（唯一传送门 + 与门等宽的落脚平台）。
     *
     * <p>必须等服务器完全启动、维度都已加载之后再做，否则
     * {@code server.getLevel(SKY_LEVEL)} 会返回 null。</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel skyLevel = event.getServer().getLevel(DHConfig.SKY_LEVEL);
        SkyLandmarkBuilder.ensureLandmark(skyLevel, DimensionHome.LOGGER);
    }

    /**
     * 注册 {@code /dimensionhome selftest} 运行时自检命令。
     *
     * <p>用途是把游戏内验收变成可复现的命令输出，避免每次改代码都要人工进世界核对。</p>
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(DHSelfTest.build());
    }
}
