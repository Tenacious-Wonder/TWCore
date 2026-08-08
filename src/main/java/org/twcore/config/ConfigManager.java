package org.twcore.config;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.TwModManager;
import org.twcore.api.config.ConfigInfluencer;
import org.twcore.api.config.ConfigType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * <h1>TW Core 统一配置管理器</h1>
 * <p>
 * 负责所有配置的注册、影响器收集、分端加载与文件持久化。
 * </p>
 *
 * <h2>配置文件路径</h2>
 * <p>
 * 所有配置文件存放在 {@code config/tenacious-wonder/<modId>/<configName>.json}。
 * 例如，模组 {@code a} 注册了 {@code ore} 配置，则文件位于
 * {@code config/tenacious-wonder/a/ore.json}。
 * </p>
 *
 * <h2>分端加载</h2>
 * <p>
 * 配置根据 {@link ConfigInfluencer.ConfigSide} 分为两类：
 * <ul>
 *   <li>{@link ConfigInfluencer.ConfigSide#COMMON} —— 双端通用配置，在双端注册完成后立即加载。</li>
 *   <li>{@link ConfigInfluencer.ConfigSide#CLIENT} —— 仅客户端配置，在客户端专属注册完成后加载。</li>
 * </ul>
 * 外部需要分别在对应时机调用 {@link #loadCommon()} 和 {@link #loadClient()}。
 * {@code loadClient()} 仅在物理客户端调用。
 * </p>
 *
 * <h2>影响器与注册顺序</h2>
 * <p>
 * 其他模组可通过 {@code TwConfig.addDefaultOverride()} 向某个配置添加
 * {@link ConfigInfluencer 影响器}。注册阶段<b>纯粹收集数据，不进行任何检查</b>。
 * 所有影响器暂存于内部池中。当配置被实际加载时，才从池中取出属于该配置的
 * 所有影响器，并筛选出来源模组已在 {@link TwModManager} 中注册的有效影响器，
 * 传递给默认值工厂生成最终默认值。无法匹配到已注册配置的影响器会被静默丢弃，
 * 不产生任何日志。
 * </p>
 *
 * <h2>错误处理</h2>
 * <p>
 * 配置文件加载过程中的任何异常（JSON 格式错误、Codec 解析失败、版本迁移失败等）
 * 都会导致当前文件被<b>立即使用最终默认值覆写</b>，并保存为当前有效版本。
 * </p>
 *
 * <h2>线程安全</h2>
 * <p>
 * 所有注册与加载方法使用内部同步锁，保证在模组加载阶段的单线程顺序调用安全。
 * 运行时数据缓存使用 {@link ConcurrentHashMap}，支持并发读取。
 * </p>
 *
 * @see ConfigType
 * @see ConfigInfluencer
 * @see ConfigInfluencer.ConfigSide
 */
public final class ConfigManager {
    private static final Logger LOGGER = TWCore.LOGGER;
    private static final Path BASE_DIR = FabricLoader.getInstance().getConfigDir().resolve("tenacious-wonder");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 内部键，标识一个配置 (modId, configName)
    private record ModConfigKey(String modId, String configName) {}
    // 内部条目，存储配置元信息
    private record ConfigEntry<T>(ConfigType<T> type) {}

    // 已注册的配置定义
    private static final Map<ModConfigKey, ConfigEntry<?>> entries = new LinkedHashMap<>();
    // 运行时数据缓存，加载后存放
    private static final Map<ModConfigKey, Object> dataCache = new ConcurrentHashMap<>();
    // 影响器待处理池：目标配置键 → 影响器列表
    private static final Map<ModConfigKey, List<ConfigInfluencer<?>>> pendingInfluencers = new HashMap<>();

    private ConfigManager() {}

    // ========== 注册阶段 API（仅收集，不检查） ==========

    /**
     * 注册一个配置类型。必须在所有注册器调用阶段调用。
     *
     * @param modId 配置所属模组 ID
     * @param type  配置元信息
     * @throws IllegalArgumentException 如果相同 (modId, name) 已注册
     */
    public static synchronized void registerConfig(String modId, ConfigType<?> type) {
        ModConfigKey key = new ModConfigKey(modId, type.name());
        if (entries.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate config registration: " + key);
        }
        entries.put(key, new ConfigEntry<>(type));
    }

    /**
     * 为目标配置添加一个默认值影响器。
     * 注册阶段不做任何检查，直接存入待处理池。目标配置可能尚未注册，也可能永远不会注册。
     * 最终加载时，若目标不存在则静默丢弃。
     *
     * @param targetModId 目标配置所属模组 ID
     * @param configName  目标配置名称
     * @param influencer  影响器
     */
    public static synchronized void addInfluencer(String targetModId, String configName, ConfigInfluencer<?> influencer) {
        ModConfigKey key = new ModConfigKey(targetModId, configName);
        pendingInfluencers.computeIfAbsent(key, k -> new ArrayList<>()).add(influencer);
    }

    // ========== 分端加载阶段 ==========

    /**
     * 加载所有 {@link ConfigInfluencer.ConfigSide#COMMON} 配置。
     * 应在双端通用注册全部完成后调用。
     */
    public static synchronized void loadCommon() {
        loadBySide(ConfigInfluencer.ConfigSide.COMMON);
    }

    /**
     * 加载所有 {@link ConfigInfluencer.ConfigSide#CLIENT} 配置。
     * 仅在物理客户端调用，应在客户端专属注册完成后执行。
     */
    public static synchronized void loadClient() {
        loadBySide(ConfigInfluencer.ConfigSide.CLIENT);
    }

    /**
     * 遍历已注册条目，加载指定端的所有配置。
     */
    private static void loadBySide(ConfigInfluencer.ConfigSide targetSide) {
        if (entries.isEmpty()) return;
        try {
            Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create base config directory", e);
            return;
        }

        for (Map.Entry<ModConfigKey, ConfigEntry<?>> mapEntry : entries.entrySet()) {
            ModConfigKey key = mapEntry.getKey();
            ConfigEntry<?> entry = mapEntry.getValue();
            if (entry.type().side() == targetSide) {
                loadSingleConfig(key, entry);
            }
        }
    }

    /**
     * 加载单个配置：处理影响器、计算版本、读取文件、应用默认值并保存。
     */
    private static <T> void loadSingleConfig(ModConfigKey key, ConfigEntry<T> entry) {
        ConfigType<T> type = entry.type;
        Path file = BASE_DIR.resolve(key.modId()).resolve(key.configName() + ".json");

        // 1. 从待处理池取出当前配置的所有影响器（可能为空）
        List<ConfigInfluencer<?>> rawInfluencers = pendingInfluencers.getOrDefault(key, Collections.emptyList());

        // 2. 筛选有效影响器：来源模组必须已在 TwModManager 中注册
        List<ConfigInfluencer<?>> validInfluencers = rawInfluencers.stream()
                .filter(inf -> TwModManager.IMPL.isRegistered(inf.sourceModId()))
                .toList();

        // 3. 计算有效版本号 = 来源模组版本 + 所有有效影响器来源模组版本之和
        int sourceVersion = TwModManager.IMPL.getRegisteredVersion(key.modId());
        int influencerVersionSum = validInfluencers.stream()
                .mapToInt(ConfigInfluencer::sourceModVersion)
                .sum();
        int effectiveVersion = sourceVersion + influencerVersionSum;

        // 4. 调用默认值工厂生成最终默认值
        T finalDefault;
        try {
            finalDefault = type.defaultFactory().apply(validInfluencers);
        } catch (Exception e) {
            LOGGER.error("Default factory for config '{}' threw exception, falling back to factory with empty list", key, e);
            try {
                finalDefault = type.defaultFactory().apply(Collections.emptyList());
            } catch (Exception ex) {
                LOGGER.error("Critical: default factory for '{}' failed even with empty list", key, ex);
                return; // 无法生成默认值，放弃加载此配置
            }
        }

        // 5. 尝试从文件加载数据
        T data = null;
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement json = JsonParser.parseString(content);
                if (!json.isJsonObject()) {
                    throw new IllegalStateException("Config file is not a JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                int fileVersion = obj.has("version") ? obj.get("version").getAsInt() : 0;
                obj.remove("version");

                if (fileVersion == effectiveVersion) {
                    // 版本匹配，直接解析
                    DataResult<T> result = type.codec().parse(JsonOps.INSTANCE, obj);
                    Optional<T> parsed = result.resultOrPartial(error -> {
                        LOGGER.error("Failed to parse config '{}': {}", key, error);
                    });
                    if (parsed.isPresent()) {
                        data = parsed.get();
                    }
                } else if (fileVersion < effectiveVersion && type.migrator() != null) {
                    // 旧版本，尝试迁移
                    LOGGER.info("Migrating config '{}' from version {} to {}", key, fileVersion, effectiveVersion);
                    DataResult<T> migrated = type.migrator().migrate(obj, fileVersion);
                    Optional<T> result = migrated.resultOrPartial(error ->
                            LOGGER.error("Migration failed for '{}': {}", key, error));
                    if (result.isPresent()) {
                        data = result.get();
                    }
                } else {
                    LOGGER.info("Config '{}' version mismatch (file: {}, current: {}), using default", key, fileVersion, effectiveVersion);
                }
            } catch (Exception e) {
                LOGGER.error("Error reading/parsing config file '{}', will override with default", file, e);
            }
        }

        // 6. 若加载失败（data 仍为 null），使用最终默认值覆写
        if (data == null) {
            data = finalDefault;
        }

        // 7. 存入缓存并保存文件（确保文件版本与当前有效版本一致）
        dataCache.put(key, data);
        saveConfig(key, data, effectiveVersion);
    }

    // ========== 持久化 ==========

    @SuppressWarnings("unchecked")
    private static <T> void saveConfig(ModConfigKey key, T data, int version) {
        Path file = BASE_DIR.resolve(key.modId()).resolve(key.configName() + ".json");
        try {
            Files.createDirectories(file.getParent());
            ConfigEntry<?> entry = entries.get(key);
            if (entry == null) return;
            Codec<T> codec = (Codec<T>) entry.type.codec();

            JsonElement json = codec.encodeStart(JsonOps.INSTANCE, data)
                    .getOrThrow(false, s -> LOGGER.error("Failed to encode config '{}': {}", key, s));
            JsonObject obj = ensureJsonObject(json);
            obj.addProperty("version", version);
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
            LOGGER.debug("Config '{}' saved (version {})", key, version);
        } catch (Exception e) {
            LOGGER.error("Failed to save config '{}'", key, e);
        }
    }

    // ========== 数据访问 ==========

    /**
     * 获取已加载的配置数据。
     *
     * @param modId      配置所属模组 ID
     * @param configName 配置名称
     * @param <T>        预期数据类型
     * @return 配置数据，若未加载则返回 {@code null}
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T get(String modId, String configName) {
        return (T) dataCache.get(new ModConfigKey(modId, configName));
    }

    /**
     * 更新配置，修改后自动保存到文件。
     *
     * @param modId      配置所属模组 ID
     * @param configName 配置名称
     * @param updater    接收旧数据并返回新数据的函数
     * @param <T>        配置数据类型
     * @throws IllegalStateException 如果指定配置尚未加载
     */
    public static <T> void update(String modId, String configName, Function<T, T> updater) {
        ModConfigKey key = new ModConfigKey(modId, configName);
        T old = get(modId, configName);
        if (old == null) {
            throw new IllegalStateException("Config not loaded: " + key);
        }
        T newData = updater.apply(old);
        dataCache.put(key, newData);

        // 重新计算版本并保存
        int sourceVersion = TwModManager.IMPL.getRegisteredVersion(modId);
        int influencerSum = pendingInfluencers.getOrDefault(key, Collections.emptyList()).stream()
                .filter(inf -> TwModManager.IMPL.isRegistered(inf.sourceModId()))
                .mapToInt(ConfigInfluencer::sourceModVersion)
                .sum();
        saveConfig(key, newData, sourceVersion + influencerSum);
    }

    // ========== 工具方法 ==========

    private static JsonObject ensureJsonObject(JsonElement json) {
        if (json.isJsonObject()) {
            return json.getAsJsonObject();
        } else {
            JsonObject wrapper = new JsonObject();
            wrapper.add("value", json);
            return wrapper;
        }
    }
}