package org.twcore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.util.Identifier;

/**
 * <h1>监听器阶段</h1>
 * <p>
 * 供 TW Core 的事件划分订阅方的执行顺序。注册时指定阶段，同一阶段内按注册顺序执行，阶段之间按本类
 * 常量的声明顺序执行。
 * </p>
 * <p>
 * 不指定阶段等同于 {@link #NORMAL}。阶段只决定执行顺序，不改变回调能做什么。
 * </p>
 *
 * <h2>接入方式</h2>
 * <pre>{@code
 * PlayerDamageEvent.PLAYER_DAMAGE.register(TwEventPhases.LOW, myCallback);
 * }</pre>
 *
 * @see PlayerDamageEvent
 * @see BodyPartHitEvent
 * @since 1.0.5
 */
public final class TwEventPhases {

    /**
     * 最先执行：需要在其他订阅方之前观察或改动时使用。
     */
    public static final Identifier HIGHEST = new Identifier("tw_core", "highest");

    /**
     * 早于默认阶段执行。
     */
    public static final Identifier HIGH = new Identifier("tw_core", "high");

    /**
     * 默认阶段：注册时不指定阶段即使用本阶段。
     */
    public static final Identifier NORMAL = Event.DEFAULT_PHASE;

    /**
     * 晚于默认阶段执行。
     */
    public static final Identifier LOW = new Identifier("tw_core", "low");

    /**
     * 最后执行：用于汇总前序结果或做收尾处理。
     */
    public static final Identifier LOWEST = new Identifier("tw_core", "lowest");

    private TwEventPhases() {
    }
}
