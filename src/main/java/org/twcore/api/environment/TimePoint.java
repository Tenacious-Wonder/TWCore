package org.twcore.api.environment;

import net.minecraft.world.World;
import org.twcore.environment.TimePointImp;

/**
 * 一个不可变的游戏时刻，用于查询日历、季节、昼夜等信息。
 * <p>
 * 时刻基于世界总刻数（tick）定义，通过 {@link #of(long)} 从任意刻数创建，
 * 或通过 {@link #fromWorld(World)} 从当前世界创建。
 * 所有时间运算方法均返回新的 {@code TimePoint} 实例，不会修改原对象。
 * <p>
 * 实现了 {@link Comparable}，可按时间先后进行自然排序。
 */
public interface TimePoint extends Comparable<TimePoint> {

    /**
     * 使用世界总刻数创建一个时刻。
     *
     * @param totalTicks 世界总刻数（通常来自 {@code World.getTimeOfDay()}）
     * @return 一个新的 {@code TimePoint} 实例
     */
    static TimePoint of(long totalTicks) {
        return new TimePointImp(totalTicks);
    }

    /**
     * 从当前世界创建对应当前时间的时刻。
     * <p>
     * 等价于 {@code of(world.getTimeOfDay())}。
     *
     * @param world 游戏世界对象
     * @return 对应当前世界时间的 {@code TimePoint}
     */
    static TimePoint fromWorld(World world) {
        return of(world.getTimeOfDay());
    }

    // ==================== 基础时间查询 ====================

    /**
     * @return 该时刻对应的世界总刻数
     */
    long getTotalTicks();

    /**
     * @return 从世界创建起经过的总游戏天数
     */
    long getTotalDays();

    /**
     * @return 当天内的刻数，范围 0 ~ 23999
     */
    long getDayTicks();

    /**
     * @return 完整的日期时间分解（年、月、日、时、分）
     */
    GameDateTime getDateTime();

    /** @return 年份，从 1 开始 */
    long getYear();

    /** @return 月份，1 ~ 12 */
    int getMonth();

    /** @return 日期，1 ~ 30 */
    int getDay();

    /** @return 小时，0 ~ 23 */
    int getHour();

    /** @return 分钟，0 ~ 59 */
    int getMinute();

    /**
     * 返回基于自然月的季度编号。
     * <ul>
     *   <li>1 = 春季（3, 4, 5月）</li>
     *   <li>2 = 夏季（6, 7, 8月）</li>
     *   <li>3 = 秋季（9, 10, 11月）</li>
     *   <li>4 = 冬季（12, 1, 2月）</li>
     * </ul>
     *
     * @return 季度编号
     */
    int getQuarter();

    // ==================== 季节 ====================

    /**
     * @return 当前季节（基于 120 天周期，独立于年月）
     */
    Season getSeason();

    /**
     * 返回当前季节在 120 天周期中的相位（弧度）。
     * <p>
     * 冬季开始相位为 0，春季 π/2，夏季 π，秋季 3π/2。
     *
     * @return 季节相位，范围 [0, 2π)
     */
    double getSeasonPhase();

    // ==================== 时间运算 ====================

    /**
     * 增加指定刻数，返回新时刻。
     * @param ticks 要增加的刻数（可为负）
     * @return 新时刻
     */
    TimePoint addTicks(long ticks);

    /**
     * 增加指定天数。
     * @param days 要增加的天数
     * @return 新时刻
     */
    TimePoint addDays(long days);

    /**
     * 增加指定月数（每月 30 天）。
     * @param months 月数
     * @return 新时刻
     */
    TimePoint addMonths(int months);

    /**
     * 增加指定年数（每年 360 天）。
     * @param years 年数
     * @return 新时刻
     */
    TimePoint addYears(int years);

    /**
     * 增加指定小时数。
     * @param hours 小时数
     * @return 新时刻
     */
    TimePoint addHours(long hours);

    /**
     * 增加指定分钟数（每分钟约 16 刻）。
     * @param minutes 分钟数
     * @return 新时刻
     */
    TimePoint addMinutes(long minutes);

    // ==================== 比较与判断 ====================

    /**
     * 是否早于另一个时刻。
     * @param other 另一个时刻
     * @return true 如果本时刻更早
     */
    boolean isBefore(TimePoint other);

    /**
     * 是否晚于另一个时刻。
     * @param other 另一个时刻
     * @return true 如果本时刻更晚
     */
    boolean isAfter(TimePoint other);

    /**
     * 是否与另一个时刻在同一天（忽略时、分）。
     * @param other 另一个时刻
     * @return true 如果两个时刻在同一天
     */
    boolean isSameDay(TimePoint other);

    /**
     * @return true 如果在白天（06:00 ~ 18:00）
     */
    boolean isDaytime();

    /**
     * @return true 如果在夜晚
     */
    boolean isNighttime();

    // ==================== 格式化 ====================

    /**
     * 返回格式化日期时间字符串，例如 "Year 2, Month 5, Day 12, 06:30"。
     * @return 格式化字符串
     */
    String toFormattedString();

    // ==================== 嵌套类型 ====================

    /** 四季枚举 */
    enum Season {
        WINTER, SPRING, SUMMER, AUTUMN
    }

    /**
     * 日期时间分解记录。
     * @param year   年
     * @param month  月
     * @param day    日
     * @param hour   时
     * @param minute 分
     */
    record GameDateTime(long year, int month, int day, int hour, int minute) {}
}