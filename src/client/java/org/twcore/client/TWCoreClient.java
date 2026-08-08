package org.twcore.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import org.twcore.TWCore;
import org.twcore.api.config.TwConfig;
import org.twcore.api.event.TwCoreClientRegisterEvent;
import org.twcore.client.model.ModModelLoader;
import org.twcore.client.registry.ClientConfigs;

public class TWCoreClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelLoadingPlugin.register(new ModModelLoader());
        TwCoreClientRegisterEvent.TW_CORE_CLIENT_REGISTRAR.register(TWCoreClient::register);
    }

    private static void register() {
        ClientConfigs.registerAll(TwConfig.forMod(TWCore.MOD_ID));
    }
}
