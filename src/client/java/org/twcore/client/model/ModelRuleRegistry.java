package org.twcore.client.model;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.twcore.TWCore;

import java.util.*;
import java.util.function.Function;

/**
 * 模型规则注册表。
 * 管理规则类型及其解析器，负责解析配置字符串并生成待加载的模型标识符列表。
 */
public final class ModelRuleRegistry {
    private static final Logger LOGGER = TWCore.LOGGER;

    private final Map<String, Function<List<String>, List<Identifier>>> parsers = new LinkedHashMap<>();
    private final List<String> lastErrors = new ArrayList<>();

    private static final ModelRuleRegistry INSTANCE = new ModelRuleRegistry();

    private ModelRuleRegistry() {}

    public static ModelRuleRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个规则类型。
     *
     * @param type   规则类型字符串，大小写敏感，不能为空
     * @param parser 解析器，输入为去掉类型名后的参数列表，输出为模型标识符列表
     * @throws IllegalArgumentException 如果 type 为空或 parser 为 null
     */
    public static void registerType(String type, Function<List<String>, List<Identifier>> parser) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Rule type cannot be null or empty");
        }
        if (parser == null) {
            throw new IllegalArgumentException("Parser function cannot be null");
        }
        INSTANCE.parsers.put(type, parser);
    }

    /**
     * 批量解析规则字符串，收集所有模型标识符。
     *
     * @param ruleStrings 完整的规则字符串列表，格式 "类型|参数1|参数2|..."
     * @return 去重后的模型标识符列表
     */
    public List<Identifier> resolveAll(List<String> ruleStrings) {
        lastErrors.clear();
        Set<Identifier> identifiers = new LinkedHashSet<>();

        for (String raw : ruleStrings) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String[] parts = raw.split("\\|", 2);
            String type = parts[0].trim();
            if (type.isEmpty()) {
                lastErrors.add("Empty type in rule: '" + raw + "'");
                continue;
            }

            Function<List<String>, List<Identifier>> parser = parsers.get(type);
            if (parser == null) {
                lastErrors.add("Unknown rule type: '" + type + "'");
                continue;
            }

            List<String> params;
            if (parts.length > 1) {
                params = Arrays.stream(parts[1].split("\\|"))
                        .map(String::trim)
                        .toList();
            } else {
                params = Collections.emptyList();
            }

            try {
                List<Identifier> result = parser.apply(params);
                if (result != null) {
                    identifiers.addAll(result);
                }
            } catch (Exception e) {
                lastErrors.add("Error executing rule '" + type + "': " + e.getMessage());
                LOGGER.warn("Model rule parser for type '{}' threw an exception", type, e);
            }
        }
        return new ArrayList<>(identifiers);
    }

    /**
     * 获取最近一次 {@link #resolveAll} 调用产生的错误信息快照。
     * 返回的列表为副本，不受后续 {@code resolveAll} 调用的影响。
     */
    public List<String> getLastErrors() {
        return new ArrayList<>(lastErrors);
    }
}
