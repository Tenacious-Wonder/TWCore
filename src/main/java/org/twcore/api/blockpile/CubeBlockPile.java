package org.twcore.api.blockpile;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.blockpile.CubeBlockPileManager;
import org.twcore.blockpile.ServerCubeBlockPileReference;

import java.util.*;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 管理由同种方块组成的立方体区域的方块堆结构实例。
 *
 * <p>该类代表世界中一个具体的方块堆结构，自动处理结构的完整性检查、拆分和合并。
 * 当结构中的方块被破坏时，会自动拆分成更小的完整结构；当相邻结构满足条件时，会自动合并。</p>
 *
 * <h2>核心特性</h2>
 * <ul>
 *   <li><strong>自动完整性维护</strong> - 当方块缺失时自动拆分，相邻时自动合并</li>
 *   <li><strong>坐标转换</strong> - 支持相对坐标与世界坐标的相互转换</li>
 *   <li><strong>大小限制</strong> - 最大支持{@value #MAX_SIZE}×{@value #MAX_SIZE}×{@value #MAX_SIZE}的方块堆</li>
 *   <li><strong>资源管理</strong> - 实现{@link AutoCloseable}，确保正确释放资源</li>
 * </ul>
 *
 * <h2>使用注意事项</h2>
 * <p>方块堆一般用于带有方块实体的方块，如果使用没有方块实体的方块创建方块堆，
 * 可能无法完整地使用该类的功能。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建3x3x3的石头方块堆
 * CubeBlockPile stonePile = CubeBlockPile.builder()
 *     .world(world)
 *     .baseBlock(Blocks.STONE)
 *     .range(startPos, 3, 3, 3)
 *     .build();
 *
 * // 检查完整性
 * if (!stonePile.checkIntegrity()) {
 *     List<CubeBlockPile> newPiles = stonePile.checkAndSplitIntegrity();
 * }
 * }</pre>
 *
 * @see CubeBlockPileManager
 * @see CubeBlockPileHelper
 */
@Deprecated
public class CubeBlockPile implements AutoCloseable {
    private static final Logger LOGGER = TWCore.LOGGER;

    /** 最大方块堆尺寸限制 */
    public static final int MAX_SIZE = 100;

    protected final WorldView world;
    protected final Block baseBlock;
    protected final PatternRange range;
    protected final BlockPos masterPos;
    protected boolean disposed = false;

    /**
     * 创建方块堆结构实例。
     *
     * @param world     世界视图
     * @param baseBlock 基础方块类型
     * @param range     结构范围
     * @throws IllegalArgumentException 如果尺寸超过最大限制
     * @throws IllegalStateException    如果注册失败
     */
    protected CubeBlockPile(WorldView world, Block baseBlock, PatternRange range) {
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.baseBlock = Objects.requireNonNull(baseBlock, "Base block cannot be null");
        this.range = Objects.requireNonNull(range, "Range cannot be null");
        this.masterPos = range.getStart();

        validateSize(range);
        registerToManager();
    }

    /**
     * 验证结构尺寸是否在允许范围内。
     */
    private void validateSize(PatternRange range) {
        if (range.getWidth() > MAX_SIZE || range.getHeight() > MAX_SIZE || range.getDepth() > MAX_SIZE) {
            throw new IllegalArgumentException(
                    String.format("CubeBlockPile size %dx%dx%d exceeds maximum allowed size %dx%dx%d",
                            range.getWidth(), range.getHeight(), range.getDepth(),
                            MAX_SIZE, MAX_SIZE, MAX_SIZE));
        }
    }

    /**
     * 将方块堆结构注册到管理器。
     */
    private void registerToManager() {
        if (!CubeBlockPileManager.registerCubeBlockPile(this)) {
            CubeBlockPile existing = CubeBlockPileManager.findCubeBlockPile(world, masterPos);
            String errorMsg = existing != null
                    ? String.format("Failed to register CubeBlockPile at position %s. Position already occupied by CubeBlockPile with base block %s",
                    masterPos, existing.getBaseBlock())
                    : String.format("Failed to register CubeBlockPile at position %s for unknown reason", masterPos);
            throw new IllegalStateException(errorMsg);
        }
    }

