package org.twcore.blockvolume;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.twcore.api.blockvolume.BlockVolume;

import java.util.List;

/**
 * 附加在一个区块上的方块体数据集合。
 *
 * <p>记录覆盖了该区块的所有方块体。由于一个方块体可能跨越多个区块，
 * 同一个方块体会冗余出现在它所覆盖的每一个区块的附加数据里；所有写入操作
 * （注册/移除/重建）都会主动确保覆盖区块已加载（见 {@link BlockVolumeManager}），
 * 因此该数据始终与最新结构状态一致。</p>
 *
 * @param volumes 覆盖该区块的方块体列表，可能为空
 */
public record BlockVolumeChunkData(List<BlockVolume> volumes) {

	/** 该记录的编解码器，用于附加数据的持久化。 */
	public static final Codec<BlockVolumeChunkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockVolume.CODEC.listOf().fieldOf("volumes").forGetter(BlockVolumeChunkData::volumes)
	).apply(instance, BlockVolumeChunkData::new));

	/** 空集合。 */
	public static final BlockVolumeChunkData EMPTY = new BlockVolumeChunkData(List.of());

	/** 是否不含任何方块体。 */
	public boolean isEmpty() {
		return volumes.isEmpty();
	}
}
