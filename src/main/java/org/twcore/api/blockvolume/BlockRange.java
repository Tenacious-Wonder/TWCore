package org.twcore.api.blockvolume;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * 不可变的长方体方块范围：从 {@code start} 起、沿三轴各 {@code width}/{@code height}/{@code depth}
 * 个方块的实心长方体区域（含边界）。
 *
 * <p>它是 {@link BlockVolume} 的几何部分，也作为拆合算法的独立输入，与具体方块类型无关。</p>
 *
 * @param start  最小角（西北下）方块坐标
 * @param width  X 轴方向方块数量
 * @param height Y 轴方向方块数量
 * @param depth  Z 轴方向方块数量
 * @since 1.0.4
 */
public record BlockRange(@NotNull BlockPos start, int width, int height, int depth) {

	/** 持久化编解码器，用于 {@link BlockVolume} 在区块附加数据中的序列化。 */
	public static final Codec<BlockRange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("start").forGetter(BlockRange::start),
			Codec.INT.fieldOf("width").forGetter(BlockRange::width),
			Codec.INT.fieldOf("height").forGetter(BlockRange::height),
			Codec.INT.fieldOf("depth").forGetter(BlockRange::depth)
	).apply(instance, BlockRange::new));

	public BlockRange {
		if (width <= 0 || height <= 0 || depth <= 0) {
			throw new IllegalArgumentException(
					String.format("BlockRange dimensions must be positive: %dx%dx%d", width, height, depth));
		}
	}

	/** 单方块范围。 */
	public BlockRange(BlockPos start) {
		this(start, 1, 1, 1);
	}

	/**
	 * 最大角（东南上）方块坐标，含边界。
	 *
	 * @since 1.0.4
	 */
	public @NotNull BlockPos end() {
		return start.add(width - 1, height - 1, depth - 1);
	}

	/**
	 * 范围内的方块数量。
	 *
	 * @since 1.0.4
	 */
	public int volume() {
		return width * height * depth;
	}

	/**
	 * 判断世界坐标是否落在范围内。
	 *
	 * @since 1.0.4
	 */
	public boolean contains(@NotNull BlockPos pos) {
		BlockPos end = end();
		return pos.getX() >= start.getX() && pos.getX() <= end.getX()
				&& pos.getY() >= start.getY() && pos.getY() <= end.getY()
				&& pos.getZ() >= start.getZ() && pos.getZ() <= end.getZ();
	}
}
