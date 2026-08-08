package org.twcore.api.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;

/**
 * 配置迁移器，用于将旧版本的JSON数据迁移到当前版本的数据对象。
 * @param <T> 配置数据类型
 */
@FunctionalInterface
public interface ConfigMigrator<T> {
    /**
     * 将旧版本的JSON元素迁移为当前版本的数据对象。
     * @param oldJson 旧版本的JSON元素（通常为JsonObject）
     * @param oldVersion 旧版本号
     * @return 迁移结果，成功则包含当前版本的数据对象
     */
    DataResult<T> migrate(JsonElement oldJson, int oldVersion);
}