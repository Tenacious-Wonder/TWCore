package org.twcore.environment;

import org.twcore.api.environment.TimePoint;
import java.util.Objects;

/**
 * {@link TimePoint} 的默认实现，基于世界总刻数。
 * <p>
 * 内部缓存衍生值（总天数、日期时间等），多次查询不会重复计算。
 */
public class TimePointImp implements TimePoint {
    static final long TICKS_PER_MINUTE = 16L;
    static final long TICKS_PER_HOUR = 1000L;
    static final long TICKS_PER_DAY = 24000L;
    static final int DAYS_PER_MONTH = 30;
    static final int MONTHS_PER_YEAR = 12;
    static final int DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR; // 360
    static final int SEASON_CYCLE_DAYS = 120;
    static final int DAYS_PER_SEASON = SEASON_CYCLE_DAYS / 4; // 30

    private final long totalTicks;

    private Long totalDays;
    private Long dayTicks;
    private GameDateTime gameDateTime;
    private Season season;
    private Double seasonPhase;

    /**
     * 通过 {@link TimePoint#of(long)} 创建实例。
     */
    public TimePointImp(long totalTicks) {
        this.totalTicks = totalTicks;
    }

    // ==================== 基础查询实现 ====================
    @Override
    public long getTotalTicks() { return totalTicks; }

    @Override
    public long getTotalDays() {
        if (totalDays == null) totalDays = totalTicks / TICKS_PER_DAY;
        return totalDays;
    }

    @Override
    public long getDayTicks() {
        if (dayTicks == null) dayTicks = totalTicks % TICKS_PER_DAY;
        return dayTicks;
    }

    @Override
    public GameDateTime getDateTime() {
        if (gameDateTime == null) gameDateTime = parseDateTime();
        return gameDateTime;
    }

    @Override public long getYear()   { return getDateTime().year(); }
    @Override public int  getMonth()  { return getDateTime().month(); }
    @Override public int  getDay()    { return getDateTime().day(); }
    @Override public int  getHour()   { return getDateTime().hour(); }
    @Override public int  getMinute() { return getDateTime().minute(); }

    @Override
    public int getQuarter() {
        int month = getMonth();
        if (month >= 3 && month <= 5) return 1;
        if (month >= 6 && month <= 8) return 2;
        if (month >= 9 && month <= 11) return 3;
        return 4;
    }

    // ==================== 季节实现 ====================
    @Override
    public Season getSeason() {
        if (season == null) {
            long dayInCycle = getTotalDays() % SEASON_CYCLE_DAYS;
            if (dayInCycle < DAYS_PER_SEASON) season = Season.WINTER;
            else if (dayInCycle < 2 * DAYS_PER_SEASON) season = Season.SPRING;
            else if (dayInCycle < 3 * DAYS_PER_SEASON) season = Season.SUMMER;
            else season = Season.AUTUMN;
        }
        return season;
    }

    @Override
    public double getSeasonPhase() {
        if (seasonPhase == null) {
            double dayInCycle = getTotalDays() % (double) SEASON_CYCLE_DAYS;
            seasonPhase = dayInCycle / SEASON_CYCLE_DAYS * 2.0 * Math.PI;
        }
        return seasonPhase;
    }

    // ==================== 时间运算实现 ====================
    @Override
    public TimePoint addTicks(long ticks) { return new TimePointImp(totalTicks + ticks); }
    @Override
    public TimePoint addDays(long days) { return addTicks(days * TICKS_PER_DAY); }
    @Override
    public TimePoint addMonths(int months) { return addDays((long) months * DAYS_PER_MONTH); }
    @Override
    public TimePoint addYears(int years) { return addDays((long) years * DAYS_PER_YEAR); }
    @Override
    public TimePoint addHours(long hours) { return addTicks(hours * TICKS_PER_HOUR); }
    @Override
    public TimePoint addMinutes(long minutes) { return addTicks(minutes * TICKS_PER_MINUTE); }

    // ==================== 比较与判断 ====================
    @Override
    public int compareTo(TimePoint other) {
        return Long.compare(this.totalTicks, other.getTotalTicks());
    }

    @Override
    public boolean isBefore(TimePoint other) { return compareTo(other) < 0; }
    @Override
    public boolean isAfter(TimePoint other) { return compareTo(other) > 0; }
    @Override
    public boolean isSameDay(TimePoint other) { return getTotalDays() == other.getTotalDays(); }

    @Override
    public boolean isDaytime() {
        long dt = getDayTicks();
        return dt >= 6000 && dt < 18000;
    }
    @Override
    public boolean isNighttime() { return !isDaytime(); }

    // ==================== 格式化 ====================
    @Override
    public String toFormattedString() {
        GameDateTime dt = getDateTime();
        return String.format("Year %d, Month %d, Day %d, %02d:%02d",
                dt.year(), dt.month(), dt.day(), dt.hour(), dt.minute());
    }

    @Override
    public String toString() { return toFormattedString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimePoint that)) return false;
        return totalTicks == that.getTotalTicks();
    }

    @Override
    public int hashCode() { return Objects.hash(totalTicks); }

    // 将 tick 分解为年月日时分
    private GameDateTime parseDateTime() {
        long totalDays = getTotalDays();
        long dayTicks = getDayTicks();

        long year = totalDays / DAYS_PER_YEAR + 1;
        long dayInYear = totalDays % DAYS_PER_YEAR;
        int month = (int) (dayInYear / DAYS_PER_MONTH) + 1;
        int day = (int) (dayInYear % DAYS_PER_MONTH) + 1;
        int hour = (int) (dayTicks / TICKS_PER_HOUR);
        int minute = (int) ((dayTicks % TICKS_PER_HOUR) * 60 / TICKS_PER_HOUR);

        return new GameDateTime(year, month, day, hour, minute);
    }
}