package org.twcore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twcore.api.TwModManager;
import org.twcore.blockpile.CubeBlockPileManager;
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
        register();

        // 杂项
        ContainerTypes.initDefaultMappings();
        cubeBlockPileInit();
        registerDefaultAction();

        LOGGER.info("TW`s Core is initializing!");
    }

    /**
     * TW`s Core对自己注册。
     *
     * @see TwModManager
     */
    private void register() {
        TwModManager.IMPL.register(MOD_ID, 3);
    }

    // ==================== 其他注册逻辑 ====================

    /**
     * 方块堆事件注册。
     *
     * @see org.twcore.api.blockpile.CubeBlockPile
     */
    private static void cubeBlockPileInit(){
        ServerWorldEvents.LOAD.register(CubeBlockPileManager::onWorldStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(CubeBlockPileManager::onServerStopping);
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
