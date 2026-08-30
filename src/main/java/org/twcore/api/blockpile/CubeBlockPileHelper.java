package org.twcore.api.blockpile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.blockpile.CubeBlockPileManager;
import org.twcore.blockpile.ServerCubeBlockPileReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 方块堆操作助手 - 处理方块放置和破坏时的方块堆逻辑
 *
 * <p>该类作为方块堆系统的协调器，负责处理方块生命周期事件并维护方块堆结构的完整性。
 * 它提供了自动合并、拆分和引用更新等核心功能。</p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li><strong>事件处理</strong> - 处理方块放置、破坏和邻居更新事件</li>
 *   <li><strong>自动合并</strong> - 执行多轮合并，最大化方块堆结构</li>
 *   <li><strong>引用更新</strong> - 更新实现{@link CubeBlockPileEntity}的方块实体中的方块堆引用</li>
 *   <li><strong>完整性维护</strong> - 确保方块堆结构在变化时保持正确状态</li>
 *   <li><strong>调试工具</strong> - 提供强制更新和修复功能</li>
 * </ul>
 *
 * @see CubeBlockPileEntity
 * @see CubeBlockPile
 * @see CubeBlockPileManager
 */
@Deprecated
public class CubeBlockPileHelper {
    private static final Logger LOGGER = TWCore.LOGGER;

    /** 最大合并轮数，防止无限循环 */
    private static final int MAX_MERGE_ROUNDS = 10;

    /**
     * 处理方块放置事件，创建新的方块堆结构并尝试与相邻结构合并。
     *
     * @param world      世界实例
     * @param pos        方块位置
     * @param coreBlock  核心方块类型
     *
     * @see CubeBlockPileEntity
     * @see #performMultiRoundMerging(CubeBlockPile)
     * @apiNote 需要在使用了方块堆系统的对应方块类中重写
     * {@link net.minecraft.block.AbstractBlock#onBlockAdded(BlockState, World, BlockPos, BlockState, boolean)}
     * 方法主动调用此方法。
     */
    public static void onBlockPlaced(World world, BlockPos pos, Block coreBlock) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(pos, "Position cannot be null");
        Objects.requireNonNull(coreBlock, "Core block cannot be null");

        // 检查该位置是否已经有方块堆
        CubeBlockPile existing = CubeBlockPileManager.findCubeBlockPile(world, pos);
        if (existing != null && !existing.isDisposed()) {
            // 位置已有方块堆，检查完整性
            List<CubeBlockPile> newBlocks = existing.checkAndSplitIntegrity();
            updateBlockEntityReferences(world, newBlocks);
            return;
        }

        // 创建新的单方块堆
        CubeBlockPile newCubeBlockPile = createSingleBlockCubeBlockPile(world, pos, coreBlock);
        if (newCubeBlockPile == null) {
            LOGGER.error("Failed to create single block CubeBlockPile at {}", pos);
            return;
        }

        // 更新当前方块的引用
        updateBlockEntityReference(world, pos, newCubeBlockPile);

