package org.twcore.api.util;

import net.minecraft.state.property.IntProperty;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于管理和创建自定义范围的 {@link IntProperty} 属性的工具类。
 *
 * <p>同名同范围的属性若反复创建，会得到互不相等的实例；方块属性又常常需要在构造函数
 * 之前就确定下来。本类针对这两点提供两种机制：</p>
 * <ol>
 *     <li><b>多属性缓存</b> —— 相同名称与范围的属性只创建一次，后续请求返回同一实例；</li>
 *     <li><b>预缓存机制</b> —— 先声明属性范围、稍后再取用，用于绕开方块构造函数中的时序限制。</li>
 * </ol>
 *
 * <h2>常规用法：直接创建</h2>
 * <pre>{@code
 * public class FoodBlock extends Block {
 *     private final IntProperty NUMBER_OF_FOOD = IntPropertyManager.create("number_of_food", 1, 8);
 *
 *     public FoodBlock(Settings settings) {
 *         super(settings);
 *         this.setDefaultState(this.getStateManager().getDefaultState()
 *                 .with(FACING, Direction.NORTH)
 *                 .with(NUMBER_OF_FOOD, 1));
 *     }
 *
 *     @Override
 *     protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
 *         builder.add(FACING, NUMBER_OF_FOOD);
 *     }
 * }
 * }</pre>
 *
 * <h2>预缓存用法：先声明、后取用</h2>
 * <pre>{@code
 * // 注册方块之前先声明属性范围
 * IntPropertyManager.preCache("number_of_food", 1, 12);
 *
 * // 再创建方块实例
 * FoodBlock customFoodBlock = new FoodBlock(FabricBlockSettings.create().hardness(0.5f).resistance(0.5f));
 *
 * // 在 appendProperties 中取用（取走后预缓存自动清空）
 * @Override
 * protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
 *     if (this.NUMBER_OF_FOOD == null) {
 *         this.NUMBER_OF_FOOD = IntPropertyManager.take();
 *     }
 *     builder.add(FACING, NUMBER_OF_FOOD);
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>预缓存每次只能保存一份，新的预缓存会覆盖尚未取用的那一份；</li>
 *     <li>取用预缓存后缓存即被清除，需要重新预缓存才能再次取用；</li>
 *     <li>传入的 {@code min} 与 {@code max} 相等时，{@code min} 会被自动减 1
 *         （属性至少需要两个取值）；</li>
 *     <li>Minecraft 允许重名的不同属性，因此名称相同但范围不同的属性会被视为两个属性。</li>
 * </ul>
 *
 * @see IntProperty
 */
public class IntPropertyManager {
    // 多属性缓存，避免重复创建相同范围的属性
    private static final Map<String, IntProperty> PROPERTY_CACHE = new HashMap<>();

    // 预缓存信息
    private static PendingPropertyInfo pendingInfo = null;

    /**
     * 预缓存一个从 1 到指定最大值的属性信息（不立即创建属性实例）。
     *
     * @param name 属性名称
     * @param max  最大值
     */
    public static void preCache(String name, int max) {
        preCache(name, 1, max);
    }

    /**
     * 预缓存指定范围的属性信息（不立即创建属性实例）。
     *
     * @param name 属性名称
     * @param min  最小值
     * @param max  最大值
     */
    public static void preCache(String name, int min, int max) {
        if (max == min) {
            min -= 1;
        }
        pendingInfo = new PendingPropertyInfo(name, min, max);
    }

    /**
     * 取走预缓存的属性，取走后预缓存即被清除。
     *
     * <p>若缓存中已存在名称与范围相同的属性，则直接返回该实例，不再新建。</p>
     *
     * @return 对应的 {@link IntProperty} 实例
     * @throws IllegalStateException 如果没有预缓存的属性信息
     */
    public static IntProperty take() {
        if (pendingInfo == null) {
            throw new IllegalStateException("No pre-cached property found");
        }

        // 生成缓存键
        String key = generateKey(pendingInfo.name, pendingInfo.min, pendingInfo.max);

        // 检查是否已存在相同范围的属性
        if (PROPERTY_CACHE.containsKey(key)) {
            IntProperty property = PROPERTY_CACHE.get(key);
            clearPending(); // 清除预缓存信息
            return property;
        }

        // 创建新属性并缓存
        IntProperty property = IntProperty.of(pendingInfo.name, pendingInfo.min, pendingInfo.max);
        PROPERTY_CACHE.put(key, property);
        clearPending(); // 清除预缓存信息
        return property;
    }

    /**
     * 直接创建或获取一个从 1 到指定最大值的属性。
     *
     * @param name 属性名称
     * @param max  最大值
     * @return 对应的 {@link IntProperty} 实例
     */
    public static IntProperty create(String name, int max) {
        return create(name, 1, max);
    }

    /**
     * 直接创建或获取一个指定范围的属性。
     *
     * @param name 属性名称
     * @param min  最小值
     * @param max  最大值
     * @return 对应的 {@link IntProperty} 实例
     */
    public static IntProperty create(String name, int min, int max) {
        if (max == min) {
            min -= 1;
        }
        String key = generateKey(name, min, max);

        // 检查是否已存在相同范围的属性
        if (PROPERTY_CACHE.containsKey(key)) {
            return PROPERTY_CACHE.get(key);
        }

        // 创建新属性并缓存
        IntProperty property = IntProperty.of(name, min, max);
        PROPERTY_CACHE.put(key, property);
        return property;
    }

    /**
     * 生成用于缓存的唯一键。
     */
    private static String generateKey(String name, int min, int max) {
        return name + ":" + min + ":" + max;
    }

    /**
     * 清除预缓存信息。
     */
    private static void clearPending() {
        pendingInfo = null;
    }

    /**
     * 检查是否有尚未取用的预缓存属性信息。
     *
     * @return 如果有预缓存的属性信息则返回 {@code true}
     */
    public static boolean hasPending() {
        return pendingInfo != null;
    }

    /**
     * 获取当前预缓存属性信息的文本表示（用于调试）。
     *
     * @return 预缓存的属性信息；如果没有则返回 {@code null}
     */
    public static String getPendingInfo() {
        return pendingInfo != null ? pendingInfo.toString() : null;
    }

    /**
     * 清空所有属性缓存与预缓存信息（主要用于测试或重新加载）。
     */
    public static void clearAll() {
        PROPERTY_CACHE.clear();
        clearPending();
    }

    /**
     * 获取当前属性缓存中的条目数量。
     */
    public static int getCacheSize() {
        return PROPERTY_CACHE.size();
    }

    /**
     * 预缓存中的属性信息。
     */
    private record PendingPropertyInfo(String name, int min, int max) {
        @Override
        public @NotNull String toString() {
            return "PendingPropertyInfo{name='" + name + "', min=" + min + ", max=" + max + "}";
        }
    }
}
