package org.twcore.api.content;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twcore.container.ContainerType;
import org.twcore.content.Content;

import java.util.Objects;

/**
 * <h1>容器内容物栈</h1>
 * <p>
 * 一个 {@code ContainerStack} 实例代表对一个具体物品堆栈的完整分析结果，
 * 它绑定了：
 * <ul>
 *     <li>一个 {@link ContainerType 容器类型} —— 物品属于哪种容器（碗、瓶等）；</li>
 *     <li>一个可选的 {@link Content 内容物} —— 容器当前装载了什么（{@code null} 表示空）；</li>
 *     <li>被分析物品堆栈的副本（{@link ItemStack}）—— 构造时复制，与外部原堆栈互不影响。</li>
 * </ul>
 *
 * <h2>核心职责</h2>
 * <p>
 * 容器物品的读写都围绕这个对象展开：分析一次物品堆栈，之后的空否判断、内容物比对与
 * 替换都在同一个对象上完成，无需反复传递堆栈：
 * </p>
 * <pre>{@code
 * ContainerStack cs = ContainerUtil.analyze(stack).orElseThrow();
 * if (cs.isEmptyContainer()) { ... }
 * if (cs.providesContent(content)) { ... }
 * ItemStack newStack = cs.replaceContent(newContent);
 * }</pre>
 *
 * <h2>对象来源</h2>
 * <p>
 * 通常情况下，应通过 {@link ContainerUtil#analyze(ItemStack)} 获取 {@code ContainerStack} 实例。
 * 也可以直接调用构造器，但必须确保传入的容器类型与物品堆栈确实匹配。
 * </p>
 *
 * <h2>不可变性</h2>
 * <p>
 * 本对象本身是不可变的（所有字段均为 final）：构造时会<b>复制</b>传入的物品堆栈，
 * 因此外部之后对原堆栈的修改不会影响本对象。所有“修改”操作
 * （如 {@link #replaceContent(Content)}）也都不改变本对象，而是返回新的 {@link ItemStack}。
 * </p>
 *
 * @see ContainerUtil
 * @see ContainerType
 * @see Content
 */
public record ContainerStack(ContainerType container, @Nullable Content content, ItemStack originalStack) {
    /**
     * 构造容器内容物栈，并复制传入的原始堆栈。
     *
     * @param container     容器类型，不能为 null
     * @param content       内容物类型，可以为 null（空容器）
     * @param originalStack 被分析的物品堆栈，不能为 null；构造时会复制一份保存
     */
    public ContainerStack(@NotNull ContainerType container,
                          @Nullable Content content,
                          @NotNull ItemStack originalStack) {
        this.container = Objects.requireNonNull(container, "Container cannot be null");
        this.content = content;
        this.originalStack = originalStack.copy();
    }

    /**
     * 判断此容器是否为空（没有装载任何内容物）。
     */
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
     * 判断另一个容器内容物栈是否与本栈装的是同一种东西。
     *
     * <p>只比较<b>容器类型</b>与<b>内容物</b>：两者都相同（含都为空）即为 {@code true}。
     * 物品的<b>数量与 NBT 数据不参与比较</b>，因此“3 个装水的碗”与“1 个装水的碗”
     * 在这里算同一种；需要连同数量与 NBT 一起严格比较时，请使用 {@link #equals(Object)}。</p>
     *
     * @param other 要比较的另一个容器内容物栈
     * @return 如果容器类型与内容物都相同则返回 {@code true}
     */
    public boolean sameContentsAs(@NotNull ContainerStack other) {
        Objects.requireNonNull(other);
        return container.equals(other.container) && Objects.equals(content, other.content);
    }

    /**
     * 替换此容器中的内容物，返回一个新的 ItemStack。
     * 不修改本对象持有的堆栈。
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
    public @NotNull String toString() {
        return "ContainerStack{" +
                "container=" + container +
                ", content=" + (content != null ? content : "null") +
                '}';
    }
}