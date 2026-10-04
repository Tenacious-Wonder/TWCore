package org.twcore.api.util;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;

/**
 * <h1>攻击伤害判据</h1>
 * <p>
 * 判断一次伤害是否属于「玩家被攻击」，供需要在受击时出手的逻辑共用，使各方对「算不算被攻击」的
 * 理解一致。
 * </p>
 * <p>
 * 魔法、间接魔法、凋零与龙息不算；有攻击者的伤害算；没有攻击者但属于爆炸、下落铁砧、下落方块与
 * 下落钟乳石的伤害也算，其余（摔落、岩浆、仙人掌、虚空等）不算。
 * </p>
 *
 * @since 1.0.5
 */
public final class CombatDamageFilter {

    private CombatDamageFilter() {
    }

    /**
     * 判断一次伤害是否属于被攻击。
     *
     * @param source 伤害来源
     * @return 属于被攻击返回 {@code true}
     * @since 1.0.5
     */
    public static boolean isAttack(DamageSource source) {
        if (source.isOf(DamageTypes.MAGIC)
                || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.WITHER)
                || source.isOf(DamageTypes.DRAGON_BREATH)) {
            return false;
        }

        if (source.getAttacker() != null) {
            return true;
        }

        return source.isIn(DamageTypeTags.IS_EXPLOSION)
                || source.isOf(DamageTypes.UNATTRIBUTED_FIREBALL)
                || source.isOf(DamageTypes.FALLING_ANVIL)
                || source.isOf(DamageTypes.FALLING_BLOCK)
                || source.isOf(DamageTypes.FALLING_STALACTITE);
    }
}
