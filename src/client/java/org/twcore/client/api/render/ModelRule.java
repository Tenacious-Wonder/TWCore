package org.twcore.client.api.render;

import net.minecraft.util.Identifier;
import org.twcore.client.model.ModelRuleRegistry;

import java.util.List;
import java.util.function.Function;

/**
 * <h1>模型加载规则 API</h1>
 * <p>
 * 允许其他模组注册自定义的模型生成规则类型。
 * 规则类型通过字符串标识，并提供一个解析函数，将参数列表转换为
 * 一组 {@link Identifier}（模型标识符）。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ModelRule.register("ore_variant", params -> {
 *     String ore = params.get(0);
 *     String stone = params.get(1);
 *     Identifier modelId = new Identifier("mymod", "block/" + ore + "_" + stone);
 *     return List.of(modelId);
 * });
 * }</pre>
 *
 * <h2>规则字符串格式</h2>
 * <p>
 * 规则配置字符串格式为 {@code 类型|参数1|参数2|...}。
 * 注册的解析器仅接收参数部分（类型名已被剥离），参数按 {@code |} 分割。
 * 参数部分不允许包含 {@code |} 字符。
 * </p>
 *
 * <p>本类为纯静态工具，不可实例化。</p>
 *
 * @see ModelRuleRegistry
 */
public final class ModelRule {
    private ModelRule() {}

    /**
     * 注册一个模型规则类型。
     *
     * @param type   规则类型名称，大小写敏感，不能为空
     * @param parser 解析函数，输入为参数列表（由配置字符串分割而来，不含类型名），
     *               输出为该规则生成的所有模型标识符
     * @throws IllegalArgumentException 如果 type 为 {@code null} 或空字符串，或 parser 为 {@code null}
     */
    public static void register(String type, Function<List<String>, List<Identifier>> parser) {
        ModelRuleRegistry.registerType(type, parser);
    }
}