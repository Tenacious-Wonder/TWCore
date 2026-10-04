package org.twcore.bodypart;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import org.twcore.api.bodypart.BodyPart;
import org.twcore.api.bodypart.BodyPartHit;
import org.twcore.api.event.PlayerDamageEvent;

/**
 * 伤害结算处的接入：把本次命中的部位交给伤害事件，并采用订阅方改写后的伤害值。
 *
 * <p>只在服务端调用。没有部位记录的伤害（药水、摔落、爆炸等）同样会交给事件，此时上下文中的部位
 * 为 {@code null}。</p>
 */
public final class PlayerDamageResolver {

    private PlayerDamageResolver() {
    }

    /**
     * 应用伤害事件，求本次结算使用的伤害值。
     *
     * @param player 受伤的玩家
     * @param source 伤害来源
     * @param amount 护甲与状态效果结算之后的伤害值
     * @return 应用订阅方修改之后的伤害值，不小于 0
     */
    public static float resolve(ServerPlayerEntity player, DamageSource source, float amount) {
        BodyPartHit hit = RecentBodyPartHit.consume(player);
        BodyPart part = hit != null ? hit.part() : null;

        return PlayerDamageEvent.resolveDamage(new PlayerDamageEvent.Context(player, source, part, hit), amount);
    }
}
