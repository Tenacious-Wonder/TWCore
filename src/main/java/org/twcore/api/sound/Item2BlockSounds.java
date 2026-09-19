package org.twcore.api.sound;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

/**
 * 从物品获取对应方块声音组的工具类。
 *
 * <h2>设计目的</h2>
 * <p>
 * 提供灵活的扩展机制，允许任何物品（不限于 {@link BlockItem}）映射或解析出对应的方块声音组，
 * 用于播放放置、破坏等音效：既能给普通物品直接绑定声音组，也能通过自定义解析器按条件推断。
 * </p>
 *
 * <h2>声音组解析优先级</h2>
 * <ol>
 *     <li><b>显式映射</b>（最高优先级）：通过 {@link #registerMapping(Item, BlockSoundGroup)} 手动绑定的映射。</li>
 *     <li><b>自定义解析器链</b>：按注册顺序依次尝试，返回第一个非 {@code null} 的结果。</li>
 *     <li><b>内置默认解析器</b>：自动作为最后一个解析器，当物品是 {@link BlockItem} 时返回对应方块的 {@link BlockSoundGroup}。</li>
 *     <li><b>最终兜底</b>：若以上步骤均未获取到，返回 {@link BlockSoundGroup#STONE}。</li>
 * </ol>
 *
 * <h2>使用方法</h2>
 * <pre>{@code
 * // 注册特定物品到声音组的映射（最高优先级）
 * Item2BlockSounds.registerMapping(Items.STICK, BlockSoundGroup.WOOD);
 *
 * // 注册自定义解析器（用于处理特殊物品）
 * Item2BlockSounds.registerParser(stack -> {
 *     if (stack.isOf(Items.DIAMOND)) {
 *         return Optional.of(BlockSoundGroup.METAL);
 *     }
 *     return Optional.empty();
 * });
 *
 * // 获取声音组
 * BlockSoundGroup group = Item2BlockSounds.getSoundGroup(stack);
 * SoundEvent placeSound = group.getPlaceSound();
 * }</pre>
 *
 * @see BlockSoundGroup
 */
public final class Item2BlockSounds {

    /**
     * 显式映射表：物品 → 方块声音组。
     * 此映射优先级最高，命中后直接返回，不再经过解析器。
     */
    private static final Map<Item, BlockSoundGroup> EXPLICIT_MAPPINGS = new HashMap<>();

    /**
     * 自定义解析器列表，按注册顺序执行。
     * 每个解析器接收一个 ItemStack，返回 Optional 包装的声音组，空值表示无法解析。
     */
    private static final List<Function<ItemStack, Optional<BlockSoundGroup>>> PARSERS = new ArrayList<>();

    /**
     * 内置的 BlockItem 解析器，作为最后一个解析器。
     * 当物品是 {@link BlockItem} 时，返回对应方块的默认声音组。
     */
    private static final Function<ItemStack, Optional<BlockSoundGroup>> BLOCK_ITEM_PARSER = stack -> {
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return Optional.ofNullable(block.getDefaultState().getSoundGroup());
        }
        return Optional.empty();
    };

    static {
        // 默认解析器（BlockItem）作为初始唯一解析器
        PARSERS.add(BLOCK_ITEM_PARSER);
    }

    private Item2BlockSounds() {}

    /**
     * 注册一个显式映射，将指定物品直接绑定到某个声音组。
     * 此映射具有最高优先级，会覆盖任何解析器的结果。
     *
     * @param item       目标物品
     * @param soundGroup 对应的方块声音组
     */
    public static void registerMapping(@NotNull Item item, @NotNull BlockSoundGroup soundGroup) {
        EXPLICIT_MAPPINGS.put(item, soundGroup);
    }

    /**
     * 移除指定物品的显式映射。
     *
     * @param item 要移除映射的物品
     */
    public static void removeMapping(@NotNull Item item) {
        EXPLICIT_MAPPINGS.remove(item);
    }

    /**
     * 注册一个自定义解析器。
     * <p>
     * 解析器按注册顺序执行，一旦返回非空结果即停止继续尝试。
     * 内置的 BlockItem 解析器始终作为最后一个解析器存在，自定义解析器会插入到它之前。
     * </p>
     *
     * @param parser 解析器函数，接收 ItemStack 并返回 Optional 包装的声音组
     */
    public static void registerParser(@NotNull Function<ItemStack, Optional<BlockSoundGroup>> parser) {
        // 将新解析器添加到 BlockItem 解析器之前，保持 BlockItem 始终在最后
        int lastIndex = PARSERS.size() - 1;
        PARSERS.add(lastIndex, parser);
    }

    /**
     * 移除一个已注册的解析器。
     *
     * @param parser 要移除的解析器实例
     * @return 是否成功移除
     */
    public static boolean removeParser(@NotNull Function<ItemStack, Optional<BlockSoundGroup>> parser) {
        // 不允许移除内置的 BlockItem 解析器
        if (parser == BLOCK_ITEM_PARSER) {
            return false;
        }
        return PARSERS.remove(parser);
    }

    /**
     * 根据物品堆栈获取对应的方块声音组。
     * <p>
     * 查找优先级：
     * <ol>
     *     <li>显式映射</li>
     *     <li>自定义解析器链（按注册顺序）</li>
     *     <li>内置 BlockItem 解析器（如果物品是方块物品）</li>
     *     <li>返回 {@link BlockSoundGroup#STONE} 作为兜底</li>
     * </ol>
     *
     * @param stack 要查询的物品堆栈，不能为 {@code null}
     * @return 对应的方块声音组，保证非 {@code null}
     */
    @NotNull
    public static BlockSoundGroup getSoundGroup(@NotNull ItemStack stack) {
        // 优先检查显式映射
        Item item = stack.getItem();
        BlockSoundGroup mapped = EXPLICIT_MAPPINGS.get(item);
        if (mapped != null) {
            return mapped;
        }

        // 遍历解析器链（包含自定义解析器和最后的内置 BlockItem 解析器）
        for (Function<ItemStack, Optional<BlockSoundGroup>> parser : PARSERS) {
            Optional<BlockSoundGroup> result = parser.apply(stack);
            if (result.isPresent()) {
                return result.get();
            }
        }

        // 兜底：返回石头声音组
        return BlockSoundGroup.STONE;
    }

    /**
     * 重置所有显式映射和自定义解析器，仅保留内置的 BlockItem 解析器。
     * 主要用于测试或重载配置。
     */
    public static void reset() {
        EXPLICIT_MAPPINGS.clear();
        PARSERS.clear();
        PARSERS.add(BLOCK_ITEM_PARSER);
    }
}