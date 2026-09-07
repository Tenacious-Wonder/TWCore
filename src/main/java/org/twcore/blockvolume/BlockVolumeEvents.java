package org.twcore.blockvolume;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.blockvolume.BlockRange;
import org.twcore.api.blockvolume.BlockVolume;
import org.twcore.api.blockvolume.BlockVolumeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 方块体系统的事件处理：把"世界的方块变化"翻译成方块体结构的创建、合并与拆分。
 *
 * <p>由两个 mixin 驱动（{@code World.setBlockState} 与 {@code ChunkRegion.setBlockState} 的
 * RETURN 注入）：前者覆盖运行时的一切方块变化（放置/破坏/替换），后者覆盖世界生成阶段的
 * 方块写入。注册与查询见 {@link BlockVolumeRegistry}，拆合算法见 {@link BlockVolumeDecomposer}。</p>
 */
public final class BlockVolumeEvents {
	private static final Logger LOGGER = TWCore.LOGGER;

	/** 最大合并次数上限，防止异常情况下合并循环不收敛。合并必然使结构数减少，此值仅为防御性上限。 */
	private static final int MAX_MERGE_ROUNDS = 64;

	private BlockVolumeEvents() {
	}

	// ==================== 运行时 ====================

	/**
	 * 运行时任意方块变化后的统一处理（mixin 注入 {@code World.setBlockState} RETURN）。
	 *
	 * <p>变化位置在已注册结构中 → 校验结构完整性，不完整则以真实世界重新拆解；
	 * 否则若新方块是注册方块 → 创建单方块体并尝试合并（放置路径）。</p>
	 */
	public static void onBlockChanged(World world, BlockPos pos, BlockState newState) {
		if (world.isClient) {
			return;
		}
		BlockVolume existing = BlockVolumeManager.findBlockVolume(world, pos);
		if (existing != null) {
			// 位置仍在结构中：若新方块已非结构方块，结构必然不再完整，直接以真实世界重建；
			// 反之（例如重新放置同种方块）结构完好，无需全量扫描。
			// 其它位置的损坏由区块加载校验（onChunkLoad）兜底，避免高频方块变化触发全量扫描。
			if (newState.getBlock() != existing.baseBlock()) {
				rebuildAndMerge(world, existing);
			}
			return;
		}
		if (BlockVolumeRegistry.isRegistered(newState.getBlock())) {
			createAndMerge(world, pos, newState.getBlock());
		}
	}

	// ==================== 世界生成 ====================

	/**
	 * 世界生成阶段的方块写入处理（mixin 注入 {@code ChunkRegion.setBlockState} RETURN）。
	 *
	 * <p>写入的方块是注册方块 → 创建单方块体并尝试与生成区域内的相邻结构合并，把记录写入
	 * 可访问的覆盖区块。生成是逐块进行的，跨出生成区域的合并会暂时缺失，由区块加载时的
	 * 世界校验（{@link BlockVolumeManager#onChunkLoad}）自愈。</p>
	 */
	public static void onBlockPlacedInGeneration(ChunkRegion region, BlockPos pos, BlockState newState) {
		if (!BlockVolumeRegistry.isRegistered(newState.getBlock())) {
			return;
		}
		BlockVolume current = BlockVolume.of(newState.getBlock(), new BlockRange(pos));
		current = mergeInRegion(region, current);
		writeVolumeInRegion(region, current);
	}

	// ==================== 内部：运行时 ====================

	private static void rebuildAndMerge(World world, BlockVolume volume) {
		List<BlockVolume> rebuilt = BlockVolumeManager.rebuildBlockVolume((ServerWorld) world, volume);
		for (BlockVolume newVolume : rebuilt) {
			performMerging(world, newVolume);
		}
	}

	private static void createAndMerge(World world, BlockPos pos, Block baseBlock) {
		BlockVolume single = BlockVolume.of(baseBlock, new BlockRange(pos));
		BlockVolumeManager.registerBlockVolume((ServerWorld) world, single);
		performMerging(world, single);
	}

	/**
	 * 执行多轮合并，直到无法再合并为止。
	 */
	private static void performMerging(World world, BlockVolume initial) {
		// 该结构可能已在更早的合并中被移除（如拆分后的结构列表逐个合并，前一个合并吃掉了后一个），
		// 对已移除的结构继续合并会产生与现有结构重叠的伪结构，故先确认其仍存在
		BlockVolume existing = BlockVolumeRegistry.findBlockVolume(world, initial.masterPos());
		if (existing == null || !existing.equals(initial)) {
			return;
		}
		runMergeLoop(
				initial,
				volume -> findAdjacent(world, volume),
				(first, second, merged) -> {
					BlockVolumeManager.removeBlockVolume((ServerWorld) world, first);
					BlockVolumeManager.removeBlockVolume((ServerWorld) world, second);
					BlockVolumeManager.registerBlockVolume((ServerWorld) world, merged);
				});
	}

	/**
	 * 在生成区域内执行多轮合并（查询相邻结构只限生成区域可访问的区块）。
	 *
	 * @return 合并完成后的方块体
	 */
	private static BlockVolume mergeInRegion(ChunkRegion region, BlockVolume initial) {
		return runMergeLoop(
				initial,
				volume -> findAdjacentInRegion(region, volume),
				(first, second, merged) -> {
					removeVolumeInRegion(region, first);
					removeVolumeInRegion(region, second);
					writeVolumeInRegion(region, merged);
				});
	}

	/**
	 * 多轮合并循环：反复查找可合并的相邻方块体并替换，直到无法合并或达到轮数上限。
	 *
	 * @param initial       起始方块体
	 * @param adjacentFinder 查找相邻方块体（运行时查世界，生成阶段查生成区域）
	 * @param replacer      用合并结果替换两个旧结构
	 * @return 合并完成后的方块体
	 */
	private static BlockVolume runMergeLoop(BlockVolume initial,
											Function<BlockVolume, List<BlockVolume>> adjacentFinder,
											MergeReplacer replacer) {
		BlockVolume current = initial;
		boolean mergedInThisRound;
		int mergeCount = 0;

		do {
			mergedInThisRound = false;

			for (BlockVolume neighbor : adjacentFinder.apply(current)) {
				if (neighbor.masterPos().equals(current.masterPos())) {
					continue;
				}
				if (neighbor.baseBlock() != current.baseBlock()) {
					continue;
				}

				BlockRange mergedRange = BlockVolumeDecomposer.computeMergedRange(current.range(), neighbor.range());
				if (mergedRange == null) {
					continue;
				}

				BlockVolume merged = BlockVolume.of(current.baseBlock(), mergedRange);
				replacer.replace(current, neighbor, merged);

				current = merged;
				mergedInThisRound = true;
				mergeCount++;
				break;
			}

			if (mergeCount >= MAX_MERGE_ROUNDS) {
				LOGGER.warn("Reached maximum merge rounds for BlockVolume at {}", current.masterPos());
				break;
			}
		} while (mergedInThisRound);
		return current;
	}

	/** 合并循环中"用合并结果替换两个旧结构"的操作。 */
	@FunctionalInterface
	private interface MergeReplacer {
		void replace(BlockVolume first, BlockVolume second, BlockVolume merged);
	}

	/**
	 * 查找与指定方块体相邻的所有其它方块体。
	 */
	private static List<BlockVolume> findAdjacent(World world, BlockVolume volume) {
		List<BlockVolume> neighbors = new ArrayList<>();
		BlockRange range = volume.range();
		BlockPos start = range.start();
		BlockPos end = range.end();

		checkDirection(neighbors, world, start.west(), new BlockPos(start.getX() - 1, end.getY(), end.getZ()));
		checkDirection(neighbors, world, end.east(), new BlockPos(end.getX() + 1, end.getY(), end.getZ()));
		checkDirection(neighbors, world, start.down(), new BlockPos(end.getX(), start.getY() - 1, end.getZ()));
		checkDirection(neighbors, world, end.up(), new BlockPos(end.getX(), end.getY() + 1, end.getZ()));
		checkDirection(neighbors, world, start.north(), new BlockPos(end.getX(), end.getY(), start.getZ() - 1));
		checkDirection(neighbors, world, end.south(), new BlockPos(end.getX(), end.getY(), end.getZ() + 1));

		return neighbors;
	}

