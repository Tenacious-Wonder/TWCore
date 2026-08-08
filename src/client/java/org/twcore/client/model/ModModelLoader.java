package org.twcore.client.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.config.TwConfig;
import org.twcore.client.registry.ClientConfigs;

import java.util.List;

public class ModModelLoader implements ModelLoadingPlugin {
    private static final Logger LOGGER = TWCore.LOGGER;

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        // 获取已加载的模型规则配置
        ModelLoadingRulesData data = TwConfig.get(TWCore.MOD_ID, ClientConfigs.MODEL_LOADING_RULES);
        if (data == null || data.rules().isEmpty()) {
            return;
        }

        List<String> ruleStrings = data.rules();
        List<Identifier> models = ModelRuleRegistry.getInstance().resolveAll(ruleStrings);

        // 输出解析错误
        List<String> errors = ModelRuleRegistry.getInstance().getLastErrors();
        for (String error : errors) {
            LOGGER.warn("[ModelLoadingRules] {}", error);
        }

        if (!models.isEmpty()) {
            pluginContext.addModels(models);
        }
    }
}