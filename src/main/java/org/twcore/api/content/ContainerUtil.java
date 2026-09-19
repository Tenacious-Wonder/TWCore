package org.twcore.api.content;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twcore.container.ContainerType;
import org.twcore.content.Content;
import org.twcore.content.ContentCategories;
import org.twcore.registry.TWRegistries;

import java.util.Optional;

/**
 * <h1>容器内容物系统</h1>
 * <p>
 * 本系统将“容器”（碗、瓶、桶等）与“内容物”（水、汤、牛奶等）抽象为独立的类型，
 * 使得任意容器可以灵活地装载或清空任意兼容的内容物，而无需为每种组合编写单独的物品类。
 * 整个架构由以下几个核心组件构成：
 * </p>
 * <ul>
 *     <li><b>{@link Content}</b> —— 内容物类型，纯类型定义，不携带状态或数量；
 *         每个内容物属于一个分类（如 {@code "liquid"}、{@code "soup"}）。</li>
 *     <li><b>{@link ContainerType}</b> —— 容器类型，定义容器的物理属性（空物品、容量、音效），
 *         以及匹配物品堆栈、提取与替换内容物、判断兼容性等行为。</li>
 *     <li><b>{@link TWRegistries}</b> —— 基于原版 {@link net.minecraft.registry.Registry} 的注册表，
 *         管理所有 {@code Content} 与 {@code ContainerType} 实例。</li>
 *     <li><b>{@link ContainerStack}</b> —— 容器分析结果的绑定对象，也是日常操作的核心对象；
 *         它封装了一个具体的物品堆栈、识别出的容器类型以及提取到的内容物。</li>
 *     <li><b>本类</b> —— 系统入口（如 {@link #analyze(ItemStack)}）与不依赖具体物品堆栈的工厂方法。</li>
 *     <li><b>{@link ContentCategories}</b> —— 内容物分类管理工具，维护分类到内容物集合的映射，
 *         便于按分类快速查询。</li>
 * </ul>
 *
 * <h2>设计理念</h2>
 * <p>
 * 系统的核心是<b>解耦容器与内容物</b>：容器只负责“容纳”这一行为，内容物只负责“是什么”。
 * 通过注册表动态组合，添加新饮料只需注册一个 {@code Content}，所有兼容的容器自动能够盛装它。
 * </p>
 * <p>
 * 所有对物品堆栈的修改操作（如替换内容物）都遵循<b>不可变语义</b>：
 * 不修改传入的原始堆栈，而是返回一个新的 {@link ItemStack}。
 * </p>
 *
 * <h2>使用流程</h2>
 * <ol>
 *     <li>通过 {@link #analyze(ItemStack)} 分析手中的物品堆栈，获得 {@link ContainerStack}；</li>
 *     <li>在 {@code ContainerStack} 上调用实例方法进行判断或操作，例如
 *         {@link ContainerStack#isEmpty()}、{@link ContainerStack#canContain(Content)}、
 *         {@link ContainerStack#replaceContent(Content)}；</li>
 *     <li>若只需创建容器物品而不依赖现有堆栈，直接使用本类的静态工厂方法，
 *         如 {@link #createEmptyContainer(ContainerType, int)}、
 *         {@link #createFilledContainer(ContainerType, Content, int)}。</li>
 * </ol>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>所有 {@link Content} 与 {@link ContainerType} 实例必须在模组初始化时通过原版
 *         {@code Registry.register} 注册到 {@link TWRegistries#CONTENT} 与
 *         {@link TWRegistries#CONTAINER_TYPE}，否则分析功能无法发现它们。</li>
 *     <li>{@link ContentCategories} 需要手动调用 {@code init()} 初始化，
 *         建议在注册完成后执行一次。</li>
 *     <li>{@link ContainerType#replaceContent(ItemStack, Content)} 的实现必须遵守
 *         “返回新堆栈，不修改原堆栈”的契约。</li>
 *     <li>本类的工厂方法不接受非法参数，容器兼容性检查在内部完成，
 *         不满足时抛出 {@link IllegalArgumentException}。</li>
 * </ul>
 *
 * @see ContainerStack
 * @see ContainerType
 * @see Content
 * @see ContentCategories
 */
