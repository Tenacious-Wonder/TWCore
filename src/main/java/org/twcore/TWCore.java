package org.twcore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twcore.api.TwModManager;
import org.twcore.api.event.TwCoreRegisterEvent;
import org.twcore.blockvolume.BlockVolumeAttachments;
import org.twcore.blockvolume.BlockVolumeManager;
import org.twcore.process.playeraction.PlayerActionFactory;
import org.twcore.process.playeraction.impl.AddContentPlayerAction;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;
import org.twcore.registry.ContainerTypes;
import org.twcore.registry.RegistryInit;

public class TWCore implements ModInitializer {
    public static String MOD_ID = "tw_core";
    public static Logger LOGGER = LoggerFactory.getLogger("TW's Core");

    @Override
    public void onInitialize() {
        RegistryInit.init();
        TwCoreRegisterEvent.TW_CORE_REGISTRAR.register(TWCore::register);

        // 杂项
        ContainerTypes.initDefaultMappings();
        blockVolumeInit();
        registerDefaultAction();

        LOGGER.info("TW`s Core is initializing!");
    }

    /**
     * TW`s Core对自己注册。
     *
     * @see TwModManager
     */
    private static void register() {
        TwModManager.IMPL.register(MOD_ID, 4);
    }

    // ==================== 其他注册逻辑 ====================

    /**
     * 方块体事件注册。
     *
     * @see org.twcore.api.blockvolume.BlockVolume
     */
    private static void blockVolumeInit(){
        // attachment 需在模组初始化期间注册，先唤醒静态初始化
        BlockVolumeAttachments.registerAll();
        ServerChunkEvents.CHUNK_LOAD.register(BlockVolumeManager::onChunkLoad);
    }

    /**
     * 玩家操作类型注册。
     *
     * @see org.twcore.api.process.PlayerAction
     */
    public static void registerDefaultAction() {
        PlayerActionFactory.register(
                AddItemPlayerAction.TYPE,
                AddItemPlayerAction::fromParams,
                context -> AddItemPlayerAction.fromContext(context).orElse(null)
        );
        PlayerActionFactory.register(
                AddContentPlayerAction.TYPE,
                AddContentPlayerAction::fromParams,
                context -> AddContentPlayerAction.fromContext(context).orElse(null)
        );
    }
}
