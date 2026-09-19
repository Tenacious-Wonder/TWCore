package org.twcore.api.block;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 标记方块可响应玩家的“轻击”操作（快速左键点击）。
 *
 * <p>当玩家在极短时间内（不超过 5 个游戏 tick）完成一次完整的左键破坏动作时，
 * 实现此接口的方块会触发 {@link #onClickBlock} 回调。
 * 该机制通常用于实现类似“右键点击”的快速交互效果，但使用左键触发，例如：</p>
 * <ul>
 *     <li>自定义工具的特殊功能（敲击、切换模式等）；</li>
 *     <li>方块自身的快捷交互（如按钮、开关的快速激活）；</li>
 *     <li>调试或辅助类模组中的点击检测。</li>
 * </ul>
 *
 * <p>注意：此回调仅当破坏动作在 5 tick 内完成时才会触发。
 * 实际时长取决于网络延迟和游戏刻计算，建议避免依赖精确的时间阈值。
 * 该方法仅在服务端调用，客户端不会收到通知。</p>
 *
 * @see #onClickBlock(BlockState, World, BlockPos, PlayerEntity, Hand, Direction)
 */
public interface HaveClickInteractBlock {
    /**
     * 当玩家左键轻击方块时调用（破坏时间不超过 5 tick）。
     * <p>此方法只会在服务端调用。</p>
     *
     * @param state     方块状态
     * @param world     世界
     * @param pos       方块坐标
     * @param player    点击方块的玩家
     * @param hand      使用的手（破坏时为主手）
     * @param direction 破坏方向
     */
    void onClickBlock(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, Direction direction);
}
