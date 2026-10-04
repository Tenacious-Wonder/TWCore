package org.twcore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.bodypart.BodyPart;
import org.twcore.api.bodypart.BodyPartHit;

/**
 * <h1>玩家伤害结算事件</h1>
 * <p>
 * 服务端玩家即将承受伤害时触发，向订阅方给出本次结算的伤害值与相关上下文，并允许订阅方改写这个
 * 数值。触发点在护甲、附魔与状态效果结算之后、吸收抵扣之前：订阅方看到的是玩家实际承受的伤害，
 * 减伤也不会被吸收重复抵扣。
 * </p>
 *
 * <h2>触发范围</h2>
 * <p>
 * 对玩家的每一次有效伤害都触发，不限于命中部位的伤害。{@link Context#part()} 只在本次伤害由一次
 * 命中产生时非空（例如箭矢、近战），药水、摔落、爆炸等为 {@code null}。
 * </p>
 *
 * <h2>接入方式</h2>
 * <pre>{@code
 * PlayerDamageEvent.PLAYER_DAMAGE.register((context, currentDamage) ->
 *         context.part() == BodyPart.CHEST
 *                 ? PlayerDamageEvent.Result.adjust(-2.0F)
 *                 : PlayerDamageEvent.Result.keep());
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>最终伤害值不小于 0：无论订阅方返回什么，结果都会被夹到非负。</li>
 *     <li>返回 {@link Result#cancel()} 时，后续订阅方不再被通知。</li>
 *     <li>只在服务端触发，客户端不产生该事件。</li>
 * </ul>
 *
 * @see Modifier
 * @see Context
 * @see Result
 * @see TwEventPhases
 * @see BodyPartHitEvent
 * @since 1.0.5
 */
public final class PlayerDamageEvent {

    /**
     * 玩家伤害结算事件。回调返回 {@link Result}，用于改写本次结算使用的伤害值。
     *
     * @see Modifier
     * @since 1.0.5
     */
    public static final Event<Modifier> PLAYER_DAMAGE = EventFactory.createWithPhases(
            Modifier.class,
            callbacks -> (context, currentDamage) -> {
                float amount = currentDamage;
                for (Modifier callback : callbacks) {
                    Result result = callback.modify(context, amount);
                    if (result.stopsChain()) {
                        return Result.set(0.0F);
                    }
                    amount = result.applyTo(amount);
                }
                return Result.set(Math.max(0.0F, amount));
            },
            TwEventPhases.HIGHEST, TwEventPhases.HIGH, TwEventPhases.NORMAL,
            TwEventPhases.LOW, TwEventPhases.LOWEST);

    private PlayerDamageEvent() {
    }

    /**
     * 依次应用所有订阅方返回的结果，算出本次结算使用的伤害值。
     *
     * <p>
     * 由 TW Core 在伤害结算处调用。订阅方与消费方只需要注册回调，无需调用本方法。
     * </p>
     *
     * @param context 本次结算的只读信息
     * @param amount  护甲与状态效果结算之后的伤害值
     * @return 应用全部订阅方修改之后的伤害值，不小于 0
     * @since 1.0.5
     */
    public static float resolveDamage(Context context, float amount) {
        return PLAYER_DAMAGE.invoker().modify(context, amount).applyTo(amount);
    }

    /**
     * 伤害结算回调。
     *
     * @see Result
     * @since 1.0.5
     */
    @FunctionalInterface
    public interface Modifier {

        /**
         * 决定本次结算使用的伤害值。
         *
         * @param context       本次结算的只读信息
         * @param currentDamage 经前序订阅方处理后的伤害值
         * @return 修改结果；不需要改动时返回 {@link Result#keep()}
         * @since 1.0.5
         */
        Result modify(Context context, float currentDamage);
    }

    /**
     * 一次伤害结算的只读信息。
     *
     * @param player 受伤的玩家
     * @param source 本次伤害的来源
     * @param part   本次伤害命中的部位；没有命中部位的伤害为 {@code null}
     * @param hit    本次伤害对应的命中记录；没有命中部位的伤害为 {@code null}
     * @see Modifier
     * @since 1.0.5
     */
    public record Context(ServerPlayerEntity player, DamageSource source, @Nullable BodyPart part,
                          @Nullable BodyPartHit hit) {
    }

    /**
     * 一次伤害结算的修改结果。结果不可变，同一个结果可以被反复返回。
     *
     * <p>
     * 修改按订阅顺序逐层应用：每个订阅方拿到的当前值都是前一个订阅方处理后的结果。
     * </p>
     *
     * @see Modifier
     * @since 1.0.5
     */
    public static final class Result {

        /**
         * 保持当前值不变。
         */
        private static final Result KEEP = new Result(Kind.KEEP, 0.0F);

        /**
         * 归零并终止后续订阅。
         */
        private static final Result CANCEL = new Result(Kind.CANCEL, 0.0F);

        private final Kind kind;
        private final float value;

        private Result(Kind kind, float value) {
            this.kind = kind;
            this.value = value;
        }

        /**
         * 保持当前伤害值不变。
         *
         * @return 不作修改的结果
         * @since 1.0.5
         */
        public static Result keep() {
            return KEEP;
        }

        /**
         * 无视当前值，直接指定本次结算使用的伤害值。
         *
         * @param amount 本次结算使用的伤害值
         * @return 指定数值的结果
         * @since 1.0.5
         */
        public static Result set(float amount) {
            return new Result(Kind.SET, amount);
        }

        /**
         * 在当前值上增减。
         *
         * @param delta 增减量，负数为减伤
         * @return 增减后的结果
         * @since 1.0.5
         */
        public static Result adjust(float delta) {
            return new Result(Kind.ADJUST, delta);
        }

        /**
         * 按倍率缩放当前值。
         *
         * @param factor 倍率
         * @return 缩放后的结果
         * @since 1.0.5
         */
        public static Result scale(float factor) {
            return new Result(Kind.SCALE, factor);
        }

        /**
         * 将本次伤害归零。
         *
         * <p>
         * 与 {@code set(0)} 的区别在于后续订阅方不再被通知。
         * </p>
         *
         * @return 归零并按 {@code 0} 结算的结果
         * @since 1.0.5
         */
        public static Result cancel() {
            return CANCEL;
        }

        /**
         * 把本结果应用到当前伤害值上。由事件转发逻辑调用。
         */
        private float applyTo(float current) {
            return switch (kind) {
                case KEEP -> current;
                case SET -> value;
                case ADJUST -> current + value;
                case SCALE -> current * value;
                case CANCEL -> 0.0F;
            };
        }

        /**
         * 本结果是否终止后续订阅。由事件转发逻辑调用。
         */
        private boolean stopsChain() {
            return kind == Kind.CANCEL;
        }

        /**
         * 修改方式。
         */
        private enum Kind {
            KEEP,
            SET,
            ADJUST,
            SCALE,
            CANCEL
        }
    }
}
