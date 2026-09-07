package org.twcore.blockvolume;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.twcore.blockvolume.BlockVolumeDecomposer.Box;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方块体拆合几何核心的单元测试。
 *
 * <p>直接测试 {@link BlockVolumeDecomposer} 中与 Minecraft 类型解耦的纯几何方法
 * （坐标用 {@link BlockPos#asLong} 打包的 long），不启动游戏、不触碰注册表，
 * 任何 JVM 环境均可运行。</p>
 */
class BlockVolumeDecomposerTest {

	private static final int MAX_SIZE = 100;

	// ==================== 拆解 ====================

	@Test
	void emptyInputProducesEmptyResult() {
		assertTrue(BlockVolumeDecomposer.decomposeToBoxes(new long[0]).isEmpty());
	}

	@Test
	void singleBlockProducesSingleBox() {
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(new long[]{pos(5, 6, 7)});
		assertEquals(1, boxes.size());
		assertEquals(1, boxes.get(0).volume());
		assertEquals(pos(5, 6, 7), boxes.get(0).start());
	}

	@Test
	void solidCubeIsDecomposedIntoOneBox() {
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(filledRect(0, 0, 0, 2, 2, 2));
		assertEquals(1, boxes.size());
		assertEquals(8, boxes.get(0).volume());
	}

	/**
	 * 核心不变量：无论拆成几个长方体，所有长方体覆盖的位置必须与输入完全一致（不重不漏）。
	 * 拆合系统以真实世界为权威，拆解结果若有遗漏或重复覆盖，后续完整性校验与查询都会出错。
	 *
	 * <p>数据刻意使用非原点、y/z 尺寸不同的矩形，避免坐标解包错误被对称数据掩盖。</p>
	 */
	@Test
	void decompositionCoversInputExactly() {
		long[] blocks = concat(
				filledRect(7, 5, 3, 4, 3, 2),
				new long[]{pos(10, 10, 10), pos(50, 20, 40)});

		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(blocks);

		Set<Long> covered = new HashSet<>();
		for (Box box : boxes) {
			for (int x = 0; x < box.width(); x++) {
				for (int y = 0; y < box.height(); y++) {
					for (int z = 0; z < box.depth(); z++) {
						long p = pos(box.minX() + x, box.minY() + y, box.minZ() + z);
						assertTrue(covered.add(p), "重复覆盖: " + BlockPos.fromLong(p));
					}
				}
			}
		}
		assertEquals(blocks.length, covered.size(), "覆盖总数与输入不一致");
		for (long block : blocks) {
			assertTrue(covered.contains(block), "遗漏: " + BlockPos.fromLong(block));
		}
	}

	/**
	 * 非立方体的实心矩形拆解后同样必须不重不漏。
	 */
	@Test
	void solidRectDecompositionCoversExactly() {
		long[] blocks = filledRect(3, 4, 5, 2, 3, 4);
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(blocks);
		Set<Long> covered = new HashSet<>();
		for (Box box : boxes) {
			for (int x = 0; x < box.width(); x++) {
				for (int y = 0; y < box.height(); y++) {
					for (int z = 0; z < box.depth(); z++) {
						assertTrue(covered.add(pos(box.minX() + x, box.minY() + y, box.minZ() + z)));
					}
				}
			}
		}
		assertEquals(blocks.length, covered.size());
	}

	/**
	 * 实心长方体（非立方体）应直接拆成单个长方体，而不是"立方体加残渣"。
	 */
	@Test
	void solidRectIsDecomposedIntoSingleBox() {
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(filledRect(1, 2, 3, 2, 3, 4));
		assertEquals(1, boxes.size());
		Box box = boxes.get(0);
		assertEquals(2, box.width());
		assertEquals(3, box.height());
		assertEquals(4, box.depth());
		assertEquals(2 * 3 * 4, box.volume());
	}

	/**
	 * 两个分离的柱子应各自拆成一个长方体。
	 */
	@Test
	void separatedPillarsProduceSeparateBoxes() {
		long[] blocks = concat(filledRect(0, 0, 0, 1, 3, 1), filledRect(5, 0, 5, 1, 3, 1));
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(blocks);
		assertEquals(2, boxes.size());
		for (Box box : boxes) {
			assertEquals(3, box.height());
		}
	}

	/**
	 * 大实心结构应快速拆成单个长方体（验证新算法近似线性的复杂度，
	 * 旧算法对此规模会产生天文数字的候选枚举）。
	 */
	@Test
	void largeSolidStructureDecomposesFast() {
		long[] blocks = filledRect(0, 0, 0, 30, 30, 30);
		long start = System.nanoTime();
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(blocks);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		assertEquals(1, boxes.size());
		assertEquals(30, boxes.get(0).width());
		assertEquals(30, boxes.get(0).height());
		assertEquals(30, boxes.get(0).depth());
		assertTrue(elapsedMs < 2000, "拆解耗时过长: " + elapsedMs + "ms");
	}

	/**
	 * 每层形状不同的多层结构（阶梯）拆解后覆盖仍与输入一致。
	 */
	@Test
	void steppedStructureCoversExactly() {
		long[] blocks = concat(
				filledRect(0, 0, 0, 3, 1, 3),
				filledRect(0, 1, 0, 2, 1, 2),
				filledRect(0, 2, 0, 1, 1, 1));
		List<Box> boxes = BlockVolumeDecomposer.decomposeToBoxes(blocks);
		Set<Long> covered = new HashSet<>();
		for (Box box : boxes) {
			for (int x = 0; x < box.width(); x++) {
				for (int y = 0; y < box.height(); y++) {
					for (int z = 0; z < box.depth(); z++) {
						assertTrue(covered.add(pos(box.minX() + x, box.minY() + y, box.minZ() + z)));
					}
				}
			}
		}
		assertEquals(blocks.length, covered.size());
	}

	// ==================== 合并判定 ====================

	@Test
	void adjacentBoxesWithMatchingProjectionMerge() {
		Box first = new Box(pos(0, 0, 0), 1, 1, 1);
		Box second = new Box(pos(1, 0, 0), 1, 1, 1);
		Box merged = BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE);
		assertNotNull(merged);
		assertEquals(2, merged.width());
		assertEquals(1, merged.height());
		assertEquals(1, merged.depth());
		assertEquals(pos(0, 0, 0), merged.start());
	}

	@Test
	void adjacentBoxesWithPartialProjectionDoNotMerge() {
		// 2×1×1 与 1×2×1 贴面：y 投影不重合，外接体积大于并集体积
		Box first = new Box(pos(0, 0, 0), 2, 1, 1);
		Box second = new Box(pos(2, 1, 0), 1, 2, 1);
		assertNull(BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE));
	}

	@Test
	void overlappingBoxesDoNotMerge() {
		Box first = new Box(pos(0, 0, 0), 2, 2, 2);
		Box second = new Box(pos(1, 1, 1), 2, 2, 2);
		assertNull(BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE));
	}

	@Test
	void boxesWithGapDoNotMerge() {
		Box first = new Box(pos(0, 0, 0), 1, 1, 1);
		Box second = new Box(pos(2, 0, 0), 1, 1, 1);
		assertNull(BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE));
	}

	@Test
	void cornerTouchingBoxesDoNotMerge() {
		Box first = new Box(pos(0, 0, 0), 1, 1, 1);
		Box second = new Box(pos(1, 1, 0), 1, 1, 1);
		assertNull(BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE));
	}

	@Test
	void mergeExceedingMaxSizeIsRejected() {
		Box first = new Box(pos(0, 0, 0), MAX_SIZE, 1, 1);
		Box second = new Box(pos(MAX_SIZE, 0, 0), 1, 1, 1);
		assertNull(BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE));
	}

	@Test
	void mergeResultIsSameForSwappedArguments() {
		Box first = new Box(pos(3, 3, 3), 2, 1, 1);
		Box second = new Box(pos(1, 3, 3), 2, 1, 1);
		assertEquals(BlockVolumeDecomposer.computeMergedBox(first, second, MAX_SIZE),
				BlockVolumeDecomposer.computeMergedBox(second, first, MAX_SIZE));
	}

	// ==================== 工具 ====================

	/** 把 x/y/z 坐标打包为 {@link BlockPos#asLong} 格式（BlockPos 为纯数据类，测试环境安全）。 */
	private static long pos(int x, int y, int z) {
		return BlockPos.asLong(x, y, z);
	}

	/** 生成从 (startX, startY, startZ) 起 w×h×d 的实心矩形坐标列表。 */
	private static long[] filledRect(int startX, int startY, int startZ, int w, int h, int d) {
		long[] result = new long[w * h * d];
		int i = 0;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				for (int z = 0; z < d; z++) {
					result[i++] = pos(startX + x, startY + y, startZ + z);
				}
			}
		}
		return result;
	}

	private static long[] concat(long[]... arrays) {
		int total = 0;
		for (long[] array : arrays) {
			total += array.length;
		}
		long[] result = new long[total];
		int offset = 0;
		for (long[] array : arrays) {
			System.arraycopy(array, 0, result, offset, array.length);
			offset += array.length;
		}
		return result;
	}
}
