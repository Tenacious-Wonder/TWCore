package org.twcore.blockpile;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.blockpile.CubeBlockPile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 方块堆结构管理器 - 全局的方块堆注册和查找服务
 *
 * <p>该类负责管理世界中所有的方块堆结构实例，提供注册、查找、持久化和生命周期管理功能。
 * 使用弱引用映射来管理世界实例，防止内存泄漏。
 *
 * <h2>主要功能</h2>
 * <ul>
 * <li><strong>注册管理</strong> - 注册和注销方块堆实例</li>
 * <li><strong>位置查找</strong> - 根据坐标快速找到对应的方块堆结构</li>
 * <li><strong>数据持久化</strong> - 游戏重启时自动恢复方块堆数据</li>
 * <li><strong>内存管理</strong> - 自动清理已销毁的方块堆引用</li>
 * </ul>
 *
 * <h2>注意</h2>
 * <p>该类以{@link World}作为一集键来区分不同维度中的方块堆，因此不得注册客户端世界的{@link CubeBlockPile}</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 查找指定位置的方块堆
 * CubeBlockPile multiBlock = CubeBlockPileManager.findCubeBlockPile(world, pos);
 *
 * // 获取世界中的所有方块堆
 * Collection<CubeBlockPile> allCubeBlockPiles = CubeBlockPileManager.getCubeBlockPilesInWorld(world);
 * }</pre>
 *
 * @see CubeBlockPilePersistentState
 */
@Deprecated
public class CubeBlockPileManager {
    private static final Logger LOGGER = TWCore.LOGGER;

    /**
     * 存储了所有方块堆的映射。
     * <P>一级键存储不同世界中的方块堆映射，二级键使用主方块的坐标来标识一个方块堆</P>
     * <strong>注意：</strong>作为键的世界必须是一个服务器世界。
     */
    private static final Map<WorldView, Map<BlockPos, CubeBlockPile>> CUBE_BLOCK_PILES = new WeakHashMap<>();
    private static final Object lock = new Object();

