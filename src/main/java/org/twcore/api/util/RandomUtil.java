package org.twcore.api.util;

import java.util.Random;

/**
 * 随机数工具：为模组提供按概率判定与取随机整数两类常用随机操作。
 */
public class RandomUtil {
    private static final Random RANDOM = new Random();

    /**
     * 按指定概率随机返回 true 或 false。
     *
     * @param probability 返回 {@code true} 的概率，取值范围 [0.0, 1.0]
     * @return 以 probability 的概率返回 {@code true}，否则返回 {@code false}
     * @throws IllegalArgumentException 如果 probability 不在 [0.0, 1.0] 范围内
     */
    public static boolean randomBoolean(float probability) {
        if (probability < 0.0f || probability > 1.0f) {
            throw new IllegalArgumentException("Probability must be between 0.0 and 1.0");
        }

        return RANDOM.nextFloat() < probability;
    }

    /**
     * 取一个小于指定上界的非负随机整数。
     *
     * @param bound 上界（不含），必须为正数
     * @return [0, bound) 范围内的随机整数
     * @throws IllegalArgumentException 如果 {@code bound} 不是正数
     */
    public static int nextInt(int bound) {
        return RANDOM.nextInt(bound);
    }
}
