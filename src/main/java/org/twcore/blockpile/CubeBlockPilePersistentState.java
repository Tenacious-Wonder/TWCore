package org.twcore.blockpile;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.blockpile.CubeBlockPile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 方块堆数据的持久化存储
 */
@Deprecated
public class CubeBlockPilePersistentState extends PersistentState {
    private static final Logger LOGGER = TWCore.LOGGER;
    private static final String PERSISTENT_ID = "cubeBlockPiles";

    /**
     * 临时存储的方块堆数据
     * @see CubeBlockPileManager
     */
    private final Map<Identifier, Map<BlockPos, CubeBlockPileData>> worldData = new ConcurrentHashMap<>();

    public CubeBlockPilePersistentState() {
        super();
    }

    /**
     * 方块堆数据的序列化表示
     */
    public record CubeBlockPileData(BlockPos masterPos, String baseBlockId, BlockPos start, int width, int height,
                                    int depth) {
        public @NotNull NbtCompound toNbt() {
                NbtCompound nbt = new NbtCompound();
                nbt.put("masterPos", NbtHelper.fromBlockPos(masterPos));
                nbt.putString("baseBlockId", baseBlockId);
                nbt.put("start", NbtHelper.fromBlockPos(start));
                nbt.putInt("width", width);
                nbt.putInt("height", height);
                nbt.putInt("depth", depth);
                return nbt;
            }

            public static @NotNull CubeBlockPilePersistentState.CubeBlockPileData fromNbt(@NotNull NbtCompound nbt) {
                BlockPos masterPos = NbtHelper.toBlockPos(nbt.getCompound("masterPos"));
                String baseBlockId = nbt.getString("baseBlockId");
                BlockPos start = NbtHelper.toBlockPos(nbt.getCompound("start"));
                int width = nbt.getInt("width");
                int height = nbt.getInt("height");
                int depth = nbt.getInt("depth");
                return new CubeBlockPileData(masterPos, baseBlockId, start, width, height, depth);
            }
        }

    /**
     * 添加方块堆数据
     */
    public void addCubeBlockPile(@NotNull World world, @NotNull CubeBlockPile cubeBlockPile) {
        Identifier worldId = world.getRegistryKey().getValue();
        Map<BlockPos, CubeBlockPileData> worldMap = worldData
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>());

        CubeBlockPileData data = new CubeBlockPileData(
                cubeBlockPile.getMasterPos(),
                cubeBlockPile.getBaseBlock().getRegistryEntry().registryKey().getValue().toString(),
                cubeBlockPile.getRange().getStart(),
                cubeBlockPile.getRange().getWidth(),
                cubeBlockPile.getRange().getHeight(),
                cubeBlockPile.getRange().getDepth()
        );

        worldMap.put(cubeBlockPile.getMasterPos(), data);
        markDirty();
    }

    /**
     * 移除方块堆数据
     */
    public void removeCubeBlockPile(@NotNull World world, BlockPos masterPos) {
        Identifier worldId = world.getRegistryKey().getValue();
        Map<BlockPos, CubeBlockPileData> worldMap = worldData.get(worldId);
        if (worldMap != null) {
            worldMap.remove(masterPos);
            markDirty();
        }
    }

    /**
     * 获取世界中所有的方块堆数据
     */
    public Collection<CubeBlockPileData> getCubeBlockPilesForWorld(@NotNull World world) {
        Identifier worldId = world.getRegistryKey().getValue();
        Map<BlockPos, CubeBlockPileData> worldMap = worldData.get(worldId);
        return worldMap != null ? worldMap.values() : Collections.emptyList();
    }

    /**
     * 清除世界中的所有方块堆数据
     */
    public void clearWorldData(@NotNull World world) {
        Identifier worldId = world.getRegistryKey().getValue();
        worldData.remove(worldId);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList worldsList = new NbtList();

        for (Map.Entry<Identifier, Map<BlockPos, CubeBlockPileData>> worldEntry : worldData.entrySet()) {
            NbtCompound worldNbt = new NbtCompound();
            worldNbt.putString("worldId", worldEntry.getKey().toString());

            NbtList cubeBlockPilesList = new NbtList();
            for (CubeBlockPileData data : worldEntry.getValue().values()) {
                cubeBlockPilesList.add(data.toNbt());
            }

            worldNbt.put("cubeBlockPiles", cubeBlockPilesList);
            worldsList.add(worldNbt);
        }

        nbt.put("worlds", worldsList);

        return nbt;
    }

    /**
     * 从NBT读取数据
     */
    public static @NotNull CubeBlockPilePersistentState fromNbt(NbtCompound nbt) {
        CubeBlockPilePersistentState state = new CubeBlockPilePersistentState();

        NbtList worldsList = nbt.getList("worlds", NbtElement.COMPOUND_TYPE);
        for (NbtElement worldElement : worldsList) {
            NbtCompound worldNbt = (NbtCompound) worldElement;
            Identifier worldId = new Identifier(worldNbt.getString("worldId"));

            Map<BlockPos, CubeBlockPileData> worldMap = new ConcurrentHashMap<>();
            NbtList cubeBlockPilesList = worldNbt.getList("cubeBlockPiles", NbtElement.COMPOUND_TYPE);

            for (NbtElement blockElement : cubeBlockPilesList) {
                NbtCompound blockNbt = (NbtCompound) blockElement;
                try {
                    CubeBlockPileData data = CubeBlockPileData.fromNbt(blockNbt);
                    worldMap.put(data.masterPos, data);
                } catch (Exception e) {
                    LOGGER.error("Failed to load CubeBlockPile data from NBT: {}", e.getMessage());
                }
            }

            state.worldData.put(worldId, worldMap);
        }

        return state;
    }

    /**
     * 获取或创建持久化状态
     */
    public static CubeBlockPilePersistentState getOrCreate(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();
        return persistentStateManager.getOrCreate(
                CubeBlockPilePersistentState::fromNbt,
                CubeBlockPilePersistentState::new,
                PERSISTENT_ID
        );
    }

    /**
     * 保存到文件（手动备份）
     */
    public void saveToFile(MinecraftServer server) {
        try {
            File worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();
            File backupFile = new File(worldDir, "cubeBlockPileblocks_backup.dat");

            NbtCompound nbt = this.writeNbt(new NbtCompound());
            NbtIo.write(nbt, backupFile);
        } catch (IOException e) {
            LOGGER.error("Failed to backup CubeBlockPile data: {}", e.getMessage());
        }
    }
}