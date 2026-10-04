package org.twcore.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.twcore.bodypart.PlayerDamageResolver;

/**
 * 伤害结算事件 {@link org.twcore.api.event.PlayerDamageEvent} 的触发点。
 *
 * <p>注入到 {@code PlayerEntity#applyDamage} 中护甲与状态效果结算之后的位置，此处改写伤害值即
 * 改写玩家最终承受的伤害。只处理服务端玩家。结算过程见 {@link PlayerDamageResolver}。</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerDamageMixin {

    /**
     * 把本次结算的伤害值交给伤害事件，并采用订阅方改写后的结果。
     *
     * @param amount 护甲与状态效果结算之后的伤害值
     * @param source 伤害来源，取自原方法的参数
     * @return 应用订阅方修改之后的伤害值
     */
    @ModifyVariable(
            method = "applyDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private float twcore$applyDamageEvent(float amount, @Local(argsOnly = true) DamageSource source) {
        // 负值与零在更早的步骤里已经被挡下，这里不必重复处理。
        if (amount <= 0.0F) {
            return amount;
        }

        // 客户端那份结算会被服务端覆盖，处理了也没有意义。
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity player)) {
            return amount;
        }

        // 位置选在吸收（金心）抵扣之前：订阅方减掉的点数不会被吸收重复抵扣，
        // 金心也不替它买单。原版的无敌帧判断用的是此处之前的数值，因此连击节奏不受影响。
        return PlayerDamageResolver.resolve(player, source, amount);
    }
}
