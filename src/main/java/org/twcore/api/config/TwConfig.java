package org.twcore.api.config;

import org.jetbrains.annotations.Nullable;
import org.twcore.api.TwCoreClientRegistrar;
import org.twcore.api.TwModManager;
import org.twcore.config.ConfigManager;

import java.util.function.Function;

/**
 * <h1>TW Core 配置注册入口</h1>
 * <p>
 * 子模组通过本类完成与配置相关的所有注册操作。
 * 使用 {@link #forMod(String)} 获取与指定模组关联的构造器实例，
 * 然后调用以下方法：
 * <ul>
 *     <li>{@link #registerConfig(ConfigType)} —— 注册双端通用配置
 *         （{@link ConfigInfluencer.ConfigSide#COMMON}）。</li>
 *     <li>{@link #registerClientConfig(ConfigType)} —— 注册仅客户端配置
 *         （{@link ConfigInfluencer.ConfigSide#CLIENT}）。</li>
 *     <li>{@link #addDefaultOverride(String, String, Object)}
 *         —— 向任意已注册或尚未注册的目标配置提供默认值影响器。</li>
 * </ul>
 * </p>
 *
 * <h2>使用前提</h2>
 * <p>
 * 调用 {@link #forMod(String)} 前，指定的模组 ID 必须已通过
 * {@link TwModManager#register(String, int)} 完成注册，否则会抛出
 * {@link IllegalStateException}。这确保配置系统与模组管理器保持数据一致。
 * </p>
 *
 * <h2>跨模组默认值叠加</h2>
 * <p>
 * 通过 {@link #addDefaultOverride(String, String, Object)}
 * 方法，模组可以为其他模组的配置添加影响器。注册阶段仅收集数据，
 * 不检查目标是否存在；所有检查与合并都推迟到配置最终加载时统一执行。
 * 无法匹配到已注册配置的影响器会被静默丢弃，不会产生任何日志或异常。
 * </p>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * // 注册自身
 * TwModManager.IMPL.register("my_mod", 2);
 * TwConfig config = TwConfig.forMod("my_mod");
 *
 * // 注册一个双端通用配置
 * config.registerConfig(ConfigType.of(
 *     "my_settings",
 *     MySettings.CODEC,
 *     influencers -> MySettings.createDefault(),
 *     null
 * ));
 *
 * // 注册一个客户端专属配置
 * config.registerClientConfig(ConfigType.of(
 *     "my_ui",
 *     MyUiConfig.CODEC,
 *     influencers -> MyUiConfig.createDefault(),
 *     null,
 *     ConfigInfluencer.ConfigSide.CLIENT
 * ));
 *
 * // 为其他模组的配置添加默认值
 * config.addDefaultOverride("other_mod", "ore_list",
 *     ConfigInfluencer.create("my_mod", 2, new ExtraOre("ruby")));
 * }</pre>
 *
 * @see TwModManager
 * @see ConfigType
 * @see ConfigInfluencer
 */
public final class TwConfig {
    private final String modId;

    private TwConfig(String modId) {
        this.modId = modId;
    }

    /**
     * 根据模组 ID 和配置名称<b>尝试</b>读取已加载的配置数据。
     * <p>
     * 这是<b>软联动</b>式的宽松读取：仅当指定的配置已加载时才返回数据，
     * 否则返回 {@code null}。适用于"目标配置可能不存在、应被静默跳过"的场景，
     * 例如读取另一个<b>可选集成</b>模组的配置。它不会校验配置是否已注册，
     * 因此无法区分"配置未注册"与"配置未加载"。
     * </p>
     * <p>
     * 若你持有的是 {@link ConfigType}（类型安全、且要求配置<b>必然存在</b>），
     * 请改用 {@link #get(String, ConfigType)} 的权威版本，它会校验注册并在
     * 无法读取时报错。
     * </p>
     *
     * @param modId      配置所属模组的 ID
     * @param configName 配置名称
     * @param <T>        预期的配置数据类型
     * @return 配置数据实例，如果指定配置尚未加载则返回 {@code null}
     * @since 1.0.4
     */
    @Nullable
    public static <T> T get(String modId, String configName) {
        return ConfigManager.get(modId, configName);
    }