        // 尝试与相邻的方块堆合并
        performMultiRoundMerging(newCubeBlockPile);
    }

    /**
     * 处理方块破坏事件，拆分受影响的方块堆结构并尝试重新合并。
     *
     * @param world      世界实例
     * @param pos        被破坏的方块位置
     * @param coreBlock  核心方块类型
     * @apiNote 需要在使用了方块堆系统的对应方块类中重写
     * {@link net.minecraft.block.AbstractBlock#onStateReplaced(BlockState, World, BlockPos, BlockState, boolean)}
     * 方法主动调用此方法。
     */
    public static void onBlockBroken(World world, BlockPos pos, Block coreBlock) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(pos, "Position cannot be null");
        Objects.requireNonNull(coreBlock, "Core block cannot be null");

        // 找到包含该位置的方块堆
        CubeBlockPile cubeBlockPile = CubeBlockPileManager.findCubeBlockPile(world, pos);
        if (cubeBlockPile == null || cubeBlockPile.isDisposed()) {
            return;
        }

        // 检查并拆分方块堆
        List<CubeBlockPile> newBlocks = cubeBlockPile.checkAndSplitIntegrity();

        // 更新所有新方块堆的引用
        updateBlockEntityReferences(world, newBlocks);

        // 尝试合并新创建的方块堆（多轮合并）
        for (CubeBlockPile newBlock : newBlocks) {
            performMultiRoundMerging(newBlock);
        }
    }

    /**
     * 处理相邻方块更新事件，验证方块堆结构的完整性。
     *
     * @param world      世界实例
     * @param pos        受影响的核心方块位置
     * @param coreBlock  核心方块类型
     * @apiNote 需要在使用了方块堆系统的对应方块类中重写
     * {@link net.minecraft.block.AbstractBlock#neighborUpdate(BlockState, World, BlockPos, Block, BlockPos, boolean)}
     * 方法主动调用此方法。
     */
    public static void onNeighborUpdate(World world, BlockPos pos, Block coreBlock) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(pos, "Position cannot be null");
        Objects.requireNonNull(coreBlock, "Core block cannot be null");

        // 找到包含该位置的方块堆
        CubeBlockPile cubeBlockPile = CubeBlockPileManager.findCubeBlockPile(world, pos);
        if (cubeBlockPile == null || cubeBlockPile.isDisposed()) {
            return;
        }

        // 检查方块堆的完整性
        if (!cubeBlockPile.checkIntegrity()) {
            List<CubeBlockPile> newBlocks = cubeBlockPile.checkAndSplitIntegrity();
            updateBlockEntityReferences(world, newBlocks);

            // 尝试合并新创建的方块堆
            for (CubeBlockPile newBlock : newBlocks) {
                performMultiRoundMerging(newBlock);
            }
        }
    }

    /**
     * 执行多轮合并操作，直到无法再合并为止。
     *
     * @param initialBlock 初始的方块堆结构
     */
    public static void performMultiRoundMerging(CubeBlockPile initialBlock) {
        Objects.requireNonNull(initialBlock, "Initial block cannot be null");

        if (initialBlock.isDisposed()) {
            return;
        }

        CubeBlockPile current = initialBlock;
        boolean mergedInThisRound;
        int roundCount = 0;

        do {
            mergedInThisRound = false;
            roundCount++;

            List<CubeBlockPile> neighbors = findAdjacentCubeBlockPiles(current);

            for (CubeBlockPile neighbor : neighbors) {
                if (neighbor.isDisposed() || neighbor == current) {
                    continue;
                }

                try {
                    CubeBlockPile merged = CubeBlockPile.combine(current, neighbor);

                    if (merged != null) {
                        // 合并成功，更新引用
                        updateBlockEntityReferences((World) merged.getWorld(), List.of(merged));

                        current = merged;
                        mergedInThisRound = true;

                        // 合并后重新查找邻居，因为情况可能发生了变化
                        break;
                    }

                } catch (IllegalArgumentException e) {
                    // 继续尝试与其他邻居合并
                } catch (Exception e) {
                    LOGGER.error("Unexpected error during merge: {}", e.getMessage());
                    // 发生意外错误，停止合并
                    break;
                }
            }

            if (roundCount >= MAX_MERGE_ROUNDS) {
                LOGGER.warn("Reached maximum merge rounds for CubeBlockPile at {}",
                        current.getMasterPos());
                break;
            }

        } while (mergedInThisRound);
    }

    /**
     * 批量更新多个方块堆结构中所有方块实体的引用。
     *
     * @param world        世界实例
     * @param cubeBlockPiles  需要更新引用的方块堆结构列表
     */
    public static void updateBlockEntityReferences(World world, List<CubeBlockPile> cubeBlockPiles) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(cubeBlockPiles, "CubeBlockPiles list cannot be null");

        for (CubeBlockPile cubeBlockPile : cubeBlockPiles) {
            if (cubeBlockPile.isDisposed()) {
                continue;
            }

            updateBlockEntityReferencesForCubeBlockPile(world, cubeBlockPile);
        }
    }

    /**
     * 更新单个方块堆结构中所有方块实体的引用。
     *
     * @param world       世界实例
     * @param cubeBlockPile  需要更新引用的方块堆结构
     */
    public static void updateBlockEntityReferencesForCubeBlockPile(World world, CubeBlockPile cubeBlockPile) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(cubeBlockPile, "CubeBlockPile cannot be null");

        if (cubeBlockPile.isDisposed()) {
            throw new IllegalStateException("Cannot update references for disposed CubeBlockPile");
        }

        CubeBlockPile.PatternRange range = cubeBlockPile.getRange();
        BlockPos start = range.getStart();
        BlockPos end = cubeBlockPile.getEndPos();

        int updatedCount = 0;
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (updateBlockEntityReference(world, pos, cubeBlockPile)) {
                        updatedCount++;
                    }
                }
            }
        }
    }

    /**
     * 更新指定位置的方块实体引用。
     *
     * @param world       世界实例
     * @param pos         方块位置
     * @param cubeBlockPile  关联的方块堆结构
     * @return 如果成功更新了引用返回true，否则返回false
     */
    public static boolean updateBlockEntityReference(World world, BlockPos pos, CubeBlockPile cubeBlockPile) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(pos, "Position cannot be null");
        Objects.requireNonNull(cubeBlockPile, "CubeBlockPile cannot be null");

        // 只在服务端处理
        if (world.isClient) {
            return false;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof CubeBlockPileEntity cubeBlockPileEntity) {
            // 保存旧引用用于通知
             CubeBlockPileReference oldReference = cubeBlockPileEntity.getCubeBlockPileReference();

            // 创建新的引用
            ServerCubeBlockPileReference newRef = ServerCubeBlockPileReference.fromWorldPos(cubeBlockPile, pos);
            if (newRef != null) {
                cubeBlockPileEntity.setCubeBlockPileReference(newRef);

                // 通知引用变化
                cubeBlockPileEntity.onCubeBlockPileChanged(oldReference, newRef);
                return true;
            } else {
                LOGGER.warn("Failed to create CubeBlockPileReference for CubeBlockPileEntity at {}", pos);
            }
        }
        return false;
    }

    /**
     * 强制更新指定区域内所有方块实体的方块堆引用。
     *
     * @param world   世界实例
     * @param center  区域中心位置
     * @param radius  扫描半径（曼哈顿距离）
     */
    public static void forceUpdateReferencesInArea(World world, BlockPos center, int radius) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(center, "Center position cannot be null");

        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }

        // 获取区域内的所有实现了ICubeBlockPileEntity的方块实体
        List<BlockPos> multiBlockEntities = new ArrayList<>();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof CubeBlockPileEntity) {
                        multiBlockEntities.add(pos);
                    }
                }
            }
        }

        // 为每个方块实体重新创建引用
        int updatedCount = 0;
        int clearedCount = 0;

        for (BlockPos pos : multiBlockEntities) {
            CubeBlockPile cubeBlockPile = CubeBlockPileManager.findCubeBlockPile(world, pos);
            if (cubeBlockPile != null && !cubeBlockPile.isDisposed()) {
                if (updateBlockEntityReference(world, pos, cubeBlockPile)) {
                    updatedCount++;
                }
            } else {
                // 如果没有找到方块堆，清除引用
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof CubeBlockPileEntity cubeBlockPileEntity) {
                    CubeBlockPileReference oldReference = cubeBlockPileEntity.getCubeBlockPileReference();
                    cubeBlockPileEntity.setCubeBlockPileReference(null);
                    cubeBlockPileEntity.onCubeBlockPileChanged(oldReference, null);
                    clearedCount++;
                }
            }
        }
    }

    /**
     * 清除指定位置的方块堆引用。
     * 用于当方块实体需要明确断开与方块堆的关联时。
     *
     * @param world 世界实例
     * @param pos   方块位置
     */
    public static void clearCubeBlockPileReference(World world, BlockPos pos) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(pos, "Position cannot be null");

        if (world.isClient) {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof CubeBlockPileEntity cubeBlockPileEntity) {
            CubeBlockPileReference oldReference = cubeBlockPileEntity.getCubeBlockPileReference();
            cubeBlockPileEntity.setCubeBlockPileReference(null);
            cubeBlockPileEntity.onCubeBlockPileChanged(oldReference, null);
        }
    }

    /**
     * 创建单方块方块堆结构。
     */
    public static CubeBlockPile createSingleBlockCubeBlockPile(World world, BlockPos pos, Block coreBlock) {
        try {
            return CubeBlockPile.builder()
                    .world(world)
                    .baseBlock(coreBlock)
                    .range(pos, 1, 1, 1)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to create single block CubeBlockPile at {}: {}", pos, e.getMessage());
            return null;
        }
    }

    /**
     * 查找与指定方块堆结构相邻的所有其他结构。
     */
    public static List<CubeBlockPile> findAdjacentCubeBlockPiles(CubeBlockPile cubeBlockPile) {
        List<CubeBlockPile> neighbors = new ArrayList<>();
        World world = (World) cubeBlockPile.getWorld();
        CubeBlockPile.PatternRange range = cubeBlockPile.getRange();
        BlockPos start = range.getStart();
        BlockPos end = range.getEnd();

        // 检查六个方向的相邻位置
        // 西方向
        BlockPos westFaceStart = start.west();
        BlockPos westFaceEnd = new BlockPos(start.getX() - 1, end.getY(), end.getZ());
        checkDirection(neighbors, world, cubeBlockPile, westFaceStart, westFaceEnd);

        // 东方向
        BlockPos eastFaceStart = end.east();
        BlockPos eastFaceEnd = new BlockPos(end.getX() + 1, end.getY(), end.getZ());
        checkDirection(neighbors, world, cubeBlockPile, eastFaceStart, eastFaceEnd);

        // 下方向
        BlockPos downFaceStart = start.down();
        BlockPos downFaceEnd = new BlockPos(end.getX(), start.getY() - 1, end.getZ());
        checkDirection(neighbors, world, cubeBlockPile, downFaceStart, downFaceEnd);

        // 上方向
        BlockPos upFaceStart = end.up();
        BlockPos upFaceEnd = new BlockPos(end.getX(), end.getY() + 1, end.getZ());
        checkDirection(neighbors, world, cubeBlockPile, upFaceStart, upFaceEnd);

        // 北方向
        BlockPos northFaceStart = start.north();
        BlockPos northFaceEnd = new BlockPos(end.getX(), end.getY(), start.getZ() - 1);
        checkDirection(neighbors, world, cubeBlockPile, northFaceStart, northFaceEnd);

        // 南方向
        BlockPos southFaceStart = end.south();
        BlockPos southFaceEnd = new BlockPos(end.getX(), end.getY(), end.getZ() + 1);
        checkDirection(neighbors, world, cubeBlockPile, southFaceStart, southFaceEnd);

        return neighbors;
    }

    /**
     * 在指定方向上查找相邻的方块堆结构。
     */
    public static void checkDirection(List<CubeBlockPile> neighbors, World world, CubeBlockPile cubeBlockPile,
                                      BlockPos faceStart, BlockPos faceEnd) {
        // 检查该方向上所有可能的位置
        for (int x = faceStart.getX(); x <= faceEnd.getX(); x++) {
            for (int y = faceStart.getY(); y <= faceEnd.getY(); y++) {
                for (int z = faceStart.getZ(); z <= faceEnd.getZ(); z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    CubeBlockPile neighbor = CubeBlockPileManager.findCubeBlockPile(world, checkPos);

                    if (neighbor != null && !neighbor.isDisposed() &&
                            neighbor.getBaseBlock() == cubeBlockPile.getBaseBlock() &&
                            !neighbors.contains(neighbor)) {
                        neighbors.add(neighbor);
                    }
                }
            }
        }
    }
}