public final class ContainerUtil {

    private ContainerUtil() {}

    /**
     * 分析物品堆栈，识别其容器类型和内容物。
     *
     * @param stack 物品堆栈
     * @return 包含 {@link ContainerStack} 的 {@code Optional}；若不匹配任何容器则为空
     */
    @NotNull
    public static Optional<ContainerStack> analyze(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        for (ContainerType container : TWRegistries.CONTAINER_TYPE) {
            if (container.matches(stack)) {
                Content content = container.extractContent(stack);
                return Optional.of(new ContainerStack(container, content, stack));
            }
        }
        return Optional.empty();
    }

    /**
     * 创建一个空容器的物品堆栈。
     *
     * @param container 容器类型
     * @param amount    数量
     * @return 空容器物品堆栈
     */
    @NotNull
    public static ItemStack createEmptyContainer(@NotNull ContainerType container, int amount) {
        return container.createEmptyItemStack(amount);
    }

    /**
     * 通过注册表 ID 创建一个空容器的物品堆栈。
     *
     * @param containerId 容器类型的注册表 ID
     * @param amount      数量
     * @return 空容器物品堆栈；若 ID 无效则返回 {@code ItemStack.EMPTY}
     */
    @NotNull
    public static ItemStack createEmptyContainer(@NotNull Identifier containerId, int amount) {
        ContainerType container = TWRegistries.CONTAINER_TYPE.get(containerId);
        if (container == null) {
            return ItemStack.EMPTY;
        }
        return createEmptyContainer(container, amount);
    }

    /**
     * 创建装有指定内容物的物品堆栈。
     *
     * @param container 容器类型
     * @param content   内容物类型
     * @param amount    数量
     * @return 填充后的物品堆栈
     * @throws IllegalArgumentException 如果容器不能装入该内容物
     */
    @NotNull
    public static ItemStack createFilledContainer(@NotNull ContainerType container,
                                                  @NotNull Content content,
                                                  int amount) {
        if (!container.canContain(content)) {
            throw new IllegalArgumentException("Container cannot contain content: " + content);
        }
        return container.createItemStack(content, amount);
    }

    /**
     * 通过注册表 ID 创建装有指定内容物的物品堆栈。
     *
     * @param containerId 容器注册表 ID
     * @param contentId   内容物注册表 ID
     * @param amount      数量
     * @return 填充后的物品堆栈；若 ID 无效则返回 {@code ItemStack.EMPTY}
     */
    @NotNull
    public static ItemStack createFilledContainer(@NotNull Identifier containerId,
                                                  @NotNull Identifier contentId,
                                                  int amount) {
        ContainerType container = TWRegistries.CONTAINER_TYPE.get(containerId);
        Content content = TWRegistries.CONTENT.get(contentId);
        if (container == null || content == null) {
            return ItemStack.EMPTY;
        }
        return createFilledContainer(container, content, amount);
    }

    /**
     * 尝试从物品堆栈中提取内容物。
     *
     * @param stack 物品堆栈
     * @return 内容物；若不匹配或为空则返回 {@code null}
     */
    @Nullable
    public static Content extractContent(@NotNull ItemStack stack) {
        Optional<ContainerStack> result = analyze(stack);
        return result.map(ContainerStack::content).orElse(null);
    }

    /**
     * 获取物品堆栈的容器类型。
     *
     * @param stack 物品堆栈
     * @return 容器类型；若不匹配则返回 {@code null}
     */
    @Nullable
    public static ContainerType getContainerType(@NotNull ItemStack stack) {
        Optional<ContainerStack> result = analyze(stack);
        return result.map(ContainerStack::container).orElse(null);
    }
}