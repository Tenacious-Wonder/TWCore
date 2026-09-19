package org.twcore.api.config;

/**
 * <h1>配置默认值影响器</h1>
 * <p>
 * 由某个模组提供，用于修改另一个模组配置的最终默认值。
 * 携带一个类型安全的 {@code payload}，其类型 {@code T} 必须是
 * 双方模组都能访问到的类（如原版 Minecraft 类型或 TW Core API 中的类）。
 * </p>
 *
 * @param <T> 影响器携带的数据类型，目标模组可以安全地将其转换为该类型使用
 */
public interface ConfigInfluencer<T> {

    /**
     * 获取提交此影响器的来源模组。
     *
     * @return 影响器来源模组的 ID
     */
    String sourceModId();

    /**
     * 获取来源模组注册的 API 等级。
     *
     * <p>用于累加计算配置文件版本号，从而判断目标配置是否需要迁移。</p>
     *
     * @return 来源模组的 API 等级
     */
    int sourceModVersion();

    /**
     * 获取影响器携带的载荷数据。
     *
     * @return 类型安全的载荷数据，由来源模组提供，目标模组自行解析
     */
    T payload();

    /**
     * 配置所属的物理端：标记一个配置是双端通用（{@link #COMMON}）
     * 还是仅客户端（{@link #CLIENT}）。
     *
     * <h2>加载行为</h2>
     * <ul>
     *     <li>{@code COMMON} —— 在双端注册完成后加载，服务端与客户端都生效。</li>
     *     <li>{@code CLIENT} —— 仅在物理客户端、客户端专属注册完成后加载；
     *         在服务端该配置会被完全忽略。</li>
     * </ul>
     *
     * <p>在 {@link ConfigType} 中通过 {@code side()} 字段指定，默认为 {@code COMMON}。</p>
     *
     * @see ConfigType
     */
    enum ConfigSide {
        /** 双端通用配置，服务端和客户端都会加载 */
        COMMON,
        /** 仅客户端配置，只在物理客户端加载 */
        CLIENT
    }
}