package org.twcore.process.playeraction.impl;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.twcore.api.content.ContainerStack;
import org.twcore.api.content.ContainerUtil;
import org.twcore.container.ContainerType;
import org.twcore.content.Content;
import org.twcore.api.process.PlayerAction;
import org.twcore.process.step.StepExecutionContext;
import org.twcore.registry.TWRegistries;

import java.util.Optional;

/**
 * 添加内容物操作。
 * <p>
 * 用于在流程中要求玩家消耗一个装有特定内容物的容器物品（如一碗水、一瓶牛奶）。
 * 支持从字符串参数创建，或从玩家手持物品的上下文中自动识别。
 * </p>
 */
public class AddContentPlayerAction extends PlayerAction {
    public static final String TYPE = "add_content";

    private final Content content;
    private final int count;

    /**
     * 从字符串参数创建添加内容物操作。
     *
     * @param params 参数字符串数组，第一个元素为内容物的注册表ID，第二个可选元素为所需数量（默认1）
     * @return 添加内容物操作实例
     * @throws IllegalArgumentException 如果参数无效或内容物未注册
     */
    public static AddContentPlayerAction fromParams(String[] params) {
        if (params.length < 1) {
            throw new IllegalArgumentException("The Add Content parameter requires the Content ID parameter");
        }

        Identifier contentId = Identifier.tryParse(params[0]);
        if (contentId == null) {
            throw new IllegalArgumentException("Invalid Content ID: " + params[0]);
        }

        Content content = TWRegistries.CONTENT.get(contentId);
        if (content == null) {
            throw new IllegalArgumentException("No Content found: " + contentId);
        }

        int count = 1;
        if (params.length >= 2) {
            try {
                count = Integer.parseInt(params[1]);
                if (count <= 0) {
                    throw new IllegalArgumentException("The quantity must be greater than 0: " + count);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid quantity: " + params[1]);
            }
        }

        return new AddContentPlayerAction(content, count);
    }

    /**
     * 从执行上下文创建添加内容物操作。
     * <p>
     * 检查玩家手持物品是否为装有内容物的容器，若是，则以该内容物和容器的基础容量构造操作。
     * </p>
     *
     * @param context 步骤执行上下文
     * @return 如果手持正确的非空容器，则返回对应的操作实例，否则返回空
     */
    public static Optional<PlayerAction> fromContext(StepExecutionContext<?> context) {
        ItemStack heldItem = context.getHeldItemStack();
        Optional<ContainerStack> binding = ContainerUtil.analyze(heldItem);

        if (binding.isPresent()) {
            ContainerStack cs = binding.get();
            if (!cs.isEmpty()) {
                return Optional.of(new AddContentPlayerAction(cs.content(), cs.container().getBaseCapacity()));
            }
        }
        return Optional.empty();
    }

    public AddContentPlayerAction(Content content, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("The quantity must be greater than 0");
        }
        this.content = content;
        this.count = count;
    }

    public AddContentPlayerAction(Content content) {
        this(content, 1);
    }

    @Override
    public String toString() {
        Identifier contentId = TWRegistries.CONTENT.getId(content);
        return String.format("add_content|%s|%d", contentId, count);
    }

    @Override
    public ItemStack toItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public void consume(StepExecutionContext<?> context) {
        // 播放操作音效
        context.playSound(context.getItemSounds().getPlaceSound());

        if (context.isCreateMode()) {
            return;
        }

        // 如果不是创造模式，消耗玩家手持物品
        ItemStack heldItem = context.getHeldItemStack();
        Optional<ContainerStack> binding = ContainerUtil.analyze(heldItem);

        if (!heldItem.isEmpty() && binding.isPresent()) {
            ContainerStack cs = binding.get();
            if (cs.isEmpty()) return; // 空容器无需处理

            ContainerType container = cs.container();
            int capacity = container.getBaseCapacity();

            // 计算需要消耗多少个容器（向上取整）
            int containersNeeded = (int) Math.ceil((double) count / capacity);

            // 消耗对应数量的容器物品
            heldItem.decrement(containersNeeded);

            // 创建被消耗容器的一个副本，并清空其内容物后返还给玩家
            ItemStack consumedCopy = cs.originalStack().copyWithCount(containersNeeded);
            ItemStack emptyRemainder = container.replaceContent(consumedCopy, null);
            context.giveStack(emptyRemainder);
        }
    }

    @Override
    public boolean matches(PlayerAction other) {
        if (!(other instanceof AddContentPlayerAction otherAction)) {
            return false;
        }

        // 匹配内容物类型和数量
        return this.content == otherAction.content && this.count <= otherAction.count;
    }

    @Override
    public String getCode() {
        Identifier contentId = TWRegistries.CONTENT.getId(content);
        // 1. 操作类型固定位: "c" 表示添加物品
        StringBuilder code = new StringBuilder("c");

        // 2. 命名空间前2位（不足补'_'）
        if (contentId != null) {
            String namespace = contentId.getNamespace();
            if (namespace.length() >= 2) {
                code.append(namespace, 0, 2);
            } else {
                code.append(namespace).append("_".repeat(2 - namespace.length()));
            }
        }

        code.append("_");

        // 3. 物品路径前3位（不足补'_'）
        if (contentId != null) {
            String path = contentId.getPath();
            if (path.length() >= 3) {
                code.append(path, 0, 3);
            } else {
                code.append(path).append("_".repeat(3 - path.length()));
            }
        }

        // 4. 数量（如果大于1则添加，否则省略）
        if (count > 1) {
            code.append(count);
        }

        return code.toString();
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("player_action.add_content", content.getDisplayName(), count);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 返回该操作要求的内容物。
     *
     * <p>修复：此前本操作未暴露内部字段访问器，外部只能解析 {@link #toString()} 获取内容物，
     * 现与 {@link AddItemPlayerAction} 对齐提供访问器。</p>
     *
     * @return 内容物
     * @since 1.0.4
     */
    public Content getContent() {
        return content;
    }

    /**
     * 返回该操作要求的内容物数量。
     *
     * @return 内容物数量
     * @since 1.0.4
     */
    public int getCount() {
        return count;
    }
}