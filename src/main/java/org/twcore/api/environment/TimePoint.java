package org.twcore.api.environment;

import net.minecraft.world.World;
import org.twcore.environment.TimePointImp;

/**
 * <h1>游戏时刻</h1>
 * <p>
 * 模组常常需要按“游戏内的时间”做判断——几点天亮、现在是哪个季节、距离某个时节还有几天。
 * 本接口是这类判断的统一入口：把世界总刻数包装成一个<b>不可变的时刻对象</b>，
 * 在此之上提供日历、季节与昼夜的查询。
 * </p>
 *
 * <h2>创建与运算</h2>
 * <ul>
 *     <li><b>创建</b>：通过 {@link #of(long)} 从任意刻数创建，或通过 {@link #fromWorld(World)}
 *         取当前世界时间；</li>
 *     <li><b>运算</b>：{@code addTicks} / {@code addDays} / {@code addMonths} / {@code addYears} /
 *         {@code addHours} / {@code addMinutes} 都是<b>不可变运算</b>，返回新的时刻实例，
 *         不修改原对象；</li>
 *     <li><b>比较</b>：实现 {@link Comparable}，并提供 {@link #isBefore(TimePoint)}、
 *         {@link #isAfter(TimePoint)}、{@link #isSameDay(TimePoint)} 等判断。</li>
 * </ul>
 *
 * <h2>历法约定</h2>
 * <ul>
 *     <li>一年 12 个月、每月 30 天，共 360 天；</li>
 *     <li>四季按<b>独立的 120 天周期</b>循环，与年月无关（见 {@link #getSeasonPhase()}）。</li>
 * </ul>
 *
 * @since 1.0.4
 * @see ClimateQuery
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
     *
     * <p>等价于 {@code of(world.getTimeOfDay())}。</p>
     *
     * @param world 游戏世界对象
     * @return 对应当前世界时间的 {@code TimePoint}
     */
    static TimePoint fromWorld(World world) {
        return of(world.getTimeOfDay());
    }

    // ==================== 基础时间查询 ====================

    /**
     * 获取该时刻对应的世界总刻数。
     *
     * @return 该时刻对应的世界总刻数
     */
    long getTotalTicks();

    /**
     * 获取自世界创建以来经过的游戏天数。
     *
     * @return 从世界创建起经过的总游戏天数
     */
    long getTotalDays();

    /**
     * 获取该时刻在当天内的刻数。
     *
     * @return 当天内的刻数，范围 0 ~ 23999
     */
    long getDayTicks();

    /**
     * 获取完整的日期时间分解。
     *
     * @return 完整的日期时间分解（年、月、日、时、分）
     */
    GameDateTime getDateTime();

    /**
     * 获取年份。
     *
     * @return 年份，从 1 开始
     */
    long getYear();

    /**
     * 获取月份。
     *
     * @return 月份，1 ~ 12
     */
    int getMonth();

    /**
     * 获取日期。
     *
     * @return 日期，1 ~ 30
     */
    int getDay();

    /**
     * 获取小时。
     *
     * @return 小时，0 ~ 23
     */
    int getHour();

    /**
     * 获取分钟。
     *
     * @return 分钟，0 ~ 59
     */
    int getMinute();

    /**
     * 获取基于自然月的季度编号。
     * <ul>
     *   <li>1 = 春季（3、4、5 月）</li>
     *   <li>2 = 夏季（6、7、8 月）</li>
     *   <li>3 = 秋季（9、10、11 月）</li>
     *   <li>4 = 冬季（12、1、2 月）</li>
     * </ul>
     *
     * @return 季度编号
     */
    int getQuarter();

    // ==================== 季节 ====================

    /**
     * 获取当前季节。
     *
     * @return 当前季节（基于 120 天周期，独立于年月）
     */
    Season getSeason();

    /**
     * 获取当前季节在 120 天周期中的相位（弧度）。
     *
     * <p>冬季开始相位为 0，春季 π/2，夏季 π，秋季 3π/2。</p>
     *
     * @return 季节相位，范围 [0, 2π)
     */
    double getSeasonPhase();

    // ==================== 时间运算 ====================

    /**
     * 增加指定刻数，返回新时刻。
     *
     * @param ticks 要增加的刻数（可为负）
     * @return 新时刻
     */
    TimePoint addTicks(long ticks);

    /**
     * 增加指定天数，返回新时刻。
     *
     * @param days 要增加的天数
     * @return 新时刻
     */
    TimePoint addDays(long days);

    /**
     * 增加指定月数（每月 30 天），返回新时刻。
     *
     * @param months 月数
     * @return 新时刻
     */
    TimePoint addMonths(int months);

    /**
     * 增加指定年数（每年 360 天），返回新时刻。
     *
     * @param years 年数
     * @return 新时刻
     */
    TimePoint addYears(int years);

    /**
     * 增加指定小时数，返回新时刻。
     *
     * @param hours 小时数
     * @return 新时刻
     */
    TimePoint addHours(long hours);

    /**
     * 增加指定分钟数（每分钟约 16 刻），返回新时刻。
     *
     * @param minutes 分钟数
     * @return 新时刻
     */
    TimePoint addMinutes(long minutes);

    // ==================== 比较与判断 ====================

    /**
     * 判断本时刻是否早于另一个时刻。
     *
     * @param other 另一个时刻
     * @return 如果本时刻更早则返回 true
     */
    boolean isBefore(TimePoint other);

    /**
     * 判断本时刻是否晚于另一个时刻。
     *
     * @param other 另一个时刻
     * @return 如果本时刻更晚则返回 true
     */
    boolean isAfter(TimePoint other);

    /**
     * 判断两个时刻是否在同一天（忽略时、分）。
     *
     * @param other 另一个时刻
     * @return 如果两个时刻在同一天则返回 true
     */
    boolean isSameDay(TimePoint other);

    /**
     * 判断该时刻是否处于白天。
     *
     * @return 如果在白天（06:00 ~ 18:00）则返回 true
     */
    boolean isDaytime();

    /**
     * 判断该时刻是否处于夜晚。
     *
     * @return 如果在夜晚则返回 true
     */
    boolean isNighttime();

    // ==================== 格式化 ====================

    /**
     * 格式化为便于阅读的日期时间字符串。
     *
     * @return 格式化字符串，例如“Year 2, Month 5, Day 12, 06:30”
     */
    String toFormattedString();

    // ==================== 嵌套类型 ====================

    /** 季节：按 120 天周期循环的四季。 */
    enum Season {
        WINTER, SPRING, SUMMER, AUTUMN
    }

    /**
     * 日期时间分解记录。
     *
     * @param year   年
     * @param month  月
     * @param day    日
     * @param hour   时
     * @param minute 分
     */
    record GameDateTime(long year, int month, int day, int hour, int minute) {}
}
