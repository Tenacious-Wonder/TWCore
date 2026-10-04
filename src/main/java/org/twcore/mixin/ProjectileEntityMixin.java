package org.twcore.mixin;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.twcore.bodypart.ProjectileBodyPartDetector;

/**
 * 弹射物命中玩家的判定入口。
 *
 * <p>挂在 {@link ProjectileEntity} 的碰撞处理之前：一切弹射物的碰撞都经过同一个
 * {@code onCollision}，挂在这一层即可一次覆盖箭、三叉戟、雪球与火球。只认服务端玩家，客户端
 * 不参与判定。判定与拦截逻辑见 {@link ProjectileBodyPartDetector}。</p>
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {

    /**
     * 碰撞处理之前判定命中部位，订阅方要求挡下时取消这次碰撞。
     *
     * @param hitResult    本次碰撞的结果，只有撞到实体才是这里关心的
     * @param callbackInfo 原方法的回调，取消即表示不执行原版碰撞处理
     */
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void twcore$locateBodyPart(HitResult hitResult, CallbackInfo callbackInfo) {
        if (!(hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        ProjectileEntity projectile = (ProjectileEntity) (Object) this;

        if (ProjectileBodyPartDetector.handleCollision(projectile, player)) {
            callbackInfo.cancel();
        }
    }
}
