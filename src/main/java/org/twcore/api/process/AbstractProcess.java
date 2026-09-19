package org.twcore.api.process;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.twcore.process.step.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <h1>多步骤交互流程</h1>
 * <p>
 * 许多方块交互并不在一次点击内结束——腌制、揉捏、用餐都需要玩家分阶段参与、
 * 随时间推进。本类把这类交互组织成一条<b>按序推进的流程</b>：流程由一组带唯一
 * ID 的步骤组成，从起始步骤开始，每执行一步都会产生一个去向结果，流程据此前行、
 * 循环或终止；当前推进到哪一步由流程实例自行记忆，因此一次流程可以跨越多次交互、
 * 多个 tick 持续进行，进度还能随方块实体写入 NBT，在重进世界后继续。
 * </p>
 * <p>
 * 本类是定义这种流程的<b>基类</b>，不绑定任何特定方块实体：子类声明自己的步骤
 * 与初始步骤，方块实体持有流程实例，并把玩家的交互与方块 tick 委托给它。
 * </p>
 *
 * <h2>核心概念</h2>
 * <ul>
 *     <li><b>步骤</b>（{@link Step}）：流程的最小执行单元，由唯一 ID 标识；
 *         步骤声明自己适用于哪些方块实体，并处理该步的一次交互。</li>
 *     <li><b>流程状态</b>：流程记忆当前步骤、上一步骤与活动标记；
 *         该状态可写入 NBT，让进度随方块实体保存与恢复。</li>
 *     <li><b>执行上下文</b>（{@link StepExecutionContext}）：一次执行的现场快照，
 *         包含方块实体、方块状态、世界、位置、玩家、手与命中信息。</li>
 *     <li><b>执行结果</b>（{@link StepResult}）：步骤执行后给出的去向指令——
 *         继续当前步骤、进入下一步、完成流程、失败回退或整体重置。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <ol>
 *     <li><b>开始</b>：调用 {@link #start(World, Object)} 启动流程，流程进入
 *         活动状态，落在初始步骤上；</li>
 *     <li><b>推进</b>：玩家交互或方块 tick 调用 {@code executeStep(...)}，流程
 *         执行当前步骤，并按步骤给出的执行结果转移；</li>
 *     <li><b>完成或失败</b>：步骤宣告完成时，流程执行完成回调并自动重置回初始
 *         状态；失败则回退到指定步骤继续，或同样整体重置；</li>
 *     <li><b>复用</b>：重置后的流程可以再次 {@link #start(World, Object)}，
 *         同一个流程实例可反复承载多次交互。</li>
 * </ol>
 *
 * <h2>定义流程子类</h2>
 * <p>继承本类即获得上述完整机制，子类需要做三件事：</p>
 * <ol>
 *     <li>实现抽象方法 {@link #getInitialStepId()}，返回流程的起始步骤 ID；</li>
 *     <li>在构造函数中通过 {@link #registerStep(String, Step)} 注册全部步骤；</li>
 *     <li>按需重写钩子：生命周期钩子
 *         （{@link #onStart(World, Object)}、{@link #onComplete()}、
 *         {@link #onReset()}、{@link #beforeGetStep(StepExecutionContext)}）
 *         与状态展示钩子（{@link #shouldShowStepList()}、{@link #getCustomStatusInfo()}）。</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <p>方块实体持有流程实例，并把玩家交互委托给流程执行：</p>
 * <pre>{@code
 * public class MyBlockEntity extends BlockEntity {
 *     // 方块实体持有流程实例
 *     private final MyProcess process = new MyProcess();
 *
 *     public ActionResult onUse(PlayerEntity player, Hand hand, BlockHitResult hit) {
 *         // 委托给流程执行
 *         return process.executeStep(this, getCachedState(), world, pos, player, hand, hit);
 *     }
 *
 *     // 流程实例管理所有复杂逻辑
 *     private static class MyProcess extends AbstractProcess<MyBlockEntity> {
 *         // 步骤定义和流程逻辑...
 *     }
 * }
 * }</pre>
 *
 * @param <T> 流程支持的操作类型。若流程足够通用，则尽量不绑定特定方块实体
 * @see Step
 * @see StepExecutionContext
 * @see StepResult
 */
public abstract class AbstractProcess<T> {

    /** 步骤注册表：步骤ID -> 步骤实例 */
    protected final Map<String, Step<T>> steps = new HashMap<>();

    /** 当前执行的步骤ID */
    protected String currentStepId;

    /** 上一步执行的步骤ID */
    protected String previousStepId;

    /** 流程是否处于活动状态 */
    protected boolean isActive = false;

    // ============ 受保护的方法（子类使用） ============

    /**
     * 注册一个步骤到流程中。
     *
     * <p>步骤必须在流程初始化时（通常在构造函数中）注册。
     * 每个步骤有唯一的ID，用于在步骤之间转移。</p>
     *
     * @param stepId 步骤的唯一标识符
     * @param step 步骤实例
     */
    protected void registerStep(String stepId, Step<T> step) {
        if (steps.containsKey(stepId)) {
            throw new IllegalArgumentException("The step is already registered: " + stepId);
        }
        steps.put(stepId, step);
    }

    // ============ 公开的方法（外部调用） ============

    /**
     * 有玩家参与的情况下执行当前步骤。
     *
     * <p>此方法会调用当前步骤的{@link Step#execute}方法，并根据返回的
     * {@link StepResult}决定下一步动作。每次调用必定消耗当前步骤，
     * 但下一步可能是相同的步骤（用于循环结构）。</p>
     *
     * @param blockEntity 执行步骤的方块实体
     * @param blockState 方块的当前状态
     * @param world 世界实例
     * @param pos 方块位置
     * @param player 执行交互的玩家
     * @param hand 玩家使用的手
     * @param hit 方块点击信息
     * @return 交互结果，指示操作是否成功
     *
     * @throws IllegalStateException 如果当前步骤未设置或未注册
     */
    public ActionResult executeStep(T blockEntity, BlockState blockState, World world, BlockPos pos,
                                    PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 检查流程状态
        if (!isActive || currentStepId == null) {
            return ActionResult.PASS;
        }

        // 记录当前步骤为即将执行的上一步
        String stepBeforeExecution = currentStepId;

        // 准备执行上下文
        StepExecutionContext<T> context = new StepExecutionContext<>(
                this, blockEntity, blockState, world, pos, player, hand, hit
        );

        // 子类可以重写beforeGetStep方法来在获取步骤前做一些处理
        beforeGetStep(context);

        // 获取当前步骤（注意：currentStepId可能在beforeGetStep中被修改）
        Step<T> currentStep = steps.get(currentStepId);
        if (currentStep == null) {
            throw new IllegalStateException("Step is not registered: " + currentStepId);
        }

        // 验证方块实体类型
        if (!currentStep.canExecuteOn(blockEntity)) {
            return ActionResult.FAIL;
        }

        // 执行步骤
        StepResult result = currentStep.execute(context);
        if (result == null) {
            throw new IllegalStateException("The step returned a null result: " + currentStepId);
        }

        // 处理步骤结果
        handleStepResult(result, stepBeforeExecution);

        return result.getActionResult();
    }

    /**
     * 无玩家参与的情况下执行当前步骤。
     *
     * <p>用于让流程的推进发生在方块 tick 等非交互场合：此时没有玩家、手与命中信息，
     * 步骤只会拿到方块实体与世界位置。内部转发到带玩家的重载版本，玩家相关参数为 {@code null}。</p>
     *
     * @return 交互结果
     * @see #executeStep(Object, BlockState, World, BlockPos, PlayerEntity, Hand, BlockHitResult)
     */
    public ActionResult executeStep(T blockEntity, BlockState blockState, World world, BlockPos pos) {
        return executeStep(blockEntity, blockState, world, pos, null, null ,null);
    }

    /**
     * 开始执行流程。
     *
     * <p>此方法将流程状态重置为初始状态，并调用{@link #onStart(World, Object)}回调。
     * 通常在玩家第一次与方块交互时调用。</p>
     */
    public void start(World world, T blockEntity) {
        this.isActive = true;
        this.currentStepId = getInitialStepId();
        this.previousStepId = null;
        onStart(world, blockEntity);
    }

    /**
     * 重置流程到初始状态。
     *
     * <p>清除所有状态数据，将当前步骤重置为初始步骤，
     * 并将流程标记为非活动状态。</p>
     */
    public void reset() {
        this.currentStepId = getInitialStepId();
        this.previousStepId = null;
        this.isActive = false;
        onReset();
    }

    /**
     * 强制跳转到指定步骤。
     *
     * <p>跳过正常的步骤转移逻辑，直接跳转到指定步骤。
     * 用于特殊情况下的流程控制。</p>
     *
     * @param stepId 要跳转到的步骤ID
     * @throws IllegalArgumentException 如果步骤ID未注册
     */
    public void jumpToStep(String stepId) {
        if (!steps.containsKey(stepId)) {
            throw new IllegalArgumentException("Step is not registered: " + stepId);
        }
        this.previousStepId = this.currentStepId;
        this.currentStepId = stepId;
    }

    // ============ 状态查询方法 ============

    /**
     * 获取当前步骤 ID。
     *
     * @return 当前步骤 ID；如果尚未设置则返回 {@code null}
     */
    public String getCurrentStepId() {
        return currentStepId;
    }

    /**
     * 获取上一步骤 ID。
     *
     * @return 上一步骤 ID；如果尚未设置则返回 {@code null}
     */
    public String getPreviousStepId() {
        return previousStepId;
    }

    /**
     * 检查流程是否处于活动状态。
     *
     * @return 如果流程已开始且未完成或失败，则返回 {@code true}
     */
    public boolean isActive() {
        return isActive;
    }

    // ============ 处理步骤结果（内部方法） ============

    /**
     * 处理步骤执行结果，根据结果类型更新流程状态。
     *
     * @param result         步骤执行结果
     * @param executedStepId 刚刚执行完毕的步骤 ID，将被记为上一步骤
     */
    private void handleStepResult(StepResult result, String executedStepId) {
        // 记录刚刚执行的步骤为上一步
        this.previousStepId = executedStepId;

        switch (result.getType()) {
            case CONTINUE_SAME_STEP:
                // 当前步骤保持不变，用于循环结构；上一步仍是刚执行的同一步骤
                break;

            case NEXT_STEP:
                // 转移到下一步
                if (result.getNextStepId() == null || !steps.containsKey(result.getNextStepId())) {
                    throw new IllegalStateException("The next step is not registered: " + result.getNextStepId());
                }
                this.currentStepId = result.getNextStepId();
                break;

            case COMPLETE:
                // 完成整个流程，重置流程（reset 会清空 previousStepId）
                onComplete();
                reset();
                break;

            case FAIL:
                // 步骤执行失败，回退到指定步骤继续，否则重置流程
                String fallbackStepId = result.getFallbackStepId();
                if (fallbackStepId != null) {
                    this.currentStepId = fallbackStepId;
                } else {
                    reset();
                }
                break;

            case RESET:
                // 重置整个流程
                reset();
                break;
        }
    }

    // ============ 供子类实现的方法 ============

    /**
     * 当流程开始时调用。
     *
     * <p>子类可以在此方法中初始化流程状态数据。</p>
     */
    protected void onStart(World world, T blockEntity) {}

    /**
     * 当流程完成时调用。
     *
     * <p>子类可以在此方法中执行清理操作或触发完成事件。
     * 注意：在此方法调用后，流程会自动重置。</p>
     */
    protected void onComplete() {}

    /**
     * 当流程重置时调用。
     *
     * <p>子类可以在此方法中执行额外的重置逻辑。</p>
     */
    protected void onReset() {}

    /**
     * 获取初始步骤 ID。
     *
     * <p>子类必须实现此方法，返回流程启动时落在的步骤 ID，
     * 该步骤必须在构造函数中通过 {@link #registerStep(String, Step)} 注册过。</p>
     *
     * @return 初始步骤 ID
     */
    protected abstract String getInitialStepId();

    /**
     * 步骤获取前的钩子方法。
     *
     * <p>子类可以重写此方法，在获取步骤实例前做一些预处理，比如根据条件跳转步骤。
     * 此方法在每次执行步骤前被调用，可以通过修改 {@code currentStepId}
     * 或调用 {@link #jumpToStep(String)} 来改变将要执行的步骤。</p>
     *
     * @param context 步骤执行上下文
     */
    protected void beforeGetStep(StepExecutionContext<T> context) {}

    /**
     * 将流程状态写入 NBT。
     *
     * @param nbt 要写入的 NBT 复合标签
     */
    public void writeToNbt(NbtCompound nbt) {
        if (currentStepId != null) {
            nbt.putString("current_step_id", currentStepId);
        }

        if (previousStepId != null) {
            nbt.putString("previous_step_id", previousStepId);
        }

        nbt.putBoolean("is_active", isActive);
    }

    /**
     * 从 NBT 读取流程状态。
     *
     * @param nbt 要读取的 NBT 复合标签
     */
    public void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("current_step_id")) {
            currentStepId = nbt.getString("current_step_id");
        }

        if (nbt.contains("previous_step_id")) {
            previousStepId = nbt.getString("previous_step_id");
        }

        isActive = nbt.getBoolean("is_active");
    }

    // ============ 状态信息展示方法 ============

    @Override
    public String toString() {
        return getClass().getName()
                + "{active=" + isActive
                + ", currentStep=" + (currentStepId != null ? currentStepId : "<none>")
                + ", previousStep=" + (previousStepId != null ? previousStepId : "<none>")
                + ", initialStep=" + getInitialStepId()
                + ", registeredSteps=" + steps.size()
                + "}";
    }

    /**
     * 返回流程状态的详细多行展示。
     * <p>
     * 包含流程的核心状态、已注册步骤列表（当 {@link #shouldShowStepList()} 返回 true 时）
     * 以及子类通过 {@link #getCustomStatusInfo()} 提供的自定义信息，适合人工阅读。
     * </p>
     *
     * @return 流程状态的详细展示文本
     * @since 1.0.3
     */
    public String getStatusDetail() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("Class: ").append(getClass().getName()).append("\n");
        sb.append("Active: ").append(isActive).append("\n");
        sb.append("Current Step: ").append(currentStepId != null ? currentStepId : "<none>").append("\n");
        sb.append("Previous Step: ").append(previousStepId != null ? previousStepId : "<none>").append("\n");
        sb.append("Initial Step: ").append(getInitialStepId()).append("\n");
        sb.append("Total Registered Steps: ").append(steps.size()).append("\n");

        // 如果子类要求显示步骤列表，则显示
        if (shouldShowStepList()) {
            sb.append("Registered Steps: [").append(String.join(", ", steps.keySet())).append("]\n");
        }

        // 添加子类自定义状态信息
        String customInfo = getCustomStatusInfo();
        if (customInfo != null && !customInfo.trim().isEmpty()) {
            String[] lines = customInfo.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    sb.append(line).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 是否在状态展示中显示已注册的步骤列表。
     *
     * <p>默认返回 {@code false}，不显示步骤列表以保持输出简洁。</p>
     *
     * @return 如果应该显示步骤列表则返回 {@code true}
     */
    protected boolean shouldShowStepList() {
        return false;
    }

    /**
     * 获取子类自定义的状态信息。
     * <p>
     * 子类可以重写此方法来添加额外的状态信息到 {@link #getStatusDetail()} 输出中。
     * 返回的字符串应该以多行形式组织，每行代表一个状态条目。
     * 例如：
     * <pre>
     * Ingredient Count: 3
     * Cooking Time: 120s
     * Temperature: 150°C
     * </pre>
     * </p>
     *
     * @return 子类自定义的状态信息字符串，如果没有则返回null或空字符串
     */
    protected String getCustomStatusInfo() {
        return null;
    }

    /**
     * 获取简明的状态摘要。
     * <p>
     * 比toString更简洁，只显示最关键的信息，适合日志记录。
     * </p>
     *
     * @return 简明的状态摘要
     * @deprecated 请使用 {@link #getStatusDetail()} 获取详细展示，或使用 {@link #toString()}
     *             获取标准对象表示；此方法将在后续版本中移除。
     */
    @Deprecated
    public String getStatusSummary() {
        return String.format("[%s] Active: %s, Current: %s, Previous: %s",
                getClass().getSimpleName(),
                isActive,
                currentStepId != null ? currentStepId : "<none>",
                previousStepId != null ? previousStepId : "<none>");
    }
}