    /**
     * 根据模组 ID 和 {@link ConfigType} 权威化获取已加载的配置数据。
     * <p>
     * 与 {@link #get(String, String)} 的宽松语义不同，本方法要求配置<b>必然</b>
     * 可被读取。{@link ConfigType} 携带了完整注册元信息，因此本方法会校验该配置
     * 类型是否已注册并<b>保证返回非空数据</b>：未注册、或已注册但尚未加载
     * （双端配置在通用注册完成后加载，客户端配置要到客户端注册完成后才加载）
     * 都会抛出异常，而不是静默返回 {@code null}。这能及早暴露配置注册遗漏或
     * 加载时序错误，避免调用方在错误时机拿到空数据。
     * </p>
     *
     * @param modId 配置所属模组的 ID
     * @param type  配置的元信息（既用于查找配置，也用于校验注册）
     * @param <T>   配置数据类型，与 {@code type} 的泛型参数一致
     * @return 已加载的配置数据实例，永不为 {@code null}
     * @throws IllegalStateException 如果配置类型未注册，或已注册但尚未加载
     * @since 1.0.4
     */
    public static <T> T get(String modId, ConfigType<T> type) {
        return ConfigManager.getRequired(modId, type);
    }

    /**
     * 安全更新配置数据，修改后自动持久化到文件。
     * <p>
     * 接收一个函数，将当前配置数据转换为更新后的数据。
     * 保存时会重新计算版本号，保证文件与当前有效版本一致。
     * </p>
     *
     * @param modId      配置所属模组的 ID
     * @param configName 配置名称
     * @param updater    接收旧数据并返回新数据的函数
     * @param <T>        配置数据类型
     * @throws IllegalStateException 如果指定配置尚未加载
     */
    public static <T> void update(String modId, String configName, Function<T, T> updater) {
        ConfigManager.update(modId, configName, updater);
    }

    /**
     * 获取指定模组的配置构造器。
     *
     * @param modId 模组 ID，必须已通过 {@link TwModManager#register(String, int)} 注册
     * @return 该模组的配置构造器
     * @throws IllegalStateException 如果指定模组尚未在 {@link TwModManager} 中注册
     */
    public static TwConfig forMod(String modId) {
        if (!TwModManager.IMPL.isRegistered(modId)) {
            throw new IllegalStateException(
                    "Mod '" + modId + "' is not registered in TW Mod Manager. " +
                            "Please call TwModManager.IMPL.register() first."
            );
        }
        return new TwConfig(modId);
    }

    /**
     * 注册一个双端通用配置。
     * 配置将在通用注册完成后统一加载。
     *
     * @param type 配置元信息，其 {@code side} 必须为 {@link ConfigInfluencer.ConfigSide#COMMON}
     * @param <T>  配置数据类型
     * @throws IllegalArgumentException 如果配置类型不是 {@code COMMON}
     */
    public <T> void registerConfig(ConfigType<T> type) {
        if (type.side() != ConfigInfluencer.ConfigSide.COMMON) {
            throw new IllegalArgumentException(
                    "Config '" + type.name() + "' is marked as " + type.side() +
                            ". Use registerClientConfig() for CLIENT configs."
            );
        }
        ConfigManager.registerConfig(modId, type);
    }

    /**
     * 注册一个客户端专属配置。
     * 仅在物理客户端生效，将在客户端注册完成后统一加载。
     * <p>此方法应在 {@link TwCoreClientRegistrar#registerClient()} 中调用。</p>
     *
     * @param type 配置元信息，其 {@code side} 必须为 {@link ConfigInfluencer.ConfigSide#CLIENT}
     * @param <T>  配置数据类型
     * @throws IllegalArgumentException 如果配置类型不是 {@code CLIENT}
     */
    public <T> void registerClientConfig(ConfigType<T> type) {
        if (type.side() != ConfigInfluencer.ConfigSide.CLIENT) {
            throw new IllegalArgumentException(
                    "Config '" + type.name() + "' is marked as " + type.side() +
                            ". Use registerConfig() for COMMON configs."
            );
        }
        ConfigManager.registerConfig(modId, type);
    }

    /**
     * 为目标配置添加一个默认值影响器。
     * <p>
     * 传入任意类型的 payload 数据，本方法会自动封装来源模组信息
     * （当前 {@code modId} 及从 {@link TwModManager} 获取的 API 等级）
     * 为一个 {@link ConfigInfluencer} 并提交。
     * </p>
     * <p>
     * 注意：{@code payload} 的类型必须是两个模组都能访问到的公共类型
     * （例如原版 Minecraft 的类或 TW Core API 中定义的类型），
     * 目标模组在默认值工厂中通过 {@code instanceof} 安全提取。
     * </p>
     *
     * @param targetModId 目标配置所属模组的 ID
     * @param configName  目标配置的名称
     * @param payload     影响器携带的数据，类型自动推断
     * @param <T>         载荷类型
     */
    public <T> void addDefaultOverride(String targetModId, String configName, T payload) {
        int version = TwModManager.IMPL.getRegisteredVersion(modId);
        ConfigInfluencer<T> influencer = new ConfigInfluencer<>() {
            @Override
            public String sourceModId() {
                return modId;
            }

            @Override
            public int sourceModVersion() {
                return version;
            }

            @Override
            public T payload() {
                return payload;
            }
        };
        ConfigManager.addInfluencer(targetModId, configName, influencer);
    }
}
