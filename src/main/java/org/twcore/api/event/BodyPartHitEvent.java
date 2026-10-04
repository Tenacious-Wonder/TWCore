package org.twcore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.twcore.api.bodypart.BodyPartHit;

/**
 * <h1>命中部位事件</h1>
 * <p>
 * 玩家被弹射物击中或被近战攻击时触发，向订阅方给出这次命中的部位，并允许订阅方挡下这次命中。
 * 判定只在服务端进行，事件因此只由服务端触发。
 * </p>
 *
 * <h2>触发时机</h2>
 * <ul>
 *     <li>弹射物：在碰撞处理之前算出命中点并判定部位之后触发。</li>
 *     <li>近战：在伤害结算之前按双方站位推定部位之后触发。</li>
 * </ul>
 * <p>
 * 两者都早于伤害结算，此刻还没有伤害数值。要修改伤害值请订阅 {@link PlayerDamageEvent}。
 * </p>
 *
 * <h2>接入方式</h2>
 * <pre>{@code
 * BodyPartHitEvent.BODY_PART_HIT.register(hit -> hit.part() == BodyPart.HEAD
 *         ? BodyPartHitEvent.Result.block()
 *         : BodyPartHitEvent.Result.pass());
 * }</pre>
 * <p>
 * 返回 {@link Result#block()} 时，弹射物被崩回且不再造成伤害，近战则整次伤害取消；从该结果起，
 * 后续订阅方不再被通知。
 * </p>
 *
 * @see Callback
 * @see Result
 * @see TwEventPhases
 * @see org.twcore.api.bodypart.BodyPartHit
 * @since 1.0.5
 */
public final class BodyPartHitEvent {

    /**
     * 命中部位事件。回调返回 {@link Result}，用于放行或挡下这次命中。
     *
     * @see Callback
     * @since 1.0.5
     */
    public static final Event<Callback> BODY_PART_HIT = EventFactory.createWithPhases(
            Callback.class,
            callbacks -> hit -> {
                for (Callback callback : callbacks) {
                    if (callback.onHit(hit).blocked()) {
                        return Result.block();
                    }
                }
                return Result.pass();
            },
            TwEventPhases.HIGHEST, TwEventPhases.HIGH, TwEventPhases.NORMAL,
            TwEventPhases.LOW, TwEventPhases.LOWEST);

    private BodyPartHitEvent() {
    }

    /**
     * 通知所有订阅方，并给出这次命中是否被挡下。
     *
     * <p>
     * 由 TW Core 在命中判定处调用。订阅方与消费方只需要注册回调，无需调用本方法。
     * </p>
     *
     * @param hit 本次命中的记录
     * @return 被挡下返回 {@code true}
     * @since 1.0.5
     */
    public static boolean isBlocked(BodyPartHit hit) {
        return BODY_PART_HIT.invoker().onHit(hit).blocked();
    }

    /**
     * 命中回调。
     *
     * @see Result
     * @since 1.0.5
     */
    @FunctionalInterface
    public interface Callback {

        /**
         * 处理一次命中。
         *
         * @param hit 本次命中的记录
         * @return 放行返回 {@link Result#pass()}，挡下返回 {@link Result#block()}
         * @since 1.0.5
         */
        Result onHit(BodyPartHit hit);
    }

    /**
     * 一次命中的处理结果。结果不可变，同一个结果可以被反复返回。
     *
     * @see Callback
     * @since 1.0.5
     */
    public static final class Result {

        /**
         * 放行结果，命中照常生效。
         */
        private static final Result PASS = new Result(false);

        /**
         * 挡下结果，命中不生效。
         */
        private static final Result BLOCK = new Result(true);

        private final boolean blocked;

        private Result(boolean blocked) {
            this.blocked = blocked;
        }

        /**
         * 放行本次命中，命中照常造成伤害。
         *
         * @return 放行结果
         * @since 1.0.5
         */
        public static Result pass() {
            return PASS;
        }

        /**
         * 挡下本次命中：弹射物被崩回且不再造成伤害，近战则整次伤害取消。
         *
         * <p>
         * 从该结果起，后续订阅方不再被通知。
         * </p>
         *
         * @return 挡下结果
         * @since 1.0.5
         */
        public static Result block() {
            return BLOCK;
        }

        /**
         * 本次命中是否已被挡下。由事件转发逻辑读取。
         */
        private boolean blocked() {
            return blocked;
        }
    }
}
