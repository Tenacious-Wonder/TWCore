package org.twcore.api.content;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twcore.container.ContainerType;
import org.twcore.content.Content;

import java.util.Objects;

/**
 * 容器-内容物系统的<b>核心操作对象</b>。
 * <p>
 * 一个 {@code ContainerStack} 实例代表对一个具体物品堆栈的完整分析结果，
 * 它绑定了：
 * <ul>
 *     <li>一个 {@link ContainerType 容器类型} – 物品属于哪种容器（碗、瓶等）</li>
 *     <li>一个可选的 {@link Content 内容物} – 容器当前装载了什么（可为 {@code null} 表示空）</li>
 *     <li>原始的 {@link ItemStack} – 被分析的物品堆栈本身</li>
 * </ul>
 *
 * <h2>核心职责</h2>
 * <p>
 * 在旧设计中，操作一个“容器物品”需要反复调用 {@link ContainerUtil} 中的静态方法，
 * 流程分散且需多次传递同一个物品堆栈。重构后，大部分实例化操作被收拢到 {@code ContainerStack} 中，
 * 使得调用者可以<b>以一个对象为中心完成所有交互</b>。
 * </p>
 * <p>
 * 例如，原本需要：
 * <pre>{@code
 * if (ContainerUtil.isEmptyContainer(stack)) { ... }
 * if (ContainerUtil.providesContent(stack, content)) { ... }
 * ItemStack newStack = ContainerUtil.replaceContent(stack, newContent);
 * }</pre>
 * 现在变为：
 * <pre>{@code
 * ContainerStack cs = ContainerUtil.analyze(stack).orElseThrow();
 * if (cs.isEmptyContainer()) { ... }
 * if (cs.providesContent(content)) { ... }
 * ItemStack newStack = cs.replaceContent(newContent);
 * }</pre>
 * </p>
 *
 * <h2>对象来源</h2>
 * <p>
 * 通常情况下，应通过 {@link ContainerUtil#analyze(ItemStack)} 获取 {@code ContainerStack} 实例。
 * 也可以直接调用构造器，但必须确保传入的容器类型与物品堆栈确实匹配。
 * </p>
 *
 * <h2>不可变性</h2>
 * <p>
 * 本对象本身是不可变的（所有字段均为 final）。但请注意，
 * 原始 {@link ItemStack} 引用被保留，外部如果修改该堆栈，可能会影响此对象内部状态的一致性。
 * 推荐在分析前复制堆栈，或不要长期持有 {@code ContainerStack}。
 * 所有“修改”操作（如 {@link #replaceContent(Content)}）都不会改变本对象，
 * 而是返回新的 {@link ItemStack}。
 * </p>
 *
 * @see ContainerUtil
 * @see ContainerType
 * @see Content
 */
public record ContainerStack(ContainerType container, @Nullable Content content, ItemStack originalStack) {
    /**
     * @param container     容器类型，不能为null
     * @param content       内容物类型，可以为null（空容器）
     * @param originalStack 原始物品堆栈，不能为null
     */
    public ContainerStack(@NotNull ContainerType container,
                          @Nullable Content content,
                          @NotNull ItemStack originalStack) {
        this.container = Objects.requireNonNull(container, "Container cannot be null");
        this.content = content;
        this.originalStack = originalStack.copy();
    }

    public boolean isEmpty() {
        return content == null;
    }

    /**
     * 检查是否装有指定的内容物。
     */
    public boolean contains(@NotNull Content content) {
        return !isEmpty() && this.content.equals(content);
    }

    /**
     * 检查此容器物品堆栈是否提供指定的内容物。
     */
    public boolean providesContent(@NotNull Content content) {
        Objects.requireNonNull(content);
        return !isEmpty() && this.content.equals(content);
    }

    /**
     * 检查是否为空容器。
     */
    public boolean isEmptyContainer() {
        return isEmpty();
    }

    /**
     * 检查此容器是否可以装入指定的内容物。
     */
    public boolean canContain(@NotNull Content content) {
        return isEmpty() && container.canContain(content);
    }

    /**
     * 替换此容器中的内容物，返回一个新的 ItemStack。
     * 不修改原始堆栈。
     */
    @NotNull
    public ItemStack replaceContent(@Nullable Content newContent) {
        return container.replaceContent(originalStack, newContent);
    }

    /**
     * 获取一个装有特定内容物的新物品堆栈。
     */
    @NotNull
    public ItemStack createFilledStack(@NotNull Content content, int amount) {
        return container.createItemStack(content, amount);
    }

    /**
     * 获取一个空容器的物品堆栈。
     */
    @NotNull
    public ItemStack createEmptyStack(int amount) {
        return container.createEmptyItemStack(amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerStack that)) return false;
        return container.equals(that.container) &&
                Objects.equals(content, that.content) &&
                ItemStack.areItemsEqual(originalStack, that.originalStack);
    }

    @Override
    public @NotNull String toString() {
        return "ContainerStack{" +
                "container=" + container +
                ", content=" + (content != null ? content : "null") +
                '}';
    }
}