    /**
     * 注册一个新的CubeBlockPile
     * @param cubeBlockPile 要注册的方块堆实例
     * @return 如果成功注册返回true，否则返回false
     */
    public static boolean registerCubeBlockPile(CubeBlockPile cubeBlockPile) {
        if (cubeBlockPile == null) {
            LOGGER.error("Attempted to register null CubeBlockPile");
            return false;
        }

        if (cubeBlockPile.getWorld() instanceof World world) {
            if (world.isClient) {
                LOGGER.error("Attempted to register CubeBlockPile on client world at {}", cubeBlockPile.getMasterPos());
                cubeBlockPile.dispose();
                return false;
            }
        }

        if (cubeBlockPile.isDisposed()) {
            LOGGER.error("Attempted to register disposed CubeBlockPile at {}", cubeBlockPile.getMasterPos());
            return false;
        }

        WorldView worldView = cubeBlockPile.getWorld();
        BlockPos masterPos = cubeBlockPile.getMasterPos();

        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.computeIfAbsent(worldView, k -> new ConcurrentHashMap<>());

            if (worldMap.containsKey(masterPos)) {
                CubeBlockPile existing = worldMap.get(masterPos);
                if (!existing.isDisposed()) {
                    LOGGER.warn("Position {} in world {} is already occupied by CubeBlockPile with base block {}",
                            masterPos, worldView, existing.getBaseBlock());
                    return false;
                } else {
                    // 清理已销毁的CubeBlockPile
                    worldMap.remove(masterPos);
                }
            }

            worldMap.put(masterPos, cubeBlockPile);

            // 持久化到存档
            if (worldView instanceof ServerWorld serverWorld) {
                CubeBlockPilePersistentState persistentState = CubeBlockPilePersistentState.getOrCreate(serverWorld);
                persistentState.addCubeBlockPile(serverWorld, cubeBlockPile);
            }

            return true;
        }
    }

    /**
     * 注销一个CubeBlockPile。
     * @param cubeBlockPile 要注销的方块堆实例
     * @return 如果成功注销返回true，否则返回false
     * @apiNote 请不要直接调用该方法，如果需要注销方块堆，请直接调用{@link CubeBlockPile#dispose()}
     */
    public static boolean unregisterCubeBlockPile(CubeBlockPile cubeBlockPile) {
        if (cubeBlockPile == null) {
            LOGGER.warn("Attempted to unregister null CubeBlockPile");
            return false;
        }

        WorldView worldView = cubeBlockPile.getWorld();
        BlockPos masterPos = cubeBlockPile.getMasterPos();

        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.get(worldView);
            if (worldMap == null) {
                return false;
            }

            CubeBlockPile registered = worldMap.get(masterPos);
            if (registered == cubeBlockPile) {
                worldMap.remove(masterPos);

                // 从持久化存储中移除
                if (worldView instanceof ServerWorld serverWorld) {
                    CubeBlockPilePersistentState persistentState = CubeBlockPilePersistentState.getOrCreate(serverWorld);
                    persistentState.removeCubeBlockPile(serverWorld, masterPos);
                }

                // 如果这个世界没有其他CubeBlockPile了，清理世界映射
                if (worldMap.isEmpty()) {
                    CUBE_BLOCK_PILES.remove(worldView);
                }
                return true;
            } else {
                LOGGER.warn("CubeBlockPile at {} was not the registered instance", masterPos);
                return false;
            }
        }
    }

    /**
     * 加载世界时恢复方块堆数据
     */
    public static void loadWorldCubeBlockPiles(ServerWorld world) {
        synchronized (lock) {
            CubeBlockPilePersistentState persistentState = CubeBlockPilePersistentState.getOrCreate(world);
            Collection<CubeBlockPilePersistentState.CubeBlockPileData> cubeBlockPileDataList = persistentState.getCubeBlockPilesForWorld(world);

            for (CubeBlockPilePersistentState.CubeBlockPileData data : cubeBlockPileDataList) {
                try {
                    // 重建CubeBlockPile
                    CubeBlockPile cubeBlockPile = rebuildCubeBlockPileFromData(world, data);
                    if (cubeBlockPile != null) {
                        // 注册到内存中（不重复持久化）
                        Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.computeIfAbsent(world, k -> new ConcurrentHashMap<>());
                        worldMap.put(data.masterPos(), cubeBlockPile);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to reload CubeBlockPile at {}: {}", data.masterPos(), e.getMessage());
                }
            }
        }
    }

    /**
     * 从持久化数据重建方块堆。
     */
    private static CubeBlockPile rebuildCubeBlockPileFromData(ServerWorld world, CubeBlockPilePersistentState.CubeBlockPileData data) {
        try {
            // 获取基础方块
            Identifier blockId = new Identifier(data.baseBlockId());
            Block baseBlock = Registries.BLOCK.get(blockId);

            // 创建PatternRange
            CubeBlockPile.PatternRange range = new CubeBlockPile.PatternRange(data.start(), data.width(), data.height(), data.depth());

            // 重建方块堆
            return CubeBlockPile.builder()
                    .world(world)
                    .baseBlock(baseBlock)
                    .range(range)
                    .build();

        } catch (Exception e) {
            LOGGER.error("Failed to rebuild CubeBlockPile from data: {}", e.getMessage());
            return null;
        }
    }

    public static void onWorldStart(MinecraftServer server, World world) {
        if (!world.isClient) {
            CubeBlockPileManager.loadWorldCubeBlockPiles((ServerWorld) world);
        }
    }

    /**
     * 服务器关闭时清理
     */
    public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
        synchronized (lock) {
            CUBE_BLOCK_PILES.clear();
        }
    }

    /**
     * 手动备份方块堆数据
     */
    public static void backupCubeBlockPileData(net.minecraft.server.MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            CubeBlockPilePersistentState persistentState = CubeBlockPilePersistentState.getOrCreate(world);
            persistentState.saveToFile(server);
        }
    }

    /**
     * 根据位置查找CubeBlockPile
     */
    @Nullable
    public static CubeBlockPile findCubeBlockPile(WorldView world, BlockPos pos) {
        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.get(world);
            if (worldMap == null) {
                return null;
            }

            // 首先检查精确匹配
            CubeBlockPile exactMatch = worldMap.get(pos);
            if (exactMatch != null && !exactMatch.isDisposed()) {
                return exactMatch;
            }

            // 如果没有精确匹配，检查该位置是否在某个CubeBlockPile的范围内
            for (CubeBlockPile cubeBlockPile : worldMap.values()) {
                if (!cubeBlockPile.isDisposed() && cubeBlockPile.getRange().contains(pos)) {
                    return cubeBlockPile;
                }
            }

            return null;
        }
    }

    /**
     * 检查CubeBlockPile是否已注册且未销毁
     */
    public static boolean isRegistered(CubeBlockPile cubeBlockPile) {
        if (cubeBlockPile == null || cubeBlockPile.isDisposed()) return false;

        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.get(cubeBlockPile.getWorld());
            if (worldMap == null) return false;

            CubeBlockPile registered = worldMap.get(cubeBlockPile.getMasterPos());
            return registered == cubeBlockPile && !registered.isDisposed();
        }
    }

    /**
     * 检查位置是否被有效的CubeBlockPile占用
     */
    public static boolean isPositionOccupied(WorldView world, BlockPos pos) {
        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.get(world);
            if (worldMap == null) return false;

            CubeBlockPile cubeBlockPile = worldMap.get(pos);
            return cubeBlockPile != null && !cubeBlockPile.isDisposed();
        }
    }

    /**
     * 获取世界中所有未销毁的CubeBlockPile
     */
    public static Collection<CubeBlockPile> getCubeBlockPilesInWorld(WorldView world) {
        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.get(world);
            if (worldMap == null) {
                return Collections.emptyList();
            }

            List<CubeBlockPile> validBlocks = new ArrayList<>();
            for (CubeBlockPile cubeBlockPile : worldMap.values()) {
                if (!cubeBlockPile.isDisposed()) {
                    validBlocks.add(cubeBlockPile);
                }
            }
            return Collections.unmodifiableCollection(validBlocks);
        }
    }

    /**
     * 获取所有世界的CubeBlockPile数量统计
     */
    public static Map<WorldView, Integer> getRegistryStats() {
        synchronized (lock) {
            Map<WorldView, Integer> stats = new HashMap<>();
            for (Map.Entry<WorldView, Map<BlockPos, CubeBlockPile>> entry : CUBE_BLOCK_PILES.entrySet()) {
                int count = (int) entry.getValue().values().stream()
                        .filter(mb -> !mb.isDisposed())
                        .count();
                if (count > 0) {
                    stats.put(entry.getKey(), count);
                }
            }
            return stats;
        }
    }

    /**
     * 清理指定世界的所有CubeBlockPile
     */
    public static int clearWorld(WorldView world) {
        synchronized (lock) {
            Map<BlockPos, CubeBlockPile> worldMap = CUBE_BLOCK_PILES.remove(world);
            if (worldMap != null) {
                // 标记所有CubeBlockPile为已销毁状态
                for (CubeBlockPile cubeBlockPile : worldMap.values()) {
                    try {
                        cubeBlockPile.dispose();
                    } catch (Exception e) {
                        LOGGER.error("Error disposing CubeBlockPile at {}", cubeBlockPile.getMasterPos(), e);
                    }
                }
                int count = worldMap.size();
                return count;
            }
            return 0;
        }
    }

    /**
     * 清理所有世界的CubeBlockPile（用于服务器关闭等情况）
     */
    public static void clearAll() {
        synchronized (lock) {
            int totalCount = 0;
            for (Map<BlockPos, CubeBlockPile> worldMap : CUBE_BLOCK_PILES.values()) {
                for (CubeBlockPile cubeBlockPile : worldMap.values()) {
                    try {
                        cubeBlockPile.dispose();
                    } catch (Exception e) {
                        LOGGER.error("Error disposing CubeBlockPile at {}", cubeBlockPile.getMasterPos(), e);
                    }
                }
                totalCount += worldMap.size();
            }
            CUBE_BLOCK_PILES.clear();
        }
    }

    /**
     * 执行垃圾回收检查，清理已销毁的CubeBlockPile引用
     */
    public static void performCleanup() {
        synchronized (lock) {
            Iterator<Map.Entry<WorldView, Map<BlockPos, CubeBlockPile>>> worldIterator = CUBE_BLOCK_PILES.entrySet().iterator();

            while (worldIterator.hasNext()) {
                Map.Entry<WorldView, Map<BlockPos, CubeBlockPile>> worldEntry = worldIterator.next();
                Map<BlockPos, CubeBlockPile> worldMap = worldEntry.getValue();

                Iterator<Map.Entry<BlockPos, CubeBlockPile>> blockIterator = worldMap.entrySet().iterator();
                while (blockIterator.hasNext()) {
                    Map.Entry<BlockPos, CubeBlockPile> blockEntry = blockIterator.next();
                    CubeBlockPile cubeBlockPile = blockEntry.getValue();

                    // 清理已销毁的CubeBlockPile
                    if (cubeBlockPile.isDisposed()) {
                        blockIterator.remove();
                    }
                }

                // 如果这个世界没有有效的CubeBlockPile了，移除世界条目
                if (worldMap.isEmpty()) {
                    worldIterator.remove();
                }
            }
        }
    }

    /**
     * 使用try-with-resources模式创建临时CubeBlockPile
     */
    public static void withCubeBlockPile(WorldView world, Block baseBlock, CubeBlockPile.PatternRange range,
                                      Consumer<CubeBlockPile> action) {
        try (CubeBlockPile cubeBlockPile = CubeBlockPile.builder()
                .world(world)
                .baseBlock(baseBlock)
                .range(range)
                .build()) {
            action.accept(cubeBlockPile);
        } catch (Exception e) {
            LOGGER.error("Error in withCubeBlockPile", e);
        }
    }
}