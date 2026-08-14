package org.twcore.client.model;

import net.minecraft.util.Identifier;
import org.twcore.client.api.render.ModelRule;

import java.util.List;

/**
 * Core 内置的默认模型规则类型，随客户端初始化注册，
 * 供所有模组在 {@code model_loading_rules} 配置中直接使用。
 */
public final class DefaultModelRules {
    private DefaultModelRules() {
    }

    /** 注册 Core 自带的通用模型规则类型。 */
    public static void register() {
        // 任意路径模型
        ModelRule.register("plain_model", params -> {
            if (params.size() < 2) {
                throw new IllegalArgumentException("Usage: plain_model|<namespace>|<path>");
            }
            return List.of(new Identifier(params.get(0), params.get(1)));
        });
    }
}
