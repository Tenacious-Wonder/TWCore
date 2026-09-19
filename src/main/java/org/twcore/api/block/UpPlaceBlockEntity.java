package org.twcore.api.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.twcore.api.sound.Item2BlockSounds;

import java.util.List;

/**
 * <h1>可放置物品的方块实体</h1>
 * <p>
 * 与 {@link UpPlaceBlock} 配对：方块负责交互判定与轮廓，本实体负责“托着什么”——
 * 它以库存的形式保存方块上的物品，并处理放置、取出、存档与客户端同步。
 * 本类实现了 {@link Inventory}，因此方块上的物品与原版库存体系天然互通。
 * </p>
 *
 * <h2>子类需要实现的契约</h2>
 * <ul>
 *     <li>{@link #isValidItem} —— 什么物品允许放上来；</li>
 *     <li>{@link #getContentShape} —— 物品放上去后的碰撞与选中形状；</li>
 *     <li>{@link #tryAddItem} / {@link #tryFetchItem} —— 放置与取出的具体执行逻辑。</li>
 * </ul>
 * <p>
 * 放置与取出成功后分别回调 {@link #onPlace} 与 {@link #onFetch}，默认会播放音效
 * （放置还会消耗玩家手中 1 个物品，创造模式除外）；重写这两个回调时若仍需要默认行为，
 * 记得调用父类实现。数据变动后调用 {@link #markDirtyAndSync()} 即可同时完成标记与同步。
 * </p>
 *
 * @see UpPlaceBlock
 */
public abstract class UpPlaceBlockEntity extends BlockEntity implements Inventory {

    /** 物品栏列表，存储方块上放置的所有物品。 */
    protected DefaultedList<ItemStack> inventory;

