package org.twcore.api.environment;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.twcore.environment.ClimateQueryImpl;

/**
 * <h1>环境查询</h1>
 * <p>
 * 方块与环境机制常常需要知道“这里有多热、多湿”——作物能否生长、食物如何变质、
 * 积雪是否融化，都取决于当地的气候。本类提供查询<b>基于世界生成的动态温度（℃）
 * 与湿度（%）</b>的静态入口，以及供自定义计算使用的底层原始噪声值。
 * </p>
 *
 * <h2>数值来源</h2>
 * <p>
 * 温度与湿度综合了以下因素：
 * </p>
 * <ul>
 *     <li>世界生成噪声（温度与植被噪声）；</li>
 *     <li>季节波动（使用 {@link TimePoint} 获取季节相位）；</li>
 *     <li>高度衰减（海拔越高温度越低、越干燥）。</li>
 * </ul>
 *
 * <h2>接入方式</h2>
 * <p>
 * 直接在服务端逻辑中调用静态方法即可，无需注册或初始化：
 * </p>
 * <pre>{@code
 * float temperature = ClimateQuery.getTemperature(world, pos);
 * float humidity = ClimateQuery.getHumidity(world, pos);
 * }</pre>
 * <p>
 * 客户端调用一律返回中性值（温度 0.0、湿度 50.0），因此气候判定应放在服务端进行。
 * </p>
 *
 * @since 1.0.4
 * @see TimePoint
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