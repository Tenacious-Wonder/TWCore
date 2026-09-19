package org.twcore.api.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * <h1>可放置物品的方块</h1>
 * <p>
 * 有些方块的主要用途就是“托着一件物品”——盘子里的一份菜、砧板上的一块肉、架子上的一个瓶子。
 * 本类是这类方块的基类：它与 {@link UpPlaceBlockEntity} 配合，把“手持物品右键放上去、
 * 再右键取下来”这套交互统一实现好，子类只需声明允许放置与取出的条件。
 * </p>
 *
 * <h2>交互流程</h2>
 * <p>玩家右键方块时按以下顺序尝试：</p>
 * <ol>
 *     <li>若 {@link #canFetched(UpPlaceBlockEntity, ItemStack)} 允许，先尝试<b>取出</b>方块上的物品；</li>
 *     <li>否则若 {@link #canPlace(UpPlaceBlockEntity, ItemStack)} 允许，尝试<b>放置</b>手中的物品；</li>
 *     <li>两者都不成立时返回 {@link ActionResult#FAIL}，交由其他逻辑处理。</li>
 * </ol>
 *
 * <h2>子类需要实现的契约</h2>
 * <ul>
 *     <li>{@link #getBaseShape} —— 方块本身的轮廓形状，方块上有物品时系统会自动并入物品形状；</li>
 *     <li>{@link #canFetched} —— 取出条件；</li>
 *     <li>{@link #canPlace} —— 放置条件。</li>
 * </ul>
 * <p>
 * 音效由构造器传入的 {@link UpSounds} 决定：默认动态音效会按物品材质挑选声音，
 * 也可以设为固定音效或完全静音。方块被破坏时，方块实体中的物品会自动掉落。
 * </p>
 *
 * @see UpPlaceBlockEntity
 * @see UpSounds
 */
public abstract class UpPlaceBlock extends BlockWithEntity {

    /** 放置与取出物品时使用的音效配置，由构造器指定。 */
    public final UpSounds upSounds;

    /**
     * 创建方块，并指定交互音效。
     *
     * @param settings 方块设置
     * @param upSounds 放置与取出时使用的音效配置
     */
    public UpPlaceBlock(Settings settings, UpSounds upSounds) {
        super(settings);
        this.upSounds = upSounds;
    }

    /**
     * 创建方块，使用默认的动态物品音效（{@link UpSounds#DYNAMIC}）。
     *
     * @param settings 方块设置
     */
    public UpPlaceBlock(Settings settings) {
        this(settings, UpSounds.DYNAMIC);
    }

    /**
     * 方块被替换或破坏时，把方块实体库存中的物品掉落出来，避免物品凭空消失。
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof Inventory inventory) {
                ItemScatterer.spawn(world, pos, inventory);
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    /**
     * 获取方块的轮廓形状：基础形状与方块上物品形状的并集。
     *
     * <p>这样放置上去的物品才能正确参与碰撞与选中轮廓。</p>
     */
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof UpPlaceBlockEntity blockEntity && !blockEntity.isEmpty()) {
            return VoxelShapes.union(
                    getBaseShape(state, world, pos, context),
                    blockEntity.getContentShape(state, world, pos, context)
            );
        }
        return getBaseShape(state, world, pos, context);
    }

    /**
     * 获取方块的基准轮廓形状。
     *
     * <p>子类必须实现此方法来定义方块本身的基本形状，物品形状会在此基础上叠加。</p>
     *
     * @param state   当前方块状态
     * @param world   方块所在的世界
     * @param pos     方块位置
     * @param context 形状计算上下文
     * @return 方块的基准轮廓形状
     */
    public abstract VoxelShape getBaseShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context);

    /**
     * 处理玩家右键交互：先尝试取出方块上的物品，再尝试把手中物品放上去。
     *
     * <p>取出与放置能否进行分别由 {@link #canFetched(UpPlaceBlockEntity, ItemStack)} 与
     * {@link #canPlace(UpPlaceBlockEntity, ItemStack)} 判定；两者都不可行时返回
     * {@link ActionResult#FAIL}。</p>
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack handStack = player.getStackInHand(hand);
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity instanceof UpPlaceBlockEntity upPlaceBlockEntity) {
            // 尝试取出物品
            if (canFetched(upPlaceBlockEntity, handStack)) {
                UpPlaceBlockEntity.Result fetchResult = upPlaceBlockEntity.tryFetchItem(player, hit);
                if (fetchResult.isAccepted()) {
                    upPlaceBlockEntity.onFetch(state, world, pos, player, hand, hit, fetchResult.opsStacks());
                    return fetchResult.result();
                }
            }

            // 尝试放置物品
            if (canPlace(upPlaceBlockEntity, handStack)) {
                UpPlaceBlockEntity.Result placeResult = upPlaceBlockEntity.tryAddItem(handStack, hit);
                if (placeResult.isAccepted()) {
                    upPlaceBlockEntity.onPlace(state, world, pos, player, hand, hit, handStack, placeResult.opsStacks());
                    return placeResult.result();
                }
            }
        }

        return ActionResult.FAIL;
    }

    /**
     * 检查当前条件下是否可以执行取出操作。
     *
     * <p>子类必须实现此方法来确定取出操作的触发条件，例如手中是否持有特定工具、
     * 方块上是否有物品等。</p>
     *
     * @param blockEntity 目标方块实体
     * @param handStack   玩家手中的物品堆栈
     * @return 如果可以取出物品则返回 true
     */
    public abstract boolean canFetched(UpPlaceBlockEntity blockEntity, ItemStack handStack);

    /**
     * 检查当前条件下是否可以执行放置操作。
     *
     * <p>子类必须实现此方法来确定放置操作的触发条件，例如手中物品是否有效、
     * 方块上是否还有空位等。</p>
     *
     * @param blockEntity 目标方块实体
     * @param handStack   玩家手中的物品堆栈
     * @return 如果可以放置物品则返回 true
     */
    public abstract boolean canPlace(UpPlaceBlockEntity blockEntity, ItemStack handStack);

    /**
     * 定义物品在 {@link UpPlaceBlock} 上放置和取出时的音效配置。
     * <p>
     * 提供三种预置模式：
     * <ul>
     *     <li><b>空音效 {@link #EMPTY}</b>：完全不播放任何声音。</li>
     *     <li><b>动态物品音效 {@link #DYNAMIC}</b>：根据交互的物品类型自动决定声音。
     *         此时记录中的 {@code placeSound} 和 {@code fetchSound} 仅作为动态获取失败时的兜底值。</li>
     *     <li><b>固定音效</b>：通过构造方法直接指定一个固定的 {@link SoundEvent}，播放时不再动态计算。</li>
     * </ul>
     *
     * @param placeSound 放置时使用的固定音效（动态模式下作为兜底）
     * @param fetchSound 取出时使用的固定音效（动态模式下作为兜底）
     */
    public record UpSounds(SoundEvent placeSound, SoundEvent fetchSound) {

        /** 空音效：不播放任何声音。 */
        public static final UpSounds EMPTY = new UpSounds(null, null);

        /**
         * 动态物品音效：根据交互物品的材质自动选择声音。
         * 兜底音效为石头放置/破坏音效。
         */
        public static final UpSounds DYNAMIC = new UpSounds(
                SoundEvents.BLOCK_STONE_PLACE,
                SoundEvents.BLOCK_STONE_BREAK
        );

        /**
         * 播放固定音效（仅在非动态模式下由 {@link UpPlaceBlockEntity#playSound} 调用）。
         * 如果内部音效为 {@code null} 则不会播放。
         *
         * @param world        当前世界
         * @param pos          播放位置
         * @param isPlaceSound {@code true} 播放放置音效，{@code false} 播放取出音效
         */
        public void playSound(World world, BlockPos pos, boolean isPlaceSound) {
            if (isPlaceSound) {
                playPlaceSound(world, pos);
            } else {
                playFetchSound(world, pos);
            }
        }

        /** 播放放置固定音效（服务端，音效非空时）。 */
        public void playPlaceSound(World world, BlockPos pos) {
            if (placeSound != null && !world.isClient) {
                world.playSound(null, pos, placeSound, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }

        /** 播放取出固定音效（服务端，音效非空时）。 */
        public void playFetchSound(World world, BlockPos pos) {
            if (fetchSound != null && !world.isClient) {
                world.playSound(null, pos, fetchSound, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}
