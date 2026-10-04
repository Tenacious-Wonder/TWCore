package org.twcore.bodypart;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import org.twcore.api.bodypart.BodyPart;
import org.twcore.api.bodypart.BodyPartHit;
import org.twcore.api.event.BodyPartHitEvent;
import org.twcore.api.util.CombatDamageFilter;

/**
 * 近战攻击命中玩家的部位判定。
 *
 * <p>玩家被近战攻击时算出这一刀落在哪个部位，再交由命中事件决定是否挡下这次伤害。与弹射物那条
 * 路径的区别在于没有飞行轨迹：游戏不给出刀落在哪儿，因此改为按双方站位推定，算法见
 * {@link MeleeBodyPartGeometry}。</p>
 *
 * <p>判定挂在 {@code ServerLivingEntityEvents.ALLOW_DAMAGE} 上，用伤害的直接来源区分近战与远程：
 * 近战的直接来源就是下手的一方，箭与火球那类远程的直接来源是飞行中的弹射物，因此不会与
 * {@link ProjectileBodyPartDetector} 重复触发。</p>
 */
public final class MeleeBodyPartDetector {

    private MeleeBodyPartDetector() {
    }

    /**
     * 挂上受击判定。由 TW Core 初始化时调用一次，模组无需调用。
     *
     * @since 1.0.5
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MeleeBodyPartDetector::allowDamage);
    }

    /**
     * 伤害即将落到玩家身上时推定命中部位，并给出这次伤害是否应当继续。
     *
     * @param entity 受伤的实体
     * @param source 伤害来源
     * @param amount 本次伤害的数值，本判定不使用
     * @return 本次伤害是否继续；命中被订阅方挡下时返回 {@code false}
     */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        if (player.isDead() || player.isInvulnerableTo(source)) {
            return true;
        }

        if (!CombatDamageFilter.isAttack(source)) {
            return true;
        }

        // 挨打后的短时间内游戏本来还会挡掉后续伤害，那段无敌帧内不重复判定，
        // 使一次挨打只产生一次部位记录。
        if (player.hurtTime > 0) {
            return true;
        }

        // 只有直接来源是生物才算近战；箭与火球那类的直接来源是弹射物，留给弹射物路径。
        if (!(source.getSource() instanceof LivingEntity attacker) || attacker == player) {
            return true;
        }

        BodyPart part = MeleeBodyPartGeometry.pick(player, attacker);
        BodyPartHit hit = new BodyPartHit.Melee(player, attacker, part);
        RecentBodyPartHit.remember(player, hit);

        return !BodyPartHitEvent.isBlocked(hit);
    }
}
