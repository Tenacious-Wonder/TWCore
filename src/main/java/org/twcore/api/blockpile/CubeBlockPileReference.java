package org.twcore.api.blockpile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twcore.blockpile.ClientCubeBlockPileReference;
import org.twcore.blockpile.ServerCubeBlockPileReference;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 方块堆引用接口 - 表示单个方块在方块堆结构中的位置信息
 *
 * <p>该接口为方块堆结构中的每个方块提供身份标识，记录方块所属的方块堆结构以及在该结构中的相对位置。
 * 每个参与方块堆的方块实体都应该持有此引用。</p>
 *
 * <h2>实现类型</h2>
 * <ul>
 *   <li><strong>服务端实现</strong> - {@link ServerCubeBlockPileReference}：包含完整的逻辑和状态管理</li>
 *   <li><strong>客户端实现</strong> - {@link ClientCubeBlockPileReference}：只读视图，用于渲染和显示</li>
 * </ul>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><strong>位置标识</strong> - 记录方块在方块堆结构中的相对位置</li>
 *   <li><strong>完整性检查</strong> - 检查所属方块堆结构是否完整</li>
 *   <li><strong>数据序列化</strong> - 支持NBT序列化，确保游戏重启后数据不丢失</li>
 *   <li><strong>类型验证</strong> - 验证方块类型是否与方块堆基础方块匹配</li>
 * </ul>
 *
 * @see ServerCubeBlockPileReference
 * @see ClientCubeBlockPileReference
 * @see CubeBlockPile
 */
@Deprecated
public interface CubeBlockPileReference {
    // 序列化键名
    String MASTER_POS_KEY = "MasterPos";
    String RELATIVE_POS_KEY = "RelativePos";
    String BASE_BLOCK_KEY = "BaseBlock";
    String STRUCTURE_WIDTH_KEY = "StructureWidth";
    String STRUCTURE_HEIGHT_KEY = "StructureHeight";
    String STRUCTURE_DEPTH_KEY = "StructureDepth";

    /**
     * 将引用序列化为NBT数据。
     *
     * <p><strong>序列化内容：</strong></p>
     * <ul>
     *   <li>主方块位置</li>
     *   <li>相对位置</li>
     *   <li>基础方块类型</li>
     *   <li>结构尺寸</li>
     * </ul>
     *
     * @return 包含引用数据的NBT化合物
     */
    @NotNull
    NbtCompound toNbt();

    /**
     * 检查传入的方块是否与方块堆结构的基础方块相同。
     *
     * @param block 要检查的方块
     * @return 如果方块类型匹配返回true，否则返回false
     */
    boolean matchesBlock(@Nullable Block block);

    /**
     * 检查传入的方块状态对应的方块是否与方块堆结构的基础方块相同。
     *
     * @param blockState 要检查的方块状态
     * @return 如果方块类型匹配返回true，否则返回false
     */
    boolean matchesBlockState(@Nullable BlockState blockState);

    /**
     * 检查传入的方块实体对应的方块是否与方块堆结构的基础方块相同。
     *
     * @param blockEntity 要检查的方块实体
     * @return 如果方块类型匹配返回true，否则返回false
     */
    boolean matchesBlockEntity(@Nullable BlockEntity blockEntity);

    /**
     * 判断当前引用位置是否为主方块（方块堆结构的起始坐标位置）。
     *
     * <p>主方块通常是方块堆结构的控制核心，可能具有特殊功能。</p>
     *
     * @return 如果是主方块返回true，否则返回false
     */
    boolean isMasterBlock();

    /**
     * 获取主方块的世界坐标。
     *
     * <p>主方块坐标是方块堆结构的唯一标识，用于查找和管理结构。</p>
     *
     * @return 主方块的世界坐标
     */
    @NotNull
    BlockPos getMasterWorldPos();

    /**
     * 获取当前方块的世界坐标。
     *
     * @return 当前方块的世界坐标
     */
    @NotNull
    BlockPos getWorldPos();

    /**
     * 获取当前方块在方块堆结构中的相对坐标。
     *
     * <p>相对坐标以主方块位置为原点(0,0,0)。</p>
     *
     * @return 相对坐标
     */
    @NotNull
    BlockPos getRelativePos();

    /**
     * 获取方块堆结构的基础方块类型。
     *
     * @return 基础方块类型
     */
    @NotNull
    Block getBaseBlock();

    /**
     * 获取方块堆结构的体积（包含的方块数量）。
     *
     * @return 结构体积
     */
    int getVolume();

    /**
     * 检查方块堆结构是否包含指定的世界坐标。
     *
     * @param worldPos 要检查的世界坐标
     * @return 如果包含该坐标返回true，否则返回false
     */
    boolean containsWorldPos(@NotNull BlockPos worldPos);

    /**
     * 检查方块堆结构的完整性。
     *
     * <p><strong>完整性条件：</strong></p>
     * <ul>
     *   <li>方块堆结构未被销毁</li>
     *   <li>所有位置都是正确的基础方块</li>
     *   <li>结构在世界中实际存在</li>
     * </ul>
     *
     * @return 如果结构完整返回true，否则返回false
     */
    boolean checkIntegrity();

    /**
     * 检查方块堆结构是否已被销毁。
     *
     * <p>被销毁的结构不能再使用，应该创建新的引用。</p>
     *
     * @return 如果已被销毁返回true，否则返回false
     */
    boolean isDisposed();

    /**
     * 检查引用是否有效。
     *
     * <p><strong>有效性条件：</strong></p>
     * <ul>
     *   <li>引用未被销毁</li>
     *   <li>关联的方块堆结构存在且完整</li>
     *   <li>当前位置包含在结构中</li>
     * </ul>
     *
     * @return 如果引用有效返回true，否则返回false
     */
    boolean isValid();

    /**
     * 安全地销毁这个引用。
     *
     * <p>销毁后引用将不再可用，应该丢弃并创建新的引用。</p>
     */
    void dispose();

    /**
     * 获取当前方块在X方向上的相对位置。
     *
     * @return X轴相对坐标
     */
    int getRelativeX();

    /**
     * 获取当前方块在Y方向上的相对位置。
     *
     * @return Y轴相对坐标
     */
    int getRelativeY();

    /**
     * 获取当前方块在Z方向上的相对位置。
     *
     * @return Z轴相对坐标
     */
    int getRelativeZ();

    /**
     * 获取方块堆结构的宽度（X轴方向）。
     *
     * @return 结构宽度
     */
    int getStructureWidth();

    /**
     * 获取方块堆结构的高度（Y轴方向）。
     *
     * @return 结构高度
     */
    int getStructureHeight();

    /**
     * 获取方块堆结构的深度（Z轴方向）。
     *
     * @return 结构深度
     */
    int getStructureDepth();
}