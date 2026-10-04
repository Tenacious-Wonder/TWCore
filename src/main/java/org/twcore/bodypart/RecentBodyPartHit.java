package org.twcore.bodypart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;

import org.twcore.api.bodypart.BodyPartHit;

/**
 * 最近一次命中记录的暂存。
 *
 * <p>部位判定与伤害结算不是同一段代码：判定发生在伤害落下之前，而按部位改写伤害只能在结算时进行，
 * 两者之间没有参数通道可递。判定时在这里记下命中记录，结算时取走。</p>
 *
 * <p>只认同一刻记下的记录：「判定发生了、伤害却没有落下」的情形是有的（雪球、盾牌、无敌帧），
 * 那种记录若不校验就会被下一次挨打误用。每位玩家至多留一条，取走即删。</p>
 */
final class RecentBodyPartHit {

    /**
     * 玩家 → 刚记下的命中记录。
     */
    private static final Map<UUID, Note> NOTES = new HashMap<>();

    private RecentBodyPartHit() {
    }

    /**
     * 记下这次命中，覆盖该玩家上一条记录。
     *
     * @param player 被命中的玩家
     * @param hit    本次命中的记录
     */
    static void remember(ServerPlayerEntity player, BodyPartHit hit) {
        NOTES.put(player.getUuid(), new Note(hit, player.getWorld().getTime()));
    }

    /**
     * 取走该玩家刚记下的命中记录，并立即删除。
     *
     * <p>只有同一刻记下的记录才算数：更早的记录说明那一击没有真正落下。</p>
     *
     * @param player 被命中的玩家
     * @return 本次命中的记录；没有有效记录时返回 {@code null}
     */
    static BodyPartHit consume(ServerPlayerEntity player) {
        Note note = NOTES.remove(player.getUuid());

        if (note == null) {
            return null;
        }

        return player.getWorld().getTime() == note.tick() ? note.hit() : null;
    }

    /**
     * 一条记录：命中内容与记下它的世界刻。
     *
     * @param hit  本次命中的记录
     * @param tick 记下时的世界刻
     */
    private record Note(BodyPartHit hit, long tick) {
    }
}
