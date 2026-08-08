package org.twcore.api.config;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * <h1>配置类型元信息</h1>
 * <p>
 * 定义一种配置的完整描述，包括名称、编解码器、默认值生成逻辑、
 * 版本迁移器以及所属物理端。
 * </p>
 *
 * <h2>默认值工厂</h2>
 * <p>
 * {@link #defaultFactory()} 接收一个 {@code List<ConfigInfluencer<?>>}，
 * 即通配符类型的影响器列表。工厂方法内部应通过模式匹配或类型检查
 * 提取特定类型的影响器，并将其应用到初始默认状态上，返回最终的默认配置数据。
 * 此设计保证了跨模组默认值叠加与加载顺序完全解耦。
 * </p>
 *
 * <h2>所属端</h2>
 * <p>
 * 通过 {@link #side()} 指定配置是 {@link ConfigInfluencer.ConfigSide#COMMON 双端通用}
 * 还是 {@link ConfigInfluencer.ConfigSide#CLIENT 仅客户端}。客户端配置在服务端会被忽略。
 * 未指定时默认为 {@code COMMON}。
 * </p>
 *
 * @param name           配置名称（同时用作文件名，不含 {@code .json} 后缀）
 * @param codec          当前版本数据的 Mojang {@link Codec}，用于 JSON 序列化与反序列化
 * @param defaultFactory 最终默认值生成函数，输入为有效影响器列表，输出为合并后的默认值
 * @param migrator       版本迁移器，可为 {@code null} 表示不支持自动迁移
 * @param side           配置所属端，默认为 {@link ConfigInfluencer.ConfigSide#COMMON}
 * @param <T>            配置数据类型
 * @see ConfigInfluencer
 */
public record ConfigType<T>(
        String name,
        Codec<T> codec,
        Function<List<ConfigInfluencer<?>>, T> defaultFactory,
        @Nullable ConfigMigrator<T> migrator,
        ConfigInfluencer.ConfigSide side
) {
    /**
     * 创建一个双端通用配置类型（{@link ConfigInfluencer.ConfigSide#COMMON}）。
     */
    public static <T> ConfigType<T> of(
            String name,
            Codec<T> codec,
            Function<List<ConfigInfluencer<?>>, T> defaultFactory,
            @Nullable ConfigMigrator<T> migrator
    ) {
        return new ConfigType<>(name, codec, defaultFactory, migrator, ConfigInfluencer.ConfigSide.COMMON);
    }

    /**
     * 创建一个指定所属端的配置类型。
     */
    public static <T> ConfigType<T> of(
            String name,
            Codec<T> codec,
            Function<List<ConfigInfluencer<?>>, T> defaultFactory,
            @Nullable ConfigMigrator<T> migrator,
            ConfigInfluencer.ConfigSide side
    ) {
        return new ConfigType<>(name, codec, defaultFactory, migrator, side);
    }
}