package org.twcore.bodypart;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 已被挡下的弹射物，以及它被挡下时所在的玩家。
 *
 * <p>被崩回的弹射物速度只剩十分之一，还会在玩家身上停留若干刻，同一个游戏刻到随后几刻会反复报出
 * 同一次碰撞。原版举盾挡箭时靠「每次碰撞都拦下来」处理这件事，这里同样如此：进过本表的弹射物，
 * 在它离开这位玩家之前，每一次碰撞都继续拦着，否则它下一刻意就会扎进身体。</p>
 *
 * <p>键是弹射物实体本身，用弱引用表是为了让它被移除之后自动清理。</p>
 */
final class DeflectedProjectiles {

    /**
     * 弹射物 → 挡下它的玩家。
     */
    private static final Map<ProjectileEntity, UUID> DEFLECTED = new WeakHashMap<>();

    private DeflectedProjectiles() {
    }

    /**
     * 这枚弹射物是否已被这位玩家挡下。
     *
     * @param projectile 待查询的弹射物
     * @param player     挡下它的玩家
     * @return 已被这位玩家挡下返回 {@code true}
     */
    static boolean isDeflectedFrom(ProjectileEntity projectile, ServerPlayerEntity player) {
        return player.getUuid().equals(DEFLECTED.get(projectile));
    }

    /**
     * 记下这枚弹射物已被这位玩家挡下。
     *
     * @param projectile 被挡下的弹射物
     * @param player     挡下它的玩家
     */
    static void mark(ProjectileEntity projectile, ServerPlayerEntity player) {
        DEFLECTED.put(projectile, player.getUuid());
    }
}
