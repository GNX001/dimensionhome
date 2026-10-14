package com.dimensionhome;

import com.dimensionhome.capability.PortalOriginCapability;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * mod 事件总线（{@code Bus.MOD}）订阅者。
 *
 * <p>{@link RegisterCapabilitiesEvent} 属于 mod 事件总线，必须与
 * Forge 游戏事件总线分开注册，否则事件永远不会被派发，
 * 能力在使用时会因「未注册」而报错。</p>
 */
@Mod.EventBusSubscriber(modid = DHConfig.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DHModEvents {

    private DHModEvents() {
    }

    /**
     * 注册「来源记录」能力类型。
     *
     * <p>这一步是必须的：只有在此注册过的能力，才能被
     * {@code CapabilityManager.get(...)} 取出并在实体上读写。</p>
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PortalOriginCapability.class);
    }

    /**
     * 通用初始化：注册平滑石传送门。
     *
     * <p>必须等到这个阶段才能注册传送门，因为
     * {@code CustomPortalBuilder.registerPortal()} 会立即读取 CPA 自己的
     * 门方块注册对象，而它只在注册事件完成后才存在。</p>
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // 用 enqueueWork 保证在并行分发结束后、主线程上执行。
        event.enqueueWork(DimensionHome::registerPortal);
    }
}
