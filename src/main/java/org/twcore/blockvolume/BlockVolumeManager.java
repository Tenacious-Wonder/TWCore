package org.twcore.blockvolume;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.blockvolume.BlockVolume;
import org.twcore.api.blockvolume.BlockVolumeChangeType;
import org.twcore.api.blockvolume.BlockVolumeRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 方块体集合的注册、查询与恢复。
 *
 * <p>以区块附加数据（{@link BlockVolumeAttachments#CHUNK_DATA}）为<b>唯一事实来源</b>：
 * 每个方块体写入它覆盖的每个区块，查询只需定位位置所在区块；系统不维护全局运行时索引，
 * 内存随区块加载自然伸缩。写入操作通过 {@link #getOrLoadChunk} 主动确保覆盖区块可读写附加数据；
 * 区块加载时（{@link #onChunkLoad}）以真实世界校验记录，纠正事件遗漏造成的数据陈旧。</p>
 *
 * <p>区块级读写方法（{@link #writeVolumeToChunk} / {@link #removeVolumeFromChunk} /
 * {@link #findInChunk}）面向任意 {@link Chunk} 实现，运行时与生成阶段共用：
 * 前者提供 {@link WorldChunk}，生成阶段提供生成中的 {@link Chunk}。</p>
 */
public final class BlockVolumeManager {

	private BlockVolumeManager() {
	}

	// ==================== 查询 ====================

	/**
	 * 查找包含指定位置的方块体。
	 *
	 * @return 覆盖该位置的方块体；位置所在区块未加载或不属于任何方块体时返回 {@code null}
	 */
	@Nullable
	public static BlockVolume findBlockVolume(WorldView world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return null;
		}
		WorldChunk chunk = serverWorld.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk == null ? null : findInChunk(chunk, pos);
	}

	/**
	 * 在指定区块的附加数据中查找覆盖某位置的方块体。
	 */
	@Nullable
	public static BlockVolume findInChunk(Chunk chunk, BlockPos pos) {
		BlockVolumeChunkData data = chunk.getAttached(BlockVolumeAttachments.CHUNK_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		for (BlockVolume volume : data.volumes()) {
			if (volume.containsWorldPos(pos)) {
				return volume;
			}
		}
		return null;
	}

	// ==================== 注册与移除 ====================

	/**
	 * 注册一个方块体：把它的记录写入所覆盖的所有区块，并触发 {@link BlockVolumeRegistry#CHANGE} 事件。
	 *
	 * <p>同主方块位置的旧记录会被替换（幂等）。</p>
	 */
	public static void registerBlockVolume(ServerWorld world, BlockVolume volume) {
		for (ChunkPos chunkPos : coveredChunks(volume)) {
			writeVolumeToChunk(getOrLoadChunk(world, chunkPos), volume);
		}
		BlockVolumeRegistry.CHANGE.invoker().onBlockVolumeChanged(world, volume, BlockVolumeChangeType.CREATED);
	}

	/**
	 * 注销一个方块体：从它所覆盖的所有区块清除记录，并触发 {@link BlockVolumeRegistry#CHANGE} 事件。
	 */
	public static void removeBlockVolume(ServerWorld world, BlockVolume volume) {
		for (ChunkPos chunkPos : coveredChunks(volume)) {
			removeVolumeFromChunk(getOrLoadChunk(world, chunkPos), volume);
		}
		BlockVolumeRegistry.CHANGE.invoker().onBlockVolumeChanged(world, volume, BlockVolumeChangeType.REMOVED);
	}

	/**
	 * 把方块体的记录写入单个区块（幂等：同主方块位置的旧记录被替换）。
	 */
	public static void writeVolumeToChunk(Chunk chunk, BlockVolume volume) {
		BlockVolumeChunkData current = chunk.getAttachedOrElse(BlockVolumeAttachments.CHUNK_DATA, BlockVolumeChunkData.EMPTY);
		List<BlockVolume> list = new ArrayList<>(current.volumes());
		list.removeIf(v -> v.masterPos().equals(volume.masterPos()));
		list.add(volume);
		chunk.setAttached(BlockVolumeAttachments.CHUNK_DATA, new BlockVolumeChunkData(list));
	}

	/**
	 * 从单个区块清除方块体的记录。
	 */
	public static void removeVolumeFromChunk(Chunk chunk, BlockVolume volume) {
		BlockVolumeChunkData current = chunk.getAttached(BlockVolumeAttachments.CHUNK_DATA);
		if (current == null || current.isEmpty()) {
			return;
		}
		List<BlockVolume> list = new ArrayList<>(current.volumes());
		list.removeIf(v -> v.masterPos().equals(volume.masterPos()));
		if (list.isEmpty()) {
			chunk.setAttached(BlockVolumeAttachments.CHUNK_DATA, null);
		} else {
			chunk.setAttached(BlockVolumeAttachments.CHUNK_DATA, new BlockVolumeChunkData(list));
		}
	}

	/**
	 * 以真实世界为准重新拆解一个方块体（通常在其方块被破坏后调用）。
	 *
	 * <p>会先移除旧记录，再把真实存在的方块重新拆分成若干完整方块体并注册。</p>
	 *
	 * @return 重新拆分出的完整方块体列表
	 */
	public static List<BlockVolume> rebuildBlockVolume(ServerWorld world, BlockVolume volume) {
		List<BlockPos> validBlocks = BlockVolumeDecomposer.findValidBlocks(world, volume.range(), volume.baseBlock());
		List<BlockVolume> newVolumes = BlockVolumeDecomposer.splitValidBlocks(volume.baseBlock(), validBlocks);

		removeBlockVolume(world, volume);
		for (BlockVolume newVolume : newVolumes) {
			registerBlockVolume(world, newVolume);
		}
		return newVolumes;
	}

	// ==================== 区块加载校正 ====================

	/**
	 * 区块加载时校验其附加数据中的方块体记录。
	 *
	 * <p>记录完整（范围内仍全为目标方块）则保留；否则以真实方块为准重新拆解并替换记录，
	 * 用于兜底事件遗漏（如命令/结构方块改方块）造成的数据陈旧。</p>
	 *
	 * <p>只处理<b>覆盖区块全部已加载</b>的结构：拆解与重注册需要写入所有覆盖区块，
	 * 而强制加载未加载区块会与当前区块加载流程重入导致死锁；未全部加载的结构
	 * 在其覆盖区块陆续加载后自然进入此校验。</p>
	 */
	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		BlockVolumeChunkData data = chunk.getAttached(BlockVolumeAttachments.CHUNK_DATA);
		if (data == null || data.isEmpty()) {
			return;
		}
		for (BlockVolume volume : List.copyOf(data.volumes())) {
			if (!allCoveredChunksLoaded(world, volume)) {
				continue;
			}
			if (volume.checkIntegrity(world)) {
				continue;
			}
			removeBlockVolume(world, volume);
			for (BlockVolume rebuilt : reconstructFromData(world, volume)) {
				registerBlockVolume(world, rebuilt);
			}
		}
	}

	/**
	 * 判断方块体的覆盖区块是否全部已加载。
	 */
	private static boolean allCoveredChunksLoaded(ServerWorld world, BlockVolume volume) {
		for (ChunkPos chunkPos : coveredChunks(volume)) {
			if (world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z) == null) {
				return false;
			}
		}
		return true;
	}

	// ==================== 内部 ====================

	/**
	 * 获取已加载区块；若区块休眠（已生成但未加载），主动加载它。
	 *
	 * <p>附加数据的读写只需要区块达到可访问的加载等级，因此对休眠区块主动加载是安全的；
	 * 方块体覆盖的区块必然已生成（其中的方块真实存在），加载不会触发地形生成。</p>
	 */
	private static WorldChunk getOrLoadChunk(ServerWorld world, ChunkPos chunkPos) {
		WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z);
		return chunk != null ? chunk : world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z, true);
	}

	private static List<BlockVolume> reconstructFromData(ServerWorld world, BlockVolume volume) {
		List<BlockPos> validBlocks = BlockVolumeDecomposer.findValidBlocks(world, volume.range(), volume.baseBlock());
		if (validBlocks.isEmpty()) {
			return List.of();
		}
		return BlockVolumeDecomposer.splitValidBlocks(volume.baseBlock(), validBlocks);
	}

	/**
	 * 方块体覆盖的区块坐标集合（长方体范围在区块维度上的投影）。
	 */
	public static Set<ChunkPos> coveredChunks(BlockVolume volume) {
		BlockPos start = volume.masterPos();
		BlockPos end = volume.endPos();
		Set<ChunkPos> result = new HashSet<>();
		for (int cx = start.getX() >> 4; cx <= end.getX() >> 4; cx++) {
			for (int cz = start.getZ() >> 4; cz <= end.getZ() >> 4; cz++) {
				result.add(new ChunkPos(cx, cz));
			}
		}
		return result;
	}
}
