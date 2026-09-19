package org.twcore.api.process;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.twcore.process.playeraction.PlayerActionFactory;
import org.twcore.process.step.StepExecutionContext;

/**
 * <h1>玩家操作</h1>
 * <p>
 * 流程步骤往往要求玩家<b>做出一次具体的动作</b>——把一个物品投进流程、
 * 取走一份产出，或是消耗某件工具。玩家此刻实际做出的动作，与配方与需求中
 * 预先写好的动作，都需要用同一种东西描述，才能互相比较、存档与执行。
 * 本类就是这类<b>玩家动作</b>的公共基类：每种具体动作是一个子类，携带自己
 * 的类型标识与参数，并提供序列化、物品化、匹配与消耗能力。
 * </p>
 *
 * <h2>动作的两种来源</h2>
 * <ul>
 *     <li><b>数据来源</b>：由 {@link #fromString(String)} 从字符串恢复，
 *         用于读取配方 JSON 与 NBT 存档中预定义的动作；</li>
 *     <li><b>实时来源</b>：由 {@link PlayerActionFactory} 中注册的创建器，
 *         从执行上下文捕获玩家正在做出的行为（如手持物品点击），
 *         生成对应的动作。</li>
 * </ul>
 *
 * <h2>字符串格式</h2>
 * <p>动作编码为“类型|参数1|参数2|...”，参数之间以竖线分隔：</p>
 * <pre>{@code
 * add_item|minecraft:beef
 * }</pre>
 *
 * <h2>动作的能力</h2>
 * <ul>
 *     <li><b>序列化</b>：{@link #toString()} 编码为字符串，
 *         可交回 {@link #fromString(String)} 解析；</li>
 *     <li><b>物品化</b>：{@link #toItemStack()} 把动作表现为物品堆栈，
 *         与原版库存体系互通，便于界面展示与交互；</li>
 *     <li><b>消耗落地</b>：{@link #consume(StepExecutionContext)} 落实动作的
 *         资源变化，如扣除玩家物品、减少工具耐久等；</li>
 *     <li><b>配方匹配</b>：{@link #matches(PlayerAction)} 判断玩家实际动作
 *         能否满足配方的要求；</li>
 *     <li><b>展示</b>：{@link #getCode()} 与 {@link #getDisplayName()}
 *         提供简短稳定的编码与可读名称。</li>
 * </ul>
 *
 * <h2>定义操作子类</h2>
 * <p>每种具体动作是一个 {@code PlayerAction} 子类，需要：</p>
 * <ol>
 *     <li>提供类型标识（{@link #getType()}），实现本动作的字符串解析、
 *         编码与消耗逻辑；</li>
 *     <li>提供从字符串参数与执行上下文创建实例的静态工厂方法
 *         （如 {@code fromParams} 与 {@code fromContext}）；</li>
 *     <li>在流程系统初始化时（如模组主类）通过 {@link PlayerActionFactory}
 *         注册类型标识与上述工厂，之后该类型即可从数据与实时交互中创建。</li>
 * </ol>
 *
 * @see PlayerActionFactory
 * @see StepExecutionContext
 */
public abstract class PlayerAction {

    // ==================== 核心方法 ====================

    /**
     * 从字符串恢复操作实例。
     *
     * <p>读取配方 JSON 或 NBT 存档中预定义的动作时使用；字符串格式见类注释。</p>
     *
     * @param str 格式为“类型|参数1|参数2”的字符串
     * @return 对应的操作实例
     * @throws IllegalArgumentException 如果字符串格式无效或类型未注册
     */
    public static PlayerAction fromString(String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("The operation string cannot be empty");
        }

        // 解析格式：类型|参数1|参数2...
        String[] parts = str.split("\\|");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid operation string formatting: " + str);
        }

        String type = parts[0].trim();
        String[] params = new String[parts.length - 1];
        System.arraycopy(parts, 1, params, 0, params.length);

        return PlayerActionFactory.create(type, params);
    }

    /**
     * 把动作编码为字符串。
     *
     * <p>编码遵循类注释中的格式约定，输出可供 {@link #fromString(String)} 解析，
     * 用于 NBT 与 JSON 序列化。</p>
     *
     * @return 格式为“类型|参数1|参数2”的字符串
     */
    public abstract String toString();

    /**
     * 把动作表现为物品堆栈。
     *
     * <p>物品化表示与原版库存体系互通，用于在界面中展示动作，
     * 或作为动作对应物品参与库存交互。</p>
     *
     * @return 代表此操作的物品堆栈
     */
    public abstract ItemStack toItemStack();

    /**
     * 落实动作的消耗与副作用。
     *
     * <p>执行动作对世界产生的影响，如扣除玩家手持物品、减少工具耐久；
     * 需要豁免的情形（如创造模式）由具体操作自行判断。</p>
     *
     * @param context 步骤执行上下文
     */
    public abstract void consume(StepExecutionContext<?> context);

    /**
     * 检查此操作是否与另一操作匹配，用于<b>配方匹配</b>。
     *
     * <p>匹配按“满足要求”的语义判断（可能不对称），因此<b>不是</b> {@link Object#equals(Object)}
     * 的等价关系；本类不重写 equals/hashCode，比较两个操作是否相等时请使用对象身份，
     * 或由子类自行实现严格值比较。</p>
     *
     * @param other 要匹配的另一操作（通常是配方要求）
     * @return 如果此操作与其他操作匹配则返回 true
     */
    public abstract boolean matches(PlayerAction other);

    /**
     * 获取操作的简短编码表示。
     * <ul>
     *     <li>编码应尽可能简洁，不超过 8 个字符，且能唯一标识该操作（包括类型和参数）。</li>
     *     <li>编码应该稳定，相同的操作总是返回相同的编码。</li>
     * </ul>
     *
     * @return 操作的编码字符串（长度 ≤ 8）
     */
    public abstract String getCode();

    /**
     * 获取操作的显示名称。
     *
     * @return 操作的显示名称
     */
    public abstract Text getDisplayName();

    /**
     * 检查操作是否有效。
     *
     * @param world 世界实例
     * @param pos 位置
     * @return 如果操作在当前上下文中有效则返回 true
     */
    public boolean isValid(World world, BlockPos pos) {
        return true;
    }

    /**
     * 获取操作类型的标识符。
     *
     * @return 操作类型标识符
     */
    public abstract String getType();
}