    /**
     * 方块堆结构构建器，提供流畅的API创建实例。
     */
    public static class Builder {
        private WorldView world;
        private Block baseBlock;
        private PatternRange range;

        /**
         * 设置世界。
         */
        public Builder world(WorldView world) {
            this.world = world;
            return this;
        }

        /**
         * 设置基础方块类型。
         */
        public Builder baseBlock(Block baseBlock) {
            this.baseBlock = baseBlock;
            return this;
        }

        /**
         * 设置结构范围。
         */
        public Builder range(PatternRange range) {
            this.range = range;
            return this;
        }

        /**
         * 设置结构范围。
         *
         * @param start  起始坐标
         * @param width  宽度（X轴方向）
         * @param height 高度（Y轴方向）
         * @param depth  深度（Z轴方向）
         * @throws IllegalArgumentException 如果尺寸超过最大限制
         */
        public Builder range(BlockPos start, int width, int height, int depth) {
            if (width > MAX_SIZE || height > MAX_SIZE || depth > MAX_SIZE) {
                throw new IllegalArgumentException(
                        String.format("Requested size %dx%dx%d exceeds maximum allowed size %dx%dx%d",
                                width, height, depth, MAX_SIZE, MAX_SIZE, MAX_SIZE));
            }
            this.range = new PatternRange(start, width, height, depth);
            return this;
        }

        /**
         * 构建方块堆结构实例。
         */
        public CubeBlockPile build() {
            return new CubeBlockPile(world, baseBlock, range);
        }
    }

    /**
     * 创建新的构造器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 检查方块堆的完整性，如果不完整则自动拆分为完整的子结构。
     *
     * @return 拆分后的新方块堆列表，如果结构完整则返回空列表
     */
    public List<CubeBlockPile> checkAndSplitIntegrity() {
        if (disposed) {
            LOGGER.warn("Attempted to check integrity of disposed CubeBlockPile at {}", masterPos);
            return Collections.emptyList();
        }

        List<BlockPos> validBlocks = findValidBlocks();

        if (validBlocks.size() == getVolume()) {
            return Collections.emptyList();
        }

        dispose();
        return splitCubeBlockPile(validBlocks);
    }

