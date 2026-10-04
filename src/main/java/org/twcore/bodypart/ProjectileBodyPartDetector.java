package org.twcore.bodypart;

import java.util.Optional;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.twcore.api.bodypart.BodyPart;
import org.twcore.api.bodypart.BodyPartHit;
import org.twcore.api.event.BodyPartHitEvent;

/**
 * 弹射物命中玩家的部位判定与拦截。
 *
 * <p>在碰撞处理之前求出命中点、算出部位，再交由命中事件决定是否挡下。被挡下时取消原版的碰撞处理
 * 并按原版的方式把弹射物崩回，此后该弹射物在这位玩家身上的每一次碰撞都继续被拦。</p>
 *
 * <p>只在服务端处理：客户端同样会跑弹射物代码，但那里的玩家是本地替身，判定结果没有用处。</p>
 */
public final class ProjectileBodyPartDetector {

    /**
     * 判定弹射物是否擦到实体时向外放宽的距离。
     */
    private static final double TARGETING_MARGIN = 0.3;

    private ProjectileBodyPartDetector() {
    }

    /**
     * 处理一次弹射物对玩家的碰撞。
     *
     * @param projectile 撞到玩家的弹射物
     * @param player     被撞到的玩家
     * @return 是否取消原版的碰撞处理
     */
    public static boolean handleCollision(ProjectileEntity projectile, ServerPlayerEntity player) {
        // 已被挡下的弹射物还在玩家身上打转，继续拦着，否则它下一刻意就会扎进身体。
        if (DeflectedProjectiles.isDeflectedFrom(projectile, player)) {
            return true;
        }

        Vec3d hitPos = locateHitPoint(projectile, player);

        // 求不出交点，说明这一刻弹射物并没有真的穿过身体。射中之后它还会在玩家身上停留若干刻，
        // 每刻都重复报出同一次碰撞，而那些时刻它已经不在身体里，这些重复必须丢掉。
        if (hitPos == null) {
            return false;
        }

        BodyPart part = BodyPartResolver.resolve(player, hitPos);
        BodyPartHit hit = new BodyPartHit.Projectile(player, projectile, part, hitPos);
        RecentBodyPartHit.remember(player, hit);

        if (BodyPartHitEvent.isBlocked(hit)) {
            DeflectedProjectiles.mark(projectile, player);
            deflect(projectile);
            return true;
        }

        return false;
    }

    /**
     * 求这次碰撞打在身体上的哪个点。
     *
     * <p>原版检测命中时算出过这个点，但构造命中结果时把它丢掉了，只留下被击中者自身的位置，因此
     * 不能从命中结果里取。线段方向必须与原版一致：原版在移动弹射物之前做碰撞检测，此时
     * {@code getPos()} 是本刻起点，检测用的是「起点 → 起点 + 本刻速度」。</p>
     *
     * <p>先用真实轮廓求交，求不出时退到放宽一圈的轮廓：放宽后的交点可能落在身体外的空气里，
     * 斜射时会把命中点抬到身体上方。</p>
     *
     * @param projectile 待判定的弹射物
     * @param player     被命中的玩家
     * @return 打在身体上的点；这一刻并没有真的穿过身体时返回 {@code null}
     */
    private static Vec3d locateHitPoint(ProjectileEntity projectile, PlayerEntity player) {
        Vec3d departure = projectile.getPos();
        Vec3d arrival = departure.add(projectile.getVelocity());
        Box body = player.getBoundingBox();

        Optional<Vec3d> hit = body.raycast(departure, arrival);
        if (hit.isEmpty()) {
            hit = body.expand(TARGETING_MARGIN).raycast(departure, arrival);
        }
        return hit.orElse(null);
    }

    /**
     * 把弹射物崩回去，动作与原版「这一击没打动对方」时一致：速度反向并衰减到十分之一、朝向加
     * 180 度，使箭随后的表现与原版相同，而不是凭空消失或卡在身体里。
     *
     * @param projectile 被挡下的弹射物
     */
    private static void deflect(ProjectileEntity projectile) {
        projectile.setVelocity(projectile.getVelocity().multiply(-0.1));
        projectile.setYaw(projectile.getYaw() + 180.0F);
        projectile.prevYaw += 180.0F;
    }
}
