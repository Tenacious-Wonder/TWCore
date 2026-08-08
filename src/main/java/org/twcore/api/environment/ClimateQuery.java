package org.twcore.api.environment;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.twcore.environment.ClimateQueryImpl;

/**
 * 环境查询工具类（API）。
 * <p>
 * 提供静态方法查询基于世界生成的动态温度（℃）与湿度（%），
 * 以及底层的原始噪声值。
 * <p>
 * 温度与湿度计算综合了以下因素：
 * <ul>
 *   <li>世界生成噪声（温度与植被噪声）</li>
 *   <li>季节波动（使用 {@link TimePoint} 获取季节相位）</li>
 *   <li>高度衰减（海拔越高温度越低、越干燥）</li>
 * </ul>
 * 所有逻辑委托给 {@link ClimateQueryImpl} 实现。
 *
 * @since 1.0.3
 */
public class ClimateQuery {

    /**
     * 获取当前位置的实时温度（℃）。
     *
     * @param world 世界对象，仅限服务端（客户端调用返回 0.0）
     * @param pos   目标方块坐标
     * @return 温度值，可能超出 [-20, 50] 的常规范围（例如极端高山冬季）
     */
    public static float getTemperature(World world, BlockPos pos) {
        return ClimateQueryImpl.getTemperature(world, pos);
    }

    /**
     * 获取当前位置的实时湿度（%）。
     *
     * @param world 世界对象，仅限服务端（客户端调用返回 50.0）
     * @param pos   目标方块坐标
     * @return 湿度百分比，通常 0~100，极端环境可能轻微溢出
     */
    public static float getHumidity(World world, BlockPos pos) {
        return ClimateQueryImpl.getHumidity(world, pos);
    }

    /**
     * 获取原始温度噪声值（未叠加季节与高度修正）。
     *
     * @param world 世界对象
     * @param pos   坐标
     * @return 原始温度噪声
     */
    public static float getRawTemperatureNoise(World world, BlockPos pos) {
        return ClimateQueryImpl.getRawTemperatureNoise(world, pos);
    }

    /**
     * 获取原始湿度噪声值（未叠加季节与高度修正）。
     *
     * @param world 世界对象
     * @param pos   坐标
     * @return 原始湿度噪声
     */
    public static float getRawHumidityNoise(World world, BlockPos pos) {
        return ClimateQueryImpl.getRawHumidityNoise(world, pos);
    }
}