    /**
     * 创建方块实体。
     *
     * @param type          方块实体类型
     * @param pos           方块位置
     * @param state         方块状态
     * @param inventorySize 库存槽位数量
     */
    public UpPlaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inventorySize) {
        super(type, pos, state);
        this.inventory = DefaultedList.ofSize(inventorySize, ItemStack.EMPTY);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.inventory = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, this.inventory);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, this.inventory);
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        validateSlotIndex(slot);
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        validateSlotIndex(slot);
        return Inventories.splitStack(this.inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        validateSlotIndex(slot);
        return Inventories.removeStack(this.inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        validateSlotIndex(slot);

        this.inventory.set(slot, stack);
        limitStackSizeIfNeeded(stack);
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    /**
     * 限制物品堆叠大小不超过最大值。
     *
     * @param stack 需要限制堆叠大小的物品堆栈
     */
    protected void limitStackSizeIfNeeded(ItemStack stack) {
        if (stack.getCount() > this.getMaxCountPerStack()) {
            stack.setCount(this.getMaxCountPerStack());
        }
    }

    /**
     * 验证槽位索引是否在有效范围内。
     *
     * @param slot 要验证的槽位索引
     * @throws IllegalArgumentException 如果槽位索引超出有效范围
     */
    public void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= this.size()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range - [0," + this.size() + ")");
        }
    }

    /**
     * 获取容器中物品的碰撞形状。
     *
     * <p>方块上的物品要参与碰撞与选中轮廓计算，形状由子类按物品给出。</p>
     *
     * @param state   当前方块状态
     * @param world   方块所在的世界
     * @param pos     方块位置
     * @param context 形状计算上下文
     * @return 物品的碰撞形状
     */
    public abstract VoxelShape getContentShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context);

    /**
     * 验证物品是否可以放入该方块实体的物品栏。
     *
     * @param stack 待验证的物品堆栈
     * @return 如果可以放入则返回 true
     */
    public abstract boolean isValidItem(ItemStack stack);

    /**
     * 尝试向容器中添加物品。
     *
     * <p>默认每次添加消耗数量为 1 的物品。若需要消耗不同的数量，一般需要重写
     * {@link #onPlace(BlockState, World, BlockPos, PlayerEntity, Hand, BlockHitResult, ItemStack, List)}
     * 来扣除玩家不同数量的物品。</p>
     *
     * @param stack 要添加的物品堆栈
     * @param hit   方块点击信息
     * @return 操作结果：成功返回 {@link ActionResult#SUCCESS}，失败返回 {@link ActionResult#FAIL}
     */
    public abstract Result tryAddItem(ItemStack stack, BlockHitResult hit);

    /**
     * 尝试从容器中取出物品。
     *
     * @param player 执行取出操作的玩家
     * @param hit    方块点击信息
     * @return 操作结果：成功返回 {@link ActionResult#SUCCESS}，失败返回 {@link ActionResult#FAIL}
     */
    public abstract Result tryFetchItem(PlayerEntity player, BlockHitResult hit);

    /**
     * 当物品成功取出时调用的回调方法。
     *
     * <p>默认实现会播放取出音效。子类可以重写此方法来添加自定义逻辑，
     * 如播放粒子效果、执行特殊操作等；如果重写，请根据需要调用父类方法以保留默认音效。</p>
     *
     * @param state       当前方块状态
     * @param world       方块所在的世界
     * @param pos         方块位置
     * @param player      执行取出操作的玩家
     * @param hand        玩家使用的手
     * @param hit         方块击中结果
     * @param fetchStacks 此次取出操作获得的所有物品堆栈
     */
    public void onFetch(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, List<ItemStack> fetchStacks) {
        playSound(world, pos, fetchStacks.get(0), false);
    }

    /**
     * 当物品成功放置时调用的回调方法。
     *
     * <p>默认实现会播放放置音效并消耗玩家手中 1 个物品（创造模式除外）。
     * 子类可以重写此方法来添加自定义逻辑；如果重写，请根据需要调用父类方法以保留
     * 默认的音效与物品消耗行为。</p>
     *
     * @param state      当前方块状态
     * @param world      方块所在的世界
     * @param pos        方块位置
     * @param player     执行放置操作的玩家
     * @param hand       玩家使用的手
     * @param hit        方块击中结果
     * @param placeStack 放置的物品堆栈
     * @param itemStacks 操作影响的物品堆栈，一般是长度为 1 的 placeStack 列表
     */
    public void onPlace(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, ItemStack placeStack, List<ItemStack> itemStacks) {
        playSound(world, pos, placeStack, true);

        if (!player.isCreative()) {
            placeStack.decrement(1);
        }
    }

    /**
     * 统一的音效播放入口。
     *
     * <p>根据方块上绑定的 {@link UpPlaceBlock.UpSounds} 决定播放策略：</p>
     * <ul>
     *     <li>{@link UpPlaceBlock.UpSounds#EMPTY} —— 完全不播放；</li>
     *     <li>{@link UpPlaceBlock.UpSounds#DYNAMIC} —— 交由 {@link #getDynamicPlaceSound} 按物品推断，
     *         推断不出则静默；</li>
     *     <li>固定音效 —— 播放配置中指定的音效。</li>
     * </ul>
     *
     * @param world        当前世界
     * @param pos          播放位置
     * @param stack        交互的物品堆栈（用于动态声音获取）
     * @param isPlaceSound {@code true} 为放置音效，{@code false} 为取出音效
     * @see UpPlaceBlock.UpSounds
     */
    protected void playSound(World world, BlockPos pos, ItemStack stack, boolean isPlaceSound) {
        if (getCachedState().getBlock() instanceof UpPlaceBlock upPlaceBlock) {
            UpPlaceBlock.UpSounds sounds = upPlaceBlock.upSounds;

            if (sounds == UpPlaceBlock.UpSounds.EMPTY) {
                return;
            }

            if (sounds == UpPlaceBlock.UpSounds.DYNAMIC) {
                SoundEvent sound = getDynamicPlaceSound(stack, isPlaceSound);
                if (sound != null) {
                    world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
            } else {
                sounds.playSound(world, pos, isPlaceSound);
            }
        }
    }

    /**
     * 动态获取物品对应的放置或取出音效。
     *
     * <p>仅在 {@link UpPlaceBlock.UpSounds#DYNAMIC} 模式下被调用。默认实现先从
     * {@link Item2BlockSounds} 取得物品对应的声音组，再取出放置或破坏音效；
     * 子类可以重写此方法以实现完全自定义的选择逻辑。</p>
     *
     * @param stack        被交互的物品堆栈
     * @param isPlaceSound {@code true} 请求放置音效，{@code false} 请求取出音效
     * @return 要播放的 {@link SoundEvent}；返回 {@code null} 则不播放任何声音
     */
    protected SoundEvent getDynamicPlaceSound(ItemStack stack, boolean isPlaceSound) {
        BlockSoundGroup soundGroup = Item2BlockSounds.getSoundGroup(stack);

        return isPlaceSound ? soundGroup.getPlaceSound() : soundGroup.getBreakSound();
    }

    /**
     * 获取容器中的第一个物品的类型。
     *
     * @return 容器中第一个物品的类型；如果容器为空则返回 {@code null}
     */
    public Item getContentItem() {
        ItemStack contentStack = this.getStack(0);
        return contentStack.isEmpty() ? null : contentStack.getItem();
    }

    /**
     * 检查容器是否已满。
     *
     * <p>容器已满的条件是所有槽位都有物品，且每个物品都达到了最大堆叠数量。</p>
     *
     * @return 如果容器已满则返回 true
     */
    public boolean isFull() {
        for (int i = 0; i < this.size(); i++) {
            ItemStack stack = this.getStack(i);
            if (stack.isEmpty() || stack.getCount() < this.getMaxCountPerStack()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 标记方块实体数据已更改，并同步到客户端。
     *
     * <p>便捷方法，等价于依次调用 {@link #markDirty()} 与 {@link #sync()}。</p>
     */
    public void markDirtyAndSync() {
        this.markDirty();
        this.sync();
    }

    /**
     * 同步方块实体数据到客户端。
     *
     * <p>方块实体数据发生变化时调用，确保附近客户端能及时收到更新。</p>
     */
    public void sync() {
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
        }
    }

    /**
     * 一次放置或取出操作的结果。
     *
     * @param opsStacks 本次操作涉及的物品堆栈
     * @param result    操作对应的交互结果
     */
    public record Result(List<ItemStack> opsStacks, ActionResult result) {

        /**
         * 创建一个携带操作涉及物品的结果。
         *
         * @param opsStacks 操作涉及的物品堆栈
         * @param result    交互结果
         * @return 操作结果
         */
        public static Result of(List<ItemStack> opsStacks, ActionResult result) {
            return new Result(opsStacks, result);
        }

        /**
         * 创建一个不涉及具体物品的结果。
         *
         * @param result 交互结果
         * @return 操作结果
         */
        public static Result of(ActionResult result) {
            return new Result(List.of(), result);
        }

        /**
         * 创建一个只涉及单个物品的结果。
         *
         * @param stack  操作涉及的物品堆栈
         * @param result 交互结果
         * @return 操作结果
         */
        public static Result of(ItemStack stack, ActionResult result) {
            return new Result(List.of(stack), result);
        }

        /**
         * 判断操作是否被接受（成功）。
         *
         * @return 如果交互结果被接受则返回 {@code true}
         */
        public boolean isAccepted() {
            return result.isAccepted();
        }
    }
}
