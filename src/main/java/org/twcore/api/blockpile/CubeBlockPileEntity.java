package org.twcore.api.blockpile;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.twcore.blockpile.ClientCubeBlockPileReference;
import org.twcore.blockpile.ServerCubeBlockPileReference;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 表示可以保存方块堆引用的方块实体接口。
 *
 * <p>任何需要与方块堆系统交互的方块实体都应该实现此接口，
 * 以便在方块堆结构变化时自动更新引用。</p>
 *
 * <h2>引用类型</h2>
 * <ul>
 *   <li><strong>服务端</strong> - 使用 {@link ServerCubeBlockPileReference}，包含完整逻辑</li>
 *   <li><strong>客户端</strong> - 使用 {@link ClientCubeBlockPileReference}，只读视图</li>
 * </ul>
 *
 * @see CubeBlockPileReference
 * @see ServerCubeBlockPileReference
 * @see ClientCubeBlockPileReference
 */
@Deprecated
public interface CubeBlockPileEntity {

    /**
     * 设置该方块实体所属的方块堆引用。
     *
     * <p><strong>注意：</strong></p>
     * <ul>
     *   <li>当方块被添加到方块堆结构时，引用不为null</li>
     *   <li>当方块从方块堆结构中移除时，引用为null</li>
     *   <li>实现应该处理引用变化时的状态更新</li>
     *   <li>服务端和客户端可能持有不同类型的引用</li>
     * </ul>
     *
     * @param reference 方块堆引用，如果为null表示不属于任何方块堆
     */
    void setCubeBlockPileReference(@Nullable CubeBlockPileReference reference);

    /**
     * 获取该方块实体所属的方块堆引用。
     *
     * @return 方块堆引用，如果不属于任何方块堆返回null
     */
    @Nullable
    CubeBlockPileReference getCubeBlockPileReference();

    /**
     * 获取该方块实体的位置。
     * 用于在方块堆系统中定位和识别方块。
     *
     * @return 方块实体的世界位置
     */
    BlockPos getCubeBlockPilePos();

    /**
     * 检查该方块实体是否属于一个有效的方块堆结构。
     *
     * <p><strong>有效性条件：</strong></p>
     * <ul>
     *   <li>方块堆引用不为null</li>
     *   <li>引用本身是有效的（{@link CubeBlockPileReference#isValid()}）</li>
     *   <li>引用位置与方块实体位置匹配</li>
     * </ul>
     *
     * @return 如果属于有效的方块堆结构返回true，否则返回false
     */
    default boolean hasValidCubeBlockPile() {
        CubeBlockPileReference ref = getCubeBlockPileReference();
        return ref != null && ref.isValid() && ref.getWorldPos().equals(getCubeBlockPilePos());
    }

    /**
     * 检查方块堆引用是否为服务端引用。
     *
     * @return 如果是服务端引用返回true，客户端引用返回false
     */
    default boolean isServerReference() {
        CubeBlockPileReference ref = getCubeBlockPileReference();
        return ref != null && this instanceof ServerCubeBlockPileReference;
    }

    /**
     * 检查方块堆引用是否为客户端引用。
     *
     * @return 如果是客户端引用返回true，服务端引用返回false
     */
    default boolean isClientReference() {
        CubeBlockPileReference ref = getCubeBlockPileReference();
        return ref != null && this instanceof ClientCubeBlockPileReference;
    }

    /**
     * 当方块堆结构发生变化时调用此方法。
     * 实现可以在此方法中更新外观、状态或其他依赖方块堆的功能。
     *
     * <p><strong>触发时机：</strong></p>
     * <ul>
     *   <li>方块被添加到方块堆结构时</li>
     *   <li>方块从方块堆结构中移除时</li>
     *   <li>方块堆结构被拆分或合并时</li>
     *   <li>引用类型发生变化时（服务端/客户端）</li>
     * </ul>
     *
     * @param oldReference 变化前的方块堆引用（可能为null）
     * @param newReference 变化后的方块堆引用（可能为null）
     */
    default void onCubeBlockPileChanged(@Nullable CubeBlockPileReference oldReference,
                                     @Nullable CubeBlockPileReference newReference) {
    }

    /**
     * 获取方块堆结构信息用于显示。
     * 主要用于GUI、提示文本等显示用途。
     *
     * @return 结构信息字符串，如果没有方块堆引用返回null
     */
    @Nullable
    default String getCubeBlockPileInfo() {
        CubeBlockPileReference ref = getCubeBlockPileReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }

        return String.format("CubeBlockPile %dx%dx%d at %s",
                ref.getStructureWidth(),
                ref.getStructureHeight(),
                ref.getStructureDepth(),
                ref.getMasterWorldPos());
    }
}