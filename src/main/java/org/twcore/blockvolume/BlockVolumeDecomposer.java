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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 方块体的拆解与合并算法。
 *
 * <p>几何核心与 Minecraft 类型解耦：坐标用 {@link BlockPos#asLong} 打包的 long 表达，
 * {@link BlockVolume}/{@link BlockRange} 只在最外层适配。</p>
 *
 * <p>职责：
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

	// ==================== Minecraft 适配层 ====================

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
	 * @return 拆分出的完整方块体列表，每个方块体内所有方块都确为 {@code baseBlock}
	 */
	public static List<BlockVolume> splitValidBlocks(Block baseBlock, List<BlockPos> validBlocks) {
		if (validBlocks.isEmpty()) {
			return Collections.emptyList();
		}

		long[] coords = new long[validBlocks.size()];
		for (int i = 0; i < validBlocks.size(); i++) {
			coords[i] = validBlocks.get(i).asLong();
		}
		List<Box> boxes = decomposeToBoxes(coords);

		List<BlockVolume> result = new ArrayList<>(boxes.size());
		for (Box box : boxes) {
			BlockVolume volume = createBlockVolume(baseBlock, box);
			if (volume != null) {
				result.add(volume);
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
		Box merged = computeMergedBox(toBox(first), toBox(second), BlockVolume.MAX_SIZE);
		return merged == null ? null : toRange(merged);
	}

	// ==================== 纯几何核心 ====================

	/**
	 * 实心长方体（几何核心的基本单位，与 Minecraft 类型无关）。
	 *
	 * <p>坐标用 {@link BlockPos#asLong} 打包，解包走 {@link BlockPos#fromLong}——
	 * 该坐标类为纯数据，不触发任何注册表初始化，可在单元测试环境安全使用。</p>
	 */
	record Box(long start, int width, int height, int depth) {

		BlockPos startPos() {
			return BlockPos.fromLong(start);
		}

		int minX() {
			return BlockPos.unpackLongX(start);
		}

		int minY() {
			return BlockPos.unpackLongY(start);
		}

		int minZ() {
			return BlockPos.unpackLongZ(start);
		}

		int volume() {
			return width * height * depth;
		}
	}

	private static Box toBox(BlockRange range) {
		return new Box(range.start().asLong(), range.width(), range.height(), range.depth());
	}

	private static BlockRange toRange(Box box) {
		return new BlockRange(box.startPos(), box.width(), box.height(), box.depth());
	}

	/**
	 * 把一组坐标（{@link BlockPos#asLong} 打包）拆分成若干实心长方体。
	 *
	 * <p>策略：按 y 分层，每层做二维矩形贪心分解（行段扩展），再把相邻层中
	 * 平面投影相同的矩形合并为长方体。整体复杂度与方块数量近似线性，
	 * 且分解结果直接是长方体（而非"立方体加残渣"），方块体数量更少。</p>
	 *
	 * @return 拆分出的长方体列表，覆盖与输入完全一致（不重不漏）
	 */
	static List<Box> decomposeToBoxes(long[] blocks) {
		if (blocks.length == 0) {
			return Collections.emptyList();
		}

		// 按 y 分层
		Map<Integer, Set<Long>> layers = new TreeMap<>();
		for (long block : blocks) {
			layers.computeIfAbsent(BlockPos.unpackLongY(block), y -> new HashSet<>()).add(block);
		}

		// 每层做二维矩形分解
		List<int[]> layerRects = new ArrayList<>(); // {minX, minZ, maxX, maxZ, y}
		for (Map.Entry<Integer, Set<Long>> entry : layers.entrySet()) {
			decomposeLayer(entry.getValue(), entry.getKey(), layerRects);
		}

		// 相邻层同投影的矩形合并为长方体
		return mergeVertical(layerRects);
	}

	/**
	 * 计算两个长方体能否合并成一个更大的长方体。
	 *
	 * <p>两个长方体必须在某一条轴上相邻（或恰接），且在另两条轴上的投影完全重合；
	 * 并集体积必须等于外接长方体体积。合并结果尺寸不得超过 {@code maxSize}。</p>
	 *
	 * @return 合并后的长方体；若无法合并则返回 {@code null}
	 */
	@Nullable
	static Box computeMergedBox(Box first, Box second, int maxSize) {
		Box combined = combineBoxes(first, second, maxSize);
		if (combined == null) {
			return null;
		}
		int unionVolume = first.volume() + second.volume() - overlapVolume(first, second);
		return combined.volume() == unionVolume ? combined : null;
	}

	/**
	 * 计算两个长方体的外接长方体，若超出尺寸限制则返回 {@code null}。
	 */
	@Nullable
	private static Box combineBoxes(Box first, Box second, int maxSize) {
		int newMinX = Math.min(first.minX(), second.minX());
		int newMinY = Math.min(first.minY(), second.minY());
		int newMinZ = Math.min(first.minZ(), second.minZ());
		int newMaxX = Math.max(first.minX() + first.width - 1, second.minX() + second.width - 1);
		int newMaxY = Math.max(first.minY() + first.height - 1, second.minY() + second.height - 1);
		int newMaxZ = Math.max(first.minZ() + first.depth - 1, second.minZ() + second.depth - 1);

		int newWidth = newMaxX - newMinX + 1;
		int newHeight = newMaxY - newMinY + 1;
		int newDepth = newMaxZ - newMinZ + 1;

		if (newWidth > maxSize || newHeight > maxSize || newDepth > maxSize) {
			// 合并结果超限是正常的拒绝决策（两个结构各自保留），不是错误
			LOGGER.debug("Combined BlockVolume size {}x{}x{} would exceed maximum allowed size {}x{}x{}",
					newWidth, newHeight, newDepth, maxSize, maxSize, maxSize);
			return null;
		}
		return new Box(BlockPos.asLong(newMinX, newMinY, newMinZ), newWidth, newHeight, newDepth);
	}

	/**
	 * 两个长方体的重叠体积（无交集时为 0）。
	 */
	private static int overlapVolume(Box first, Box second) {
		int overlapX = Math.min(first.minX() + first.width - 1, second.minX() + second.width - 1)
				- Math.max(first.minX(), second.minX()) + 1;
		int overlapY = Math.min(first.minY() + first.height - 1, second.minY() + second.height - 1)
				- Math.max(first.minY(), second.minY()) + 1;
		int overlapZ = Math.min(first.minZ() + first.depth - 1, second.minZ() + second.depth - 1)
				- Math.max(first.minZ(), second.minZ()) + 1;
		if (overlapX <= 0 || overlapY <= 0 || overlapZ <= 0) {
			return 0;
		}
		return overlapX * overlapY * overlapZ;
	}

	// ==================== 分层矩形分解 ====================

	/**
	 * 对单层（固定 y 的 xz 平面）做二维矩形贪心分解。
	 *
	 * <p>任取一个未覆盖点，先沿 x 扩展出行段，再沿 z 扩展出整行区间都存在的最大矩形，
	 * 覆盖后继续，直到本层全部覆盖。</p>
	 */
	private static void decomposeLayer(Set<Long> remaining, int y, List<int[]> out) {
		while (!remaining.isEmpty()) {
			long seed = remaining.iterator().next();
			int seedX = BlockPos.unpackLongX(seed);
			int seedZ = BlockPos.unpackLongZ(seed);

			int x1 = seedX;
			int x2 = seedX;
			while (remaining.contains(BlockPos.asLong(x1 - 1, y, seedZ))) {
				x1--;
			}
			while (remaining.contains(BlockPos.asLong(x2 + 1, y, seedZ))) {
				x2++;
			}

			int z1 = seedZ;
			int z2 = seedZ;
			boolean expanded;
			do {
				expanded = false;
				if (rowFullyPresent(remaining, y, z2 + 1, x1, x2)) {
					z2++;
					expanded = true;
				} else if (rowFullyPresent(remaining, y, z1 - 1, x1, x2)) {
					z1--;
					expanded = true;
				}
			} while (expanded);

			for (int z = z1; z <= z2; z++) {
				for (int x = x1; x <= x2; x++) {
					remaining.remove(BlockPos.asLong(x, y, z));
				}
			}
			out.add(new int[]{x1, z1, x2, z2, y});
		}
	}

	/** 判断指定行（固定 y、z）的 x 区间是否全部仍在剩余集合中。 */
	private static boolean rowFullyPresent(Set<Long> remaining, int y, int z, int x1, int x2) {
		for (int x = x1; x <= x2; x++) {
			if (!remaining.contains(BlockPos.asLong(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 把各层矩形按平面投影分组，把 y 连续的组内矩形合并为长方体。
	 */
	private static List<Box> mergeVertical(List<int[]> layerRects) {
		Map<RectKey, List<Integer>> groups = new LinkedHashMap<>();
		for (int[] rect : layerRects) {
			groups.computeIfAbsent(new RectKey(rect[0], rect[1], rect[2], rect[3]), k -> new ArrayList<>()).add(rect[4]);
		}

		List<Box> result = new ArrayList<>();
		for (Map.Entry<RectKey, List<Integer>> entry : groups.entrySet()) {
			RectKey key = entry.getKey();
			List<Integer> ys = entry.getValue();
			Collections.sort(ys);

			int segmentStart = ys.get(0);
			int previous = ys.get(0);
			for (int i = 1; i < ys.size(); i++) {
				int y = ys.get(i);
				if (y != previous + 1) {
					result.add(new Box(BlockPos.asLong(key.x1, segmentStart, key.z1),
							key.x2 - key.x1 + 1, previous - segmentStart + 1, key.z2 - key.z1 + 1));
					segmentStart = y;
				}
				previous = y;
			}
			result.add(new Box(BlockPos.asLong(key.x1, segmentStart, key.z1),
					key.x2 - key.x1 + 1, previous - segmentStart + 1, key.z2 - key.z1 + 1));
		}
		return result;
	}

	/** 二维平面矩形投影（x/z 区间），用于垂直合并分组。 */
	private record RectKey(int x1, int z1, int x2, int z2) {
	}

	@Nullable
	private static BlockVolume createBlockVolume(Block baseBlock, Box box) {
		try {
			return BlockVolume.of(baseBlock, toRange(box));
		} catch (Exception e) {
			LOGGER.error("Failed to create BlockVolume from {} size {}x{}x{}: {}",
					box.startPos(), box.width, box.height, box.depth, e.getMessage());
			return null;
		}
	}
}
