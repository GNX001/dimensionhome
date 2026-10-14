package com.dimensionhome;

import com.dimensionhome.world.SkyGuardPortalFrameTester;
import com.dimensionhome.world.VoidChunkGenerator;
import com.mojang.serialization.Codec;
import net.kyrptonaught.customportalapi.CustomPortalApiRegistry;
import net.kyrptonaught.customportalapi.api.CustomPortalBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《维度之家 / Dimension Home》主类。
 *
 * <p>本 MOD 新增一个纯虚空的「空岛维度」，通过**平滑石**框架的传送门进入，
 * 抵达固定在 (0,100,0) 附近的一块与门等宽的落脚平台与唯一一扇返回门。</p>
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li>注册虚空区块生成器（{@code dimensionhome:void_generator}）；</li>
 *   <li>向 Custom Portal API Reforged 注册平滑石传送门，并挂上
 *       「空岛内禁止点燃」的框架检测器；</li>
 *   <li>注册玩家身上的来源记录能力（{@code PortalOriginCapability}）。</li>
 * </ol>
 */
@Mod(DHConfig.MOD_ID)
public class DimensionHome {

    /** 日志。 */
    public static final Logger LOGGER = LoggerFactory.getLogger(DHConfig.MOD_ID);

    /** 自定义区块生成器的注册器（写入原版 {@code Registries.CHUNK_GENERATOR}）。 */
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, DHConfig.MOD_ID);

    /** 虚空区块生成器的编解码器，对应数据包里的 {@code "type": "dimensionhome:void_generator"}。 */
    public static final RegistryObject<Codec<? extends ChunkGenerator>> VOID_GENERATOR =
            CHUNK_GENERATORS.register("void_generator", () -> VoidChunkGenerator.CODEC);

    public DimensionHome(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 注册区块生成器编解码器：数据包 JSON 靠这个 id 反序列化出 VoidChunkGenerator。
        CHUNK_GENERATORS.register(modEventBus);

        // 注意：传送门注册**不能**在这里做。
        // CustomPortalBuilder.registerPortal() 内部会取 CPA 自己的门方块
        // （cpapireforged:custom_portal_block）并调用 RegistryObject.get()，
        // 而该方块要到注册事件处理完才存在；在构造阶段调用会抛
        // NullPointerException: Registry Object not present。
        // 因此改为在 FMLCommonSetupEvent 中注册，见 DHModEvents#onCommonSetup。

        LOGGER.info("维度之家：构造完成（维度 {}）", DHConfig.SKY_LEVEL.location());
    }

    /**
     * 注册平滑石传送门。由 {@code FMLCommonSetupEvent} 调用（此时所有注册表已就绪）。
     *
     * <p>这里不涉及维度校验：{@code CustomPortalApiRegistry.addPortal} 只有在
     * 已知维度列表非空时才检查目标维度，而那个列表要等到
     * {@code ServerStartedEvent} 才被填充，所以此时注册是安全的。</p>
     */
    public static void registerPortal() {
        // 先登记自定义框架检测器，供下面 customFrameTester 引用。
        CustomPortalApiRegistry.registerPortalFrameTester(DHConfig.FRAME_TESTER_ID, SkyGuardPortalFrameTester::new);

        CustomPortalBuilder.beginPortal()
                // 框架方块必须是平滑石（不是台阶、不是其它变种）。
                .frameBlock(Blocks.SMOOTH_STONE)
                // 目标维度：本 MOD 的空岛维度。
                .destDimID(DHConfig.SKY_LEVEL.location())
                // 传送门玻璃的染色（金色 #FFD700）。
                .tintColor(DHConfig.PORTAL_TINT_R, DHConfig.PORTAL_TINT_G, DHConfig.PORTAL_TINT_B)
                // 使用我们自己的框架检测器：主世界/下界/末地可点燃，空岛维度内禁止点燃。
                .customFrameTester(DHConfig.FRAME_TESTER_ID)
                // 接管传送：返回 CANCEL_TP 让 CPA 不要自己处理，由我们决定落点。
                .registerBeforeTPEvent(com.dimensionhome.portal.DHTeleportHandler::beforeTeleport)
                // 打火石点火（默认 PortalLink 的点火源就是 FIRE）。
                .registerPortal();
    }
}
