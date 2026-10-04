package org.twcore.api.bodypart;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * <h1>玩家身体部位</h1>
 * <p>
 * 玩家身体被划分为七个判定部位，用于回答一次命中落在身体的哪一处。命中发生时以一份命中记录给出
 * 结果，本接口即这份记录：被命中的玩家、命中的部位，以及这次命中的来源信息。来源是弹射物时为
 * {@link Projectile}，是近战攻击时为 {@link Melee}，两者都实现本接口，因此按部位处理命中的逻辑
 * 只需写一次。
 * </p>
 *
 * <h2>系统构成</h2>
 * <ul>
 *     <li>{@link BodyPart} —— 七个部位，判定结果的取值。</li>
 *     <li>本接口 —— 一次命中的记录：被命中的玩家、命中的部位与来源信息。</li>
 *     <li>{@link org.twcore.api.event.BodyPartHitEvent} —— 命中时触发，订阅方可挡下这次命中。</li>
 *     <li>{@link org.twcore.api.event.PlayerDamageEvent} —— 伤害结算时触发，订阅方可改写本次结算
 *         使用的伤害值。</li>
 * </ul>
 *
 * <h2>判定流程</h2>
 * <ol>
 *     <li>弹射物命中：在碰撞处理之前求出命中点，换算到玩家身体坐标系后确定部位。</li>
 *     <li>近战受击：按攻击者的出手点与受击者的站位算出各部位的权重，再抽取一个部位。</li>
 *     <li>两条路径都只在服务端进行，判定完成后触发命中事件，命中记录随之暂存。</li>
 *     <li>伤害结算时触发伤害事件，其上下文带上本次命中的部位。</li>
 * </ol>
 *
 * <h2>接入方式</h2>
 * <pre>{@code
 * BodyPartHitEvent.BODY_PART_HIT.register(hit -> hit.part() == BodyPart.HEAD
 *         ? BodyPartHitEvent.Result.block()
 *         : BodyPartHitEvent.Result.pass());
 *
 * PlayerDamageEvent.PLAYER_DAMAGE.register((context, currentDamage) ->
 *         context.part() == BodyPart.CHEST
 *                 ? PlayerDamageEvent.Result.adjust(-2.0F)
 *                 : PlayerDamageEvent.Result.keep());
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>两个事件都只在服务端触发，客户端不产生事件。</li>
 *     <li>订阅顺序由 {@link org.twcore.api.event.TwEventPhases 阶段}划分；注册时不指定阶段即使用
 *         默认阶段。</li>
 *     <li>没有命中部位的伤害（药水、摔落、爆炸等）同样触发伤害事件，其上下文中的部位为
 *         {@code null}。</li>
 * </ul>
 *
 * @since 1.0.5
 * @see BodyPart
 * @see org.twcore.api.event.BodyPartHitEvent
 * @see org.twcore.api.event.PlayerDamageEvent
 */
public interface BodyPartHit {

    /**
     * 被命中的玩家。
     */
    ServerPlayerEntity player();

    /**
     * 命中的部位。
     */
    BodyPart part();

    /**
     * 一次弹射物命中：命中点可求，因此附带造成命中的弹射物与命中点的世界坐标。
     *
     * @param player     被命中的玩家
     * @param projectile 造成命中的弹射物
     * @param part       命中的部位
     * @param hitPos     命中点的世界坐标
     * @since 1.0.5
     */
    record Projectile(ServerPlayerEntity player, ProjectileEntity projectile, BodyPart part, Vec3d hitPos)
            implements BodyPartHit {
    }

    /**
     * 一次近战命中：贴身的攻击没有可以求交点的飞行轨迹，因此不含命中点，改为附带下手的生物。
     *
     * @param player   被命中的玩家
     * @param attacker 下手的一方
     * @param part     命中的部位
     * @since 1.0.5
     */
    record Melee(ServerPlayerEntity player, LivingEntity attacker, BodyPart part) implements BodyPartHit {
    }
}
