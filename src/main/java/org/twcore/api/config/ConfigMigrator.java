package org.twcore.api.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;

/**
 * 配置迁移器：把旧版本的 JSON 数据迁移为当前版本的数据对象。
 *
 * <p>由 {@link ConfigType} 携带，在加载到旧版本的配置文件时被调用，
 * 使旧存档的数据结构能与新版本对接。返回的迁移结果既可能成功，
 * 也可能携带错误信息（例如旧数据无法被当前编解码器解析）。</p>
 *
 * @param <T> 配置数据类型
 */
@FunctionalInterface
public interface ConfigMigrator<T> {

    /**
     * 将旧版本的 JSON 元素迁移为当前版本的数据对象。
     *
     * @param oldJson    旧版本的 JSON 元素（通常为 {@code JsonObject}）
     * @param oldVersion 旧版本号
     * @return 迁移结果：成功时包含当前版本的数据对象，失败时包含错误信息
     */
    DataResult<T> migrate(JsonElement oldJson, int oldVersion);
}