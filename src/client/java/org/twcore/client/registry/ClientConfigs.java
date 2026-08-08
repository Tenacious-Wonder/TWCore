package org.twcore.client.registry;

import org.twcore.api.config.ConfigInfluencer;
import org.twcore.api.config.ConfigType;
import org.twcore.api.config.TwConfig;
import org.twcore.client.model.ModelLoadingRulesData;

public class ClientConfigs {
    public static final ConfigType<ModelLoadingRulesData> MODEL_LOADING_RULES =
            ConfigType.of(
                    "model_loading_rules",
                    ModelLoadingRulesData.CODEC,
                    ModelLoadingRulesData::createDefault,
                    null,
                    ConfigInfluencer.ConfigSide.CLIENT
            );

    /**
     * 注册所有配置到给定 {@link TwConfig} 构造器。
     *
     * @param config 与模组关联的配置构造器
     */
    public static void registerAll(TwConfig config) {
        config.registerClientConfig(MODEL_LOADING_RULES);
    }
}