	/**
	 * 在指定方向上查找相邻的方块体结构。
	 */
	private static void checkDirection(List<BlockVolume> neighbors, World world, BlockPos faceStart, BlockPos faceEnd) {
		for (int x = faceStart.getX(); x <= faceEnd.getX(); x++) {
			for (int y = faceStart.getY(); y <= faceEnd.getY(); y++) {
				for (int z = faceStart.getZ(); z <= faceEnd.getZ(); z++) {
					BlockPos checkPos = new BlockPos(x, y, z);
					BlockVolume neighbor = BlockVolumeRegistry.findBlockVolume(world, checkPos);
					if (neighbor != null && !neighbors.contains(neighbor)) {
						neighbors.add(neighbor);
						// 该结构已完整覆盖整个扫描面（轴对齐矩形含对角即全覆盖），
						// 此方向不可能再有其他相邻结构，提前结束扫描
						if (coversFace(neighbor, faceStart, faceEnd)) {
							return;
						}
					}
				}
			}
		}
	}

	/** 判断结构范围是否完整覆盖指定的矩形面（轴对齐矩形含对角两点即全覆盖）。 */
	private static boolean coversFace(BlockVolume volume, BlockPos faceStart, BlockPos faceEnd) {
		return volume.containsWorldPos(faceStart) && volume.containsWorldPos(faceEnd);
	}

	// ==================== 内部：世界生成 ====================

	private static List<BlockVolume> findAdjacentInRegion(ChunkRegion region, BlockVolume volume) {
		List<BlockVolume> neighbors = new ArrayList<>();
		BlockRange range = volume.range();
		BlockPos start = range.start();
		BlockPos end = range.end();

		checkDirectionInRegion(neighbors, region, start.west(), new BlockPos(start.getX() - 1, end.getY(), end.getZ()));
		checkDirectionInRegion(neighbors, region, end.east(), new BlockPos(end.getX() + 1, end.getY(), end.getZ()));
		checkDirectionInRegion(neighbors, region, start.down(), new BlockPos(end.getX(), start.getY() - 1, end.getZ()));
		checkDirectionInRegion(neighbors, region, end.up(), new BlockPos(end.getX(), end.getY() + 1, end.getZ()));
		checkDirectionInRegion(neighbors, region, start.north(), new BlockPos(end.getX(), end.getY(), start.getZ() - 1));
		checkDirectionInRegion(neighbors, region, end.south(), new BlockPos(end.getX(), end.getY(), end.getZ() + 1));

		return neighbors;
	}

	private static void checkDirectionInRegion(List<BlockVolume> neighbors, ChunkRegion region, BlockPos faceStart, BlockPos faceEnd) {
		for (int x = faceStart.getX(); x <= faceEnd.getX(); x++) {
			for (int y = faceStart.getY(); y <= faceEnd.getY(); y++) {
				for (int z = faceStart.getZ(); z <= faceEnd.getZ(); z++) {
					BlockPos checkPos = new BlockPos(x, y, z);
					Chunk chunk = getChunkSafely(region, checkPos);
					if (chunk == null) {
						continue;
					}
					BlockVolume neighbor = BlockVolumeManager.findInChunk(chunk, checkPos);
					if (neighbor != null && !neighbors.contains(neighbor)) {
						neighbors.add(neighbor);
						// 该结构已完整覆盖整个扫描面，此方向不可能再有其他相邻结构，提前结束扫描
						if (coversFace(neighbor, faceStart, faceEnd)) {
							return;
						}
					}
				}
			}
		}
	}

	/**
	 * 把方块体的记录写入生成区域中可访问的覆盖区块。
	 */
	private static void writeVolumeInRegion(ChunkRegion region, BlockVolume volume) {
		for (ChunkPos chunkPos : BlockVolumeManager.coveredChunks(volume)) {
			Chunk chunk = getChunkSafely(region, new BlockPos(chunkPos.x << 4, 0, chunkPos.z << 4));
			if (chunk != null) {
				BlockVolumeManager.writeVolumeToChunk(chunk, volume);
			}
		}
	}

	private static void removeVolumeInRegion(ChunkRegion region, BlockVolume volume) {
		for (ChunkPos chunkPos : BlockVolumeManager.coveredChunks(volume)) {
			Chunk chunk = getChunkSafely(region, new BlockPos(chunkPos.x << 4, 0, chunkPos.z << 4));
			if (chunk != null) {
				BlockVolumeManager.removeVolumeFromChunk(chunk, volume);
			}
		}
	}

	/**
	 * 安全获取生成区域内的区块：超出生成区域（placementRadius 之外）返回 {@code null}。
	 */
	private static Chunk getChunkSafely(ChunkRegion region, BlockPos pos) {
		try {
			return region.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		} catch (Exception e) {
			return null;
		}
	}
}