    /**
     * 检查结构是否完整（所有方块都是基础方块类型）。
     *
     * @return 如果结构完整返回true，否则返回false
     */
    public boolean checkIntegrity() {
        if (disposed) {
            LOGGER.warn("Attempted to check integrity of disposed CubeBlockPile at {}", masterPos);
            return false;
        }

        BlockPos start = range.getStart();
        BlockPos end = getEndPos();

        int invalidCount = 0;
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    if (block != baseBlock) {
                        invalidCount++;
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Found invalid block at {}: expected {}, found {}", pos, baseBlock, block);
                        }
                    }
                }
            }
        }

        if (invalidCount > 0) {
            LOGGER.warn("CubeBlockPile at {} has {} invalid blocks out of {}", masterPos, invalidCount, getVolume());
            return false;
        }

        return true;
    }

    /**
     * 查找范围内所有有效的方块位置。
     */
    private List<BlockPos> findValidBlocks() {
        List<BlockPos> validBlocks = new ArrayList<>();
        BlockPos start = range.getStart();
        BlockPos end = getEndPos();

        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (world.getBlockState(pos).getBlock() == baseBlock) {
                        validBlocks.add(pos);
                    }
                }
            }
        }
        return validBlocks;
    }

    /**
     * 将不完整的方块堆拆分为多个完整的小方块堆。
     */
    private List<CubeBlockPile> splitCubeBlockPile(List<BlockPos> validBlocks) {
        if (validBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<CubeBlockPile> result = splitCubeBlockPileOptimized(validBlocks);
        if (result.isEmpty()) {
            LOGGER.warn("Optimized decomposition failed, falling back to original algorithm");
            result = splitCubeBlockPileFallback(validBlocks);
        }
        return result;
    }

    /**
     * 使用三维立方体分解算法进行优化拆分。
     */
    private List<CubeBlockPile> splitCubeBlockPileOptimized(List<BlockPos> validBlocks) {
        List<CubeDecomposition> decomposedCubes = decomposeIntoSolidCubes(validBlocks);
        List<CubeBlockPile> result = new ArrayList<>();

        for (CubeDecomposition cube : decomposedCubes) {
            if (cube.isValid()) {
                CubeBlockPile newCubeBlockPile = createCubeBlockPileFromCube(cube);
                if (newCubeBlockPile != null) {
                    result.add(newCubeBlockPile);
                }
            }
        }

        return result;
    }

    /**
     * 回退拆分算法：使用三维连通组件算法。
     */
    private List<CubeBlockPile> splitCubeBlockPileFallback(List<BlockPos> validBlocks) {
        List<List<BlockPos>> connectedComponents = findConnectedComponents(validBlocks);
        List<CubeBlockPile> result = new ArrayList<>();

        for (List<BlockPos> component : connectedComponents) {
            if (!component.isEmpty()) {
                CubeBlockPile newCubeBlockPile = createCubeBlockPileFromComponent(component);
                if (newCubeBlockPile != null) {
                    result.add(newCubeBlockPile);
                }
            }
        }
        return result;
    }

    /**
     * 使用广度优先搜索找到连通的方块组件。
     */
    private List<List<BlockPos>> findConnectedComponents(List<BlockPos> validBlocks) {
        Set<BlockPos> visited = new HashSet<>();
        List<List<BlockPos>> components = new ArrayList<>();
        Set<BlockPos> validSet = new HashSet<>(validBlocks);

        // 定义六个方向：上下左右前后
        int[][] directions = {
                {1, 0, 0}, {-1, 0, 0},  // 东西
                {0, 1, 0}, {0, -1, 0},  // 上下
                {0, 0, 1}, {0, 0, -1}   // 南北
        };

        for (BlockPos block : validBlocks) {
            if (!visited.contains(block)) {
                List<BlockPos> component = new ArrayList<>();
                Queue<BlockPos> queue = new LinkedList<>();

                queue.add(block);
                visited.add(block);
                component.add(block);

                while (!queue.isEmpty()) {
                    BlockPos current = queue.poll();
                    for (int[] dir : directions) {
                        BlockPos neighbor = new BlockPos(
                                current.getX() + dir[0],
                                current.getY() + dir[1],
                                current.getZ() + dir[2]);
                        if (validSet.contains(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            component.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                components.add(component);
            }
        }
        return components;
    }

    /**
     * 将连通区域分解为实心立方体（使用贪心算法）。
     */
    private List<CubeDecomposition> decomposeIntoSolidCubes(List<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<BlockPos> blockSet = new HashSet<>(blocks);
        BlockPos min = findMinBounds(blocks);
        BlockPos max = findMaxBounds(blocks);
        Set<BlockPos> covered = new HashSet<>();
        List<CubeDecomposition> cubes = new ArrayList<>();

        // 使用优先级队列按体积从大到小处理
        PriorityQueue<CubeCandidate> candidateQueue = new PriorityQueue<>(
                (a, b) -> Integer.compare(b.volume, a.volume));
        generateCubeCandidates(blockSet, min, max, candidateQueue);

        // 贪心选择：总是选择当前最大的可用立方体
        while (!candidateQueue.isEmpty() && covered.size() < blocks.size()) {
            CubeCandidate candidate = candidateQueue.poll();
            if (isCubeAvailable(blockSet, covered, candidate)) {
                CubeDecomposition cube = new CubeDecomposition(
                        candidate.start, candidate.size, candidate.size, candidate.size);
                cubes.add(cube);
                markCubeAsCovered(covered, candidate);
                LOGGER.trace("Selected cube: {} size {} (volume: {})", candidate.start, candidate.size, candidate.volume);
            }
        }

        coverRemainingBlocks(blocks, covered, cubes);
        return cubes;
    }

    /**
     * 生成立方体候选。
     */
    private void generateCubeCandidates(Set<BlockPos> blockSet, BlockPos min, BlockPos max,
                                        PriorityQueue<CubeCandidate> queue) {
        int maxPossibleSize = Math.min(
                max.getX() - min.getX() + 1,
                Math.min(max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1));

        for (int size = maxPossibleSize; size >= 1; size--) {
            for (int x = min.getX(); x <= max.getX() - size + 1; x++) {
                for (int y = min.getY(); y <= max.getY() - size + 1; y++) {
                    for (int z = min.getZ(); z <= max.getZ() - size + 1; z++) {
                        BlockPos start = new BlockPos(x, y, z);
                        if (isSolidCube(blockSet, start, size)) {
                            queue.offer(new CubeCandidate(start, size));
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查给定区域是否是实心立方体。
     */
    private boolean isSolidCube(Set<BlockPos> blockSet, BlockPos start, int size) {
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    BlockPos pos = new BlockPos(
                            start.getX() + dx,
                            start.getY() + dy,
                            start.getZ() + dz);
                    if (!blockSet.contains(pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 从连通的方块组件创建新的方块堆。
     */
    private CubeBlockPile createCubeBlockPileFromComponent(List<BlockPos> component) {
        if (component.isEmpty()) {
            return null;
        }

        // 找到组件的最小和最大坐标
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : component) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 检查是否为完整矩形区域
        if (isRectangularRegionValid(component, minX, minY, minZ, maxX, maxY, maxZ)) {
            return createRectangularCubeBlockPile(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            return createMinimalCubeBlockPile(component, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /**
     * 创建完整的矩形方块堆结构。
     */
    private CubeBlockPile createRectangularCubeBlockPile(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos newStart = new BlockPos(minX, minY, minZ);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        try {
            return CubeBlockPile.builder()
                    .world(world)
                    .baseBlock(baseBlock)
                    .range(newStart, width, height, depth)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to create CubeBlockPile from component: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 创建包含所有方块的最小矩形方块堆。
     */
    private CubeBlockPile createMinimalCubeBlockPile(List<BlockPos> component, int minX, int minY, int minZ,
                                                  int maxX, int maxY, int maxZ) {
        BlockPos newStart = new BlockPos(minX, minY, minZ);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        try {
            CubeBlockPile cubeBlockPile = CubeBlockPile.builder()
                    .world(world)
                    .baseBlock(baseBlock)
                    .range(newStart, width, height, depth)
                    .build();

            LOGGER.warn("Created non-solid CubeBlockPile at {} with size {}x{}x{} containing {} blocks",
                    newStart, width, height, depth, component.size());
            return cubeBlockPile;
        } catch (Exception e) {
            LOGGER.error("Failed to create minimal CubeBlockPile: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查矩形区域是否完全由有效方块填充。
     */
    private boolean isRectangularRegionValid(List<BlockPos> component, int minX, int minY, int minZ,
                                             int maxX, int maxY, int maxZ) {
        Set<BlockPos> componentSet = new HashSet<>(component);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!componentSet.contains(new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 根据相对位置计算世界坐标。
     *
     * @param relativeX X轴相对坐标
     * @param relativeY Y轴相对坐标
     * @param relativeZ Z轴相对坐标
     * @return 对应的世界坐标
     * @throws IllegalStateException 如果结构已被销毁
     * @throws IllegalArgumentException 如果相对坐标超出范围
     */
    public BlockPos getWorldPos(int relativeX, int relativeY, int relativeZ) {
        if (disposed) {
            throw new IllegalStateException("CubeBlockPile at " + masterPos + " has been disposed");
        }

        if (relativeX < 0 || relativeX >= range.getWidth() ||
                relativeY < 0 || relativeY >= range.getHeight() ||
                relativeZ < 0 || relativeZ >= range.getDepth()) {
            String errorMsg = String.format(
                    "Relative coordinates (%d, %d, %d) out of range. Valid range: [0-%d, 0-%d, 0-%d]",
                    relativeX, relativeY, relativeZ,
                    range.getWidth() - 1, range.getHeight() - 1, range.getDepth() - 1);
            LOGGER.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        return new BlockPos(
                range.getStart().getX() + relativeX,
                range.getStart().getY() + relativeY,
                range.getStart().getZ() + relativeZ);
    }

    /**
     * 获取相对坐标对应的世界坐标。
     */
    public BlockPos getWorldPos(BlockPos relativePos) {
        return getWorldPos(relativePos.getX(), relativePos.getY(), relativePos.getZ());
    }

    /**
     * 获取世界位置对应的相对位置。
     */
    @Nullable
    public BlockPos getRelativePosFromWorld(BlockPos worldPos) {
        if (!containsWorldPos(worldPos)) {
            return null;
        }
        return new BlockPos(
                worldPos.getX() - getMasterPos().getX(),
                worldPos.getY() - getMasterPos().getY(),
                worldPos.getZ() - getMasterPos().getZ());
    }

    /**
     * 将当前方块堆与另一个方块堆拼接。
     */
    public CubeBlockPile combineWith(@NotNull CubeBlockPile other) {
        if (disposed) {
            throw new IllegalStateException("This CubeBlockPile at " + masterPos + " has been disposed");
        }
        if (other.disposed) {
            throw new IllegalStateException("Other CubeBlockPile at " + other.masterPos + " has been disposed");
        }

        return combine(this, other);
    }

    /**
     * 拼接两个方块堆。
     */
    public static CubeBlockPile combine(@NotNull CubeBlockPile first, @NotNull CubeBlockPile second) {
        if (first.disposed || second.disposed) {
            throw new IllegalStateException("Cannot combine disposed CubeBlockPiles");
        }

        validateCombineConditions(first, second);
        PatternRange newRange = calculateCombinedRange(first, second);
        MergeDirection direction = findMergeDirection(first.range, second.range);

        if (direction == null) {
            return null;
        }

        return createCombinedCubeBlockPile(first, second, newRange);
    }

    /**
     * 手动拆分方块堆为两个部分。
     *
     * @param axis 拆分轴：'x', 'y', 或 'z'
     * @param splitPosition 在指定轴上的拆分位置（相对坐标）
     * @return 拆分后的两个方块堆，如果拆分失败返回空列表
     */
    public List<CubeBlockPile> splitAlongPlane(char axis, int splitPosition) {
        if (disposed) {
            LOGGER.warn("Attempted to split disposed CubeBlockPile at {}", masterPos);
            return Collections.emptyList();
        }

        if (splitPosition <= 0 || splitPosition >= getAxisSize(axis)) {
            LOGGER.error("Invalid split position {} for axis {}", splitPosition, axis);
            return Collections.emptyList();
        }

        try {
            BlockPos start = range.getStart();
            int width = range.getWidth();
            int height = range.getHeight();
            int depth = range.getDepth();

            CubeBlockPile firstPart, secondPart;

            switch (axis) {
                case 'x':
                    firstPart = createSubCubeBlockPile(start, splitPosition, height, depth);
                    BlockPos secondStart = new BlockPos(start.getX() + splitPosition, start.getY(), start.getZ());
                    secondPart = createSubCubeBlockPile(secondStart, width - splitPosition, height, depth);
                    break;
                case 'y':
                    firstPart = createSubCubeBlockPile(start, width, splitPosition, depth);
                    BlockPos secondStartY = new BlockPos(start.getX(), start.getY() + splitPosition, start.getZ());
                    secondPart = createSubCubeBlockPile(secondStartY, width, height - splitPosition, depth);
                    break;
                case 'z':
                    firstPart = createSubCubeBlockPile(start, width, height, splitPosition);
                    BlockPos secondStartZ = new BlockPos(start.getX(), start.getY(), start.getZ() + splitPosition);
                    secondPart = createSubCubeBlockPile(secondStartZ, width, height, depth - splitPosition);
                    break;
                default:
                    LOGGER.error("Invalid axis: {}", axis);
                    return Collections.emptyList();
            }

            if (firstPart != null && secondPart != null) {
                dispose();
                return Arrays.asList(firstPart, secondPart);
            }
        } catch (Exception e) {
            LOGGER.error("Error splitting CubeBlockPile: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 为当前方块堆中的特定相对位置创建引用。
     */
    public CubeBlockPileReference createReference(BlockPos relativePos) {
        return ServerCubeBlockPileReference.fromRelativePos(this, relativePos);
    }

    /**
     * 为当前方块堆中的特定相对位置创建引用。
     */
    public CubeBlockPileReference createReference(int relativeX, int relativeY, int relativeZ) {
        return createReference(new BlockPos(relativeX, relativeY, relativeZ));
    }

    /**
     * 为当前方块堆中的世界位置创建引用。
     */
    @Nullable
    public CubeBlockPileReference createReferenceFromWorldPos(BlockPos worldPos) {
        return ServerCubeBlockPileReference.fromWorldPos(this, worldPos);
    }

    /**
     * 安全地销毁这个CubeBlockPile实例。
     */
    @Override
    public void close() {
        dispose();
    }

    /**
     * 安全地销毁这个CubeBlockPile实例。
     */
    public void dispose() {
        if (!disposed) {
            CubeBlockPileManager.unregisterCubeBlockPile(this);
            disposed = true;
        }
    }

    public WorldView getWorld() {
        checkDisposed();
        return world;
    }

    public Block getBaseBlock() {
        checkDisposed();
        return baseBlock;
    }

    public PatternRange getRange() {
        checkDisposed();
        return range;
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * 获取方块堆的结束坐标（东南偏上位置）。
     */
    public BlockPos getEndPos() {
        checkDisposed();
        return range.getEnd();
    }

    /**
     * 获取方块堆的体积（方块数量）。
     */
    public int getVolume() {
        return disposed ? 0 : range.getVolume();
    }

    /**
     * 检查指定的世界位置是否在当前方块堆范围内。
     */
    public boolean containsWorldPos(BlockPos worldPos) {
        return getRange().contains(worldPos);
    }


    /**
     * 检查实例是否已被销毁。
     */
    private void checkDisposed() {
        if (disposed) {
            throw new IllegalStateException("CubeBlockPile at " + masterPos + " has been disposed");
        }
    }

    /**
     * 获取指定轴的大小。
     */
    private int getAxisSize(char axis) {
        return switch (axis) {
            case 'x' -> range.getWidth();
            case 'y' -> range.getHeight();
            case 'z' -> range.getDepth();
            default -> 0;
        };
    }

    /**
     * 创建子方块堆。
     */
    private CubeBlockPile createSubCubeBlockPile(BlockPos start, int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            return null;
        }
        try {
            return CubeBlockPile.builder()
                    .world(world)
                    .baseBlock(baseBlock)
                    .range(start, width, height, depth)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to create sub CubeBlockPile: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 表示三维立方体分解结果。
     */
    private static class CubeDecomposition {
        public final BlockPos start;
        public final int width;
        public final int height;
        public final int depth;
        public final int volume;

        public CubeDecomposition(BlockPos start, int width, int height, int depth) {
            this.start = start;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.volume = width * height * depth;
        }

        public boolean isValid() {
            return width > 0 && height > 0 && depth > 0;
        }
    }

    /**
     * 立方体候选。
     */
    private static class CubeCandidate {
        public final BlockPos start;
        public final int size;
        public final int volume;

        public CubeCandidate(BlockPos start, int size) {
            this.start = start;
            this.size = size;
            this.volume = size * size * size;
        }
    }

    /**
     * 验证合并条件。
     */
    private static void validateCombineConditions(CubeBlockPile first, CubeBlockPile second) {
        if (first.world != second.world) {
            String errorMsg = "CubeBlockPiles must be in the same world";
            LOGGER.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        if (first.baseBlock != second.baseBlock) {
            String errorMsg = String.format("CubeBlockPiles must have the same base block: %s vs %s",
                    first.baseBlock, second.baseBlock);
            LOGGER.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * 计算合并后的范围。
     */
    private static PatternRange calculateCombinedRange(CubeBlockPile first, CubeBlockPile second) {
        BlockPos newStart = new BlockPos(
                Math.min(first.range.getStart().getX(), second.range.getStart().getX()),
                Math.min(first.range.getStart().getY(), second.range.getStart().getY()),
                Math.min(first.range.getStart().getZ(), second.range.getStart().getZ()));

        BlockPos firstEnd = first.getEndPos();
        BlockPos secondEnd = second.getEndPos();

        BlockPos newEnd = new BlockPos(
                Math.max(firstEnd.getX(), secondEnd.getX()),
                Math.max(firstEnd.getY(), secondEnd.getY()),
                Math.max(firstEnd.getZ(), secondEnd.getZ()));

        int newWidth = newEnd.getX() - newStart.getX() + 1;
        int newHeight = newEnd.getY() - newStart.getY() + 1;
        int newDepth = newEnd.getZ() - newStart.getZ() + 1;

        if (newWidth > MAX_SIZE || newHeight > MAX_SIZE || newDepth > MAX_SIZE) {
            String errorMsg = String.format(
                    "Combined CubeBlockPile size %dx%dx%d would exceed maximum allowed size %dx%dx%d",
                    newWidth, newHeight, newDepth, MAX_SIZE, MAX_SIZE, MAX_SIZE);
            LOGGER.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        return new PatternRange(newStart, newWidth, newHeight, newDepth);
    }

    /**
     * 创建合并后的方块堆结构。
     */
    private static CubeBlockPile createCombinedCubeBlockPile(CubeBlockPile first, CubeBlockPile second, PatternRange newRange) {
        first.dispose();
        second.dispose();

        try {
            return new CubeBlockPile(first.world, first.baseBlock, newRange);
        } catch (Exception e) {
            LOGGER.error("Failed to create combined CubeBlockPile: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从立方体分解创建CubeBlockPile。
     */
    private CubeBlockPile createCubeBlockPileFromCube(CubeDecomposition cube) {
        try {
            return CubeBlockPile.builder()
                    .world(world)
                    .baseBlock(baseBlock)
                    .range(cube.start, cube.width, cube.height, cube.depth)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to create CubeBlockPile from cube {}: {}", cube.start, e.getMessage());
            return null;
        }
    }

    /**
     * 检查立方体是否可用（所有方块都未被覆盖）。
     */
    private boolean isCubeAvailable(Set<BlockPos> blockSet, Set<BlockPos> covered, CubeCandidate candidate) {
        for (int dx = 0; dx < candidate.size; dx++) {
            for (int dy = 0; dy < candidate.size; dy++) {
                for (int dz = 0; dz < candidate.size; dz++) {
                    BlockPos pos = new BlockPos(
                            candidate.start.getX() + dx,
                            candidate.start.getY() + dy,
                            candidate.start.getZ() + dz);
                    if (covered.contains(pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 将立方体标记为已覆盖。
     */
    private void markCubeAsCovered(Set<BlockPos> covered, CubeCandidate candidate) {
        for (int dx = 0; dx < candidate.size; dx++) {
            for (int dy = 0; dy < candidate.size; dy++) {
                for (int dz = 0; dz < candidate.size; dz++) {
                    BlockPos pos = new BlockPos(
                            candidate.start.getX() + dx,
                            candidate.start.getY() + dy,
                            candidate.start.getZ() + dz);
                    covered.add(pos);
                }
            }
        }
    }

    /**
     * 覆盖剩余的未覆盖方块（使用1x1x1立方体）。
     */
    private void coverRemainingBlocks(List<BlockPos> blocks, Set<BlockPos> covered,
                                      List<CubeDecomposition> cubes) {
        for (BlockPos block : blocks) {
            if (!covered.contains(block)) {
                cubes.add(new CubeDecomposition(block, 1, 1, 1));
                covered.add(block);
                LOGGER.trace("Added 1x1x1 cube for remaining block: {}", block);
            }
        }
    }

    /**
     * 查找最小边界坐标。
     */
    private BlockPos findMinBounds(List<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }

    /**
     * 查找最大边界坐标。
     */
    private BlockPos findMaxBounds(List<BlockPos> blocks) {
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    /**
     * 表示方块堆结构的范围。
     */
    public static final class PatternRange {
        private final BlockPos start;
        private final int width;
        private final int height;
        private final int depth;
        private final BlockPos end;
        private final int volume;

        public PatternRange(BlockPos start, int width, int height, int depth) {
            if (width <= 0 || height <= 0 || depth <= 0) {
                throw new IllegalArgumentException(
                        String.format("Dimensions must be positive: width=%d, height=%d, depth=%d",
                                width, height, depth));
            }

            this.start = start;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.end = new BlockPos(
                    start.getX() + width - 1,
                    start.getY() + height - 1,
                    start.getZ() + depth - 1);
            this.volume = width * height * depth;
        }

        public PatternRange(BlockPos start, int width, int height) {
            this(start, width, height, 1);
        }

        public PatternRange(BlockPos start) {
            this(start, 1, 1);
        }

        public boolean isSquare() {
            return width == height;
        }

        public boolean isCubes() {
            return this.isSquare() && width == depth;
        }

        public boolean contains(BlockPos worldPos) {
            return worldPos.getX() >= start.getX() && worldPos.getX() <= end.getX() &&
                    worldPos.getY() >= start.getY() && worldPos.getY() <= end.getY() &&
                    worldPos.getZ() >= start.getZ() && worldPos.getZ() <= end.getZ();
        }

        public BlockPos getStart() { return start; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getDepth() { return depth; }
        public BlockPos getEnd() { return end; }
        public int getVolume() { return volume; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PatternRange that = (PatternRange) obj;
            return width == that.width && height == that.height && depth == that.depth &&
                    Objects.equals(start, that.start);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, width, height, depth);
        }

        @Override
        public String toString() {
            return String.format("PatternRange{start=%s, width=%d, height=%d, depth=%d}",
                    start, width, height, depth);
        }
    }

    /**
     * 合并方向枚举。
     */
    private enum MergeDirection {
        EAST_WEST,    // 第一个在东，第二个在西
        WEST_EAST,    // 第一个在西，第二个在东
        UP_DOWN,      // 第一个在上，第二个在下
        DOWN_UP,      // 第一个在下，第二个在上
        SOUTH_NORTH,  // 第一个在南，第二个在北
        NORTH_SOUTH,  // 第一个在北，第二个在南
        OVERLAP_X,    // X轴重叠
        OVERLAP_Y,    // Y轴重叠
        OVERLAP_Z     // Z轴重叠
    }

    /**
     * 查找合并方向。
     */
    private static MergeDirection findMergeDirection(PatternRange first, PatternRange second) {
        BlockPos firstEnd = first.getEnd();
        BlockPos secondEnd = second.getEnd();

        // 检查X轴方向合并（东西方向）
        if (first.getStart().getY() == second.getStart().getY() && firstEnd.getY() == secondEnd.getY() &&
                first.getStart().getZ() == second.getStart().getZ() && firstEnd.getZ() == secondEnd.getZ()) {

            if (firstEnd.getX() + 1 == second.getStart().getX()) {
                return MergeDirection.EAST_WEST;
            } else if (secondEnd.getX() + 1 == first.getStart().getX()) {
                return MergeDirection.WEST_EAST;
            } else if (first.getStart().getX() <= secondEnd.getX() && firstEnd.getX() >= second.getStart().getX()) {
                return MergeDirection.OVERLAP_X;
            }
        }

        // 检查Y轴方向合并（上下方向）
        if (first.getStart().getX() == second.getStart().getX() && firstEnd.getX() == secondEnd.getX() &&
                first.getStart().getZ() == second.getStart().getZ() && firstEnd.getZ() == secondEnd.getZ()) {

            if (firstEnd.getY() + 1 == second.getStart().getY()) {
                return MergeDirection.UP_DOWN;
            } else if (secondEnd.getY() + 1 == first.getStart().getY()) {
                return MergeDirection.DOWN_UP;
            } else if (first.getStart().getY() <= secondEnd.getY() && firstEnd.getY() >= second.getStart().getY()) {
                return MergeDirection.OVERLAP_Y;
            }
        }

        // 检查Z轴方向合并（南北方向）
        if (first.getStart().getX() == second.getStart().getX() && firstEnd.getX() == secondEnd.getX() &&
                first.getStart().getY() == second.getStart().getY() && firstEnd.getY() == secondEnd.getY()) {

            if (firstEnd.getZ() + 1 == second.getStart().getZ()) {
                return MergeDirection.SOUTH_NORTH;
            } else if (secondEnd.getZ() + 1 == first.getStart().getZ()) {
                return MergeDirection.NORTH_SOUTH;
            } else if (first.getStart().getZ() <= secondEnd.getZ() && firstEnd.getZ() >= second.getStart().getZ()) {
                return MergeDirection.OVERLAP_Z;
            }
        }

        return null;
    }
}