package org.twcore.blockvolume;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.blockvolume.BlockRange;
import org.twcore.api.blockvolume.BlockVolume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * 方块体的拆解与合并算法（纯函数）。
 *
 * <p>不关心注册、持久化或生命周期，只负责：
 * <ul>
 *   <li>收集某范围内仍属于方块体的位置 {@link #findValidBlocks}；</li>
 *   <li>把一组有效位置拆分成若干完整方块体 {@link #splitValidBlocks}；</li>
 *   <li>判断两个范围能否合并成一个更大的长方体 {@link #computeMergedRange}。</li>
 * </ul>
 * 注册与持久化由 {@link BlockVolumeManager} 负责，事件驱动的拆合流程由
 * {@link BlockVolumeEvents} 负责。</p>
 */
public final class BlockVolumeDecomposer {
	private static final Logger LOGGER = TWCore.LOGGER;

	private BlockVolumeDecomposer() {
	}

	/**
	 * 收集给定范围内仍为方块体方块的位置。
	 */
	public static List<BlockPos> findValidBlocks(WorldView world, BlockRange range, Block baseBlock) {
		List<BlockPos> validBlocks = new ArrayList<>();
		BlockPos start = range.start();
		BlockPos end = range.end();

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
	 * 把一组有效位置拆分成若干完整方块体。
	 *
	 * <p>优先使用实心立方体分解，失败时回退到三维连通分量算法。</p>
	 *
	 * @return 拆分出的完整方块体列表，每个方块体内所有方块都确为 {@code baseBlock}
	 */
	public static List<BlockVolume> splitValidBlocks(Block baseBlock, List<BlockPos> validBlocks) {
		if (validBlocks.isEmpty()) {
			return Collections.emptyList();
		}

		List<BlockVolume> result = splitValidBlocksOptimized(baseBlock, validBlocks);
		if (result.isEmpty()) {
			LOGGER.warn("Optimized decomposition failed, falling back to connected components algorithm");
			result = splitValidBlocksFallback(baseBlock, validBlocks);
		}
		return result;
	}

	private static List<BlockVolume> splitValidBlocksOptimized(Block baseBlock, List<BlockPos> validBlocks) {
		List<CubeDecomposition> cubes = decomposeIntoSolidCubes(validBlocks);
		List<BlockVolume> result = new ArrayList<>();
		for (CubeDecomposition cube : cubes) {
			if (cube.isValid()) {
				BlockVolume volume = createBlockVolume(baseBlock, cube.start, cube.width, cube.height, cube.depth);
				if (volume != null) {
					result.add(volume);
				}
			}
		}
		return result;
	}

	private static List<BlockVolume> splitValidBlocksFallback(Block baseBlock, List<BlockPos> validBlocks) {
		List<List<BlockPos>> components = findConnectedComponents(validBlocks);
		List<BlockVolume> result = new ArrayList<>();
		for (List<BlockPos> component : components) {
			if (!component.isEmpty()) {
				BlockVolume volume = createBlockVolumeFromComponent(baseBlock, component);
				if (volume != null) {
					result.add(volume);
				}
			}
		}
		return result;
	}

	/**
	 * 计算两个范围能否合并成一个更大的长方体范围。
	 *
	 * <p>两个范围必须在某一条轴上相邻（或恰接），且在另两条轴上的投影完全重合。
	 * 合并结果必须恰好覆盖两者的<b>并集</b>：即并集体积（两者体积之和减去交集体积）
	 * 等于外接长方体体积，否则说明两者之间存在重叠或空隙。</p>
	 *
	 * @return 合并后的范围；若两者无法合并成长方体则返回 {@code null}
	 */
	@Nullable
	public static BlockRange computeMergedRange(BlockRange first, BlockRange second) {
		BlockRange combined = computeCombinedRange(first, second);
		if (combined == null) {
			return null;
		}
		int unionVolume = first.volume() + second.volume() - overlapVolume(first, second);
		return combined.volume() == unionVolume ? combined : null;
	}

	/**
	 * 两个范围的重叠体积（无交集时为 0）。
	 */
	private static int overlapVolume(BlockRange first, BlockRange second) {
		int overlapX = Math.min(first.end().getX(), second.end().getX()) - Math.max(first.start().getX(), second.start().getX()) + 1;
		int overlapY = Math.min(first.end().getY(), second.end().getY()) - Math.max(first.start().getY(), second.start().getY()) + 1;
		int overlapZ = Math.min(first.end().getZ(), second.end().getZ()) - Math.max(first.start().getZ(), second.start().getZ()) + 1;
		if (overlapX <= 0 || overlapY <= 0 || overlapZ <= 0) {
			return 0;
		}
		return overlapX * overlapY * overlapZ;
	}

	/**
	 * 计算两个范围的外接长方体，若超出尺寸限制或投影无法对齐则返回 {@code null}。
	 */
	@Nullable
	private static BlockRange computeCombinedRange(BlockRange first, BlockRange second) {
		BlockPos newStart = new BlockPos(
				Math.min(first.start().getX(), second.start().getX()),
				Math.min(first.start().getY(), second.start().getY()),
				Math.min(first.start().getZ(), second.start().getZ()));
		BlockPos newEnd = new BlockPos(
				Math.max(first.end().getX(), second.end().getX()),
				Math.max(first.end().getY(), second.end().getY()),
				Math.max(first.end().getZ(), second.end().getZ()));

		int newWidth = newEnd.getX() - newStart.getX() + 1;
		int newHeight = newEnd.getY() - newStart.getY() + 1;
		int newDepth = newEnd.getZ() - newStart.getZ() + 1;

		if (newWidth > BlockVolume.MAX_SIZE || newHeight > BlockVolume.MAX_SIZE || newDepth > BlockVolume.MAX_SIZE) {
			LOGGER.error("Combined BlockVolume size {}x{}x{} would exceed maximum allowed size {}x{}x{}",
					newWidth, newHeight, newDepth, BlockVolume.MAX_SIZE, BlockVolume.MAX_SIZE, BlockVolume.MAX_SIZE);
			return null;
		}
		return new BlockRange(newStart, newWidth, newHeight, newDepth);
	}

	// ==================== 实心立方体分解 ====================

	private static List<CubeDecomposition> decomposeIntoSolidCubes(List<BlockPos> blocks) {
		if (blocks.isEmpty()) {
			return Collections.emptyList();
		}

		Set<BlockPos> blockSet = new HashSet<>(blocks);
		BlockPos min = findMinBounds(blocks);
		BlockPos max = findMaxBounds(blocks);
		Set<BlockPos> covered = new HashSet<>();
		List<CubeDecomposition> cubes = new ArrayList<>();

		PriorityQueue<CubeCandidate> candidateQueue = new PriorityQueue<>((a, b) -> Integer.compare(b.volume, a.volume));
		generateCubeCandidates(blockSet, min, max, candidateQueue);

		while (!candidateQueue.isEmpty() && covered.size() < blocks.size()) {
			CubeCandidate candidate = candidateQueue.poll();
			if (isCubeAvailable(covered, candidate)) {
				cubes.add(new CubeDecomposition(candidate.start, candidate.size, candidate.size, candidate.size));
				markCubeAsCovered(covered, candidate);
				LOGGER.trace("Selected cube: {} size {} (volume: {})", candidate.start, candidate.size, candidate.volume);
			}
		}

		coverRemainingBlocks(blocks, covered, cubes);
		return cubes;
	}

	private static void generateCubeCandidates(Set<BlockPos> blockSet, BlockPos min, BlockPos max,
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

	private static boolean isSolidCube(Set<BlockPos> blockSet, BlockPos start, int size) {
		for (int dx = 0; dx < size; dx++) {
			for (int dy = 0; dy < size; dy++) {
				for (int dz = 0; dz < size; dz++) {
					if (!blockSet.contains(new BlockPos(start.getX() + dx, start.getY() + dy, start.getZ() + dz))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean isCubeAvailable(Set<BlockPos> covered, CubeCandidate candidate) {
		for (int dx = 0; dx < candidate.size; dx++) {
			for (int dy = 0; dy < candidate.size; dy++) {
				for (int dz = 0; dz < candidate.size; dz++) {
					BlockPos pos = new BlockPos(candidate.start.getX() + dx, candidate.start.getY() + dy, candidate.start.getZ() + dz);
					if (covered.contains(pos)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void markCubeAsCovered(Set<BlockPos> covered, CubeCandidate candidate) {
		for (int dx = 0; dx < candidate.size; dx++) {
			for (int dy = 0; dy < candidate.size; dy++) {
				for (int dz = 0; dz < candidate.size; dz++) {
					covered.add(new BlockPos(candidate.start.getX() + dx, candidate.start.getY() + dy, candidate.start.getZ() + dz));
				}
			}
		}
	}

	private static void coverRemainingBlocks(List<BlockPos> blocks, Set<BlockPos> covered, List<CubeDecomposition> cubes) {
		for (BlockPos block : blocks) {
			if (!covered.contains(block)) {
				cubes.add(new CubeDecomposition(block, 1, 1, 1));
				covered.add(block);
				LOGGER.trace("Added 1x1x1 cube for remaining block: {}", block);
			}
		}
	}

	// ==================== 连通分量回退算法 ====================

	private static List<List<BlockPos>> findConnectedComponents(List<BlockPos> validBlocks) {
		Set<BlockPos> visited = new HashSet<>();
		List<List<BlockPos>> components = new ArrayList<>();
		Set<BlockPos> validSet = new HashSet<>(validBlocks);

		int[][] directions = {
				{1, 0, 0}, {-1, 0, 0},
				{0, 1, 0}, {0, -1, 0},
				{0, 0, 1}, {0, 0, -1}
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
						BlockPos neighbor = new BlockPos(current.getX() + dir[0], current.getY() + dir[1], current.getZ() + dir[2]);
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

	private static BlockVolume createBlockVolumeFromComponent(Block baseBlock, List<BlockPos> component) {
		if (component.isEmpty()) {
			return null;
		}

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

		if (isRectangularRegionValid(component, minX, minY, minZ, maxX, maxY, maxZ)) {
			return createBlockVolume(baseBlock, new BlockPos(minX, minY, minZ),
					maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
		} else {
			BlockVolume volume = createBlockVolume(baseBlock, new BlockPos(minX, minY, minZ),
					maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
			if (volume != null) {
				LOGGER.warn("Created non-solid BlockVolume at {} containing {} blocks", new BlockPos(minX, minY, minZ), component.size());
			}
			return volume;
		}
	}

	private static boolean isRectangularRegionValid(List<BlockPos> component, int minX, int minY, int minZ,
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

	@Nullable
	private static BlockVolume createBlockVolume(Block baseBlock, BlockPos start, int width, int height, int depth) {
		if (width <= 0 || height <= 0 || depth <= 0) {
			return null;
		}
		try {
			return BlockVolume.of(baseBlock, new BlockRange(start, width, height, depth));
		} catch (Exception e) {
			LOGGER.error("Failed to create BlockVolume from {} size {}x{}x{}: {}", start, width, height, depth, e.getMessage());
			return null;
		}
	}

	private static BlockPos findMinBounds(List<BlockPos> blocks) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		for (BlockPos pos : blocks) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
		}
		return new BlockPos(minX, minY, minZ);
	}

	private static BlockPos findMaxBounds(List<BlockPos> blocks) {
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : blocks) {
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		return new BlockPos(maxX, maxY, maxZ);
	}

	private record CubeDecomposition(BlockPos start, int width, int height, int depth) {
		boolean isValid() {
			return width > 0 && height > 0 && depth > 0;
		}
	}

	private static final class CubeCandidate {
		final BlockPos start;
		final int size;
		final int volume;

		CubeCandidate(BlockPos start, int size) {
			this.start = start;
			this.size = size;
			this.volume = size * size * size;
		}
	}
}
