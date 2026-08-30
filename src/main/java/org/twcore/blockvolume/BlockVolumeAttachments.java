package org.twcore.blockvolume;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.Chunk;

/**
 * 方块体系统使用的数据附加数据（Fabric data attachment）注册入口。
 *
 * <p>目前只注册一种附加数据：{@link #CHUNK_DATA}，挂在 {@link Chunk} 上，
 * 记录覆盖该区块的方块体，作为方块体系统的唯一事实来源（持久化与查询共用）。
 * 该附加数据通过 {@link AttachmentRegistry#createPersistent(Identifier, com.mojang.serialization.Codec)}
 * 声明为跨服重启持久化，由 Fabric 自动在区块保存/加载时序列化。</p>
 */
public final class BlockVolumeAttachments {

	/** 挂在区块上的方块体集合附加数据。 */
	public static final AttachmentType<BlockVolumeChunkData> CHUNK_DATA = AttachmentRegistry.createPersistent(
			new Identifier("tw_core", "block_volume_chunk_data"),
			BlockVolumeChunkData.CODEC
	);

	private BlockVolumeAttachments() {
	}

	/**
	 * 唤醒附加数据注册（触发静态字段初始化）。仅用于初始化阶段，无其他逻辑。
	 */
	public static void registerAll() {
	}
}
