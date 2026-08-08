package org.twcore.client.model;

import com.mojang.serialization.Codec;
import org.twcore.api.config.ConfigInfluencer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 模型加载规则配置数据。
 * 内部仅维护一个去重且保持插入顺序的规则字符串列表。
 */
public record ModelLoadingRulesData(List<String> rules) {
    public static final Codec<ModelLoadingRulesData> CODEC =
            Codec.STRING.listOf()
                    .xmap(ModelLoadingRulesData::new, ModelLoadingRulesData::rules)
                    .fieldOf("rules")
                    .codec();

    /**
     * 创建默认实例。
     * 从所有影响器中提取 {@code List<String>} 类型的载荷，合并去重。
     *
     * @param influencers 有效影响器列表
     * @return 合并后的默认数据
     */
    public static ModelLoadingRulesData createDefault(List<ConfigInfluencer<?>> influencers) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (ConfigInfluencer<?> inf : influencers) {
            Object payload = inf.payload();
            if (payload instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        set.add(s);
                    }
                }
            }
        }
        return new ModelLoadingRulesData(new ArrayList<>(set));
    }
}