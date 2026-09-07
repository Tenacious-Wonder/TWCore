package org.twcore.api.blockvolume;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.blockvolume.BlockVolumeManager;

import java.util.Objects;

/**
 * <h1>方块体</h1>
 * <p>
 * 对"世界上一个由同种方块完整填满的长方体区域"的<b>不可变描述</b>（值对象）。
 * 它是方块体系统的核心概念：如同房产证描述一块不动产，它只回答"结构是什么"
 * （构成方块、长方体范围、标识位置），不持有世界引用、不管理生命周期。
 * </p>
 *
 * <h2>系统构成</h2>
 * <p>
 * 方块体系统由以下角色组成：
 * </p>
 * <ul>
 *   <li><b>本类型</b> —— 结构的不可变描述：构成方块、范围、标识、几何查询与完整性校验。</li>
 *   <li>{@link BlockRange} —— 长方体的几何范围，结构的形状部分。</li>
 *   <li>{@link BlockVolumeRegistry} —— 对外入口：注册参与系统的方块、归属查询。</li>
 *   <li>{@link org.twcore.blockvolume.BlockVolumeManager} —— 结构集合的管理：注册、查询与持久化（区块附加数据为唯一事实来源）。</li>
 *   <li>{@link org.twcore.blockvolume.BlockVolumeDecomposer} —— 纯算法：把真实世界的方块重新拆分成若干完整方块体、判定合并。</li>
 *   <li>{@link org.twcore.blockvolume.BlockVolumeEvents} —— 事件处理：把方块变化翻译成结构维护（由 mixin 注入
 *       {@code World.setBlockState} / {@code ChunkRegion.setBlockState} 自动驱动）。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <p>
 * 注册方块被放置时，系统自动创建单方块体并注册、尝试与相邻方块体合并；方块被破坏时，
 * 以真实世界为准重新拆解并注册新结构；世界生成阶段写入注册方块同样会被感知；区块加载时
 * Manager 校验附加数据，纠正与真实世界不符的记录。真实世界中的方块是唯一权威，方块体只是对它的描述。
 * </p>
 *
 * <h2>创建约束</h2>
 * <p>
 * 一个方块体的出现<b>必然伴随着真实的方块放置</b>，因此<b>不要手动构造</b>本类型。
 * 方块体只能由系统在以下场景创建：
 * </p>
 * <ul>
 *   <li>方块放置 —— 注册方块（{@link BlockVolumeRegistry#register}）被放置时，系统自动感知并创建；</li>
 *   <li>结构拆解 —— {@link org.twcore.blockvolume.BlockVolumeManager} 以真实世界为准重新拆分时；</li>
 *   <li>客户端快照 —— 方块实体从 NBT 恢复显示信息时（{@link #fromNbt}）。</li>
 * </ul>
 *
 * <h2>消费方接入</h2>
 * <p>
 * 消费方（使用方块体系统的模组）按以下步骤接入：
 * </p>
 * <ol>
 *   <li>初始化时 {@link BlockVolumeRegistry#register(Block...)} 注册参与系统的方块，此后无需任何手动接线——</li>
 *   <li>需要查询某个位置属于哪个方块体时，调用 {@link BlockVolumeRegistry#findBlockVolume}；
 *       普通方块与方块实体一视同仁；</li>
 *   <li>客户端如需显示结构信息（无服务端索引），由方块实体通过 NBT 同步轻量快照
 *       （{@link #toNbt()} / {@link #fromNbt}）。</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 初始化时注册（不要手动 new BlockVolume）
 * BlockVolumeRegistry.register(MySlateBlock.INSTANCE);
 *
 * // 归属查询
 * BlockVolume volume = BlockVolumeRegistry.findBlockVolume(world, pos);
 * if (volume != null && pos.equals(volume.masterPos())) {
 *     // 该位置是主方块
 * }
 * }</pre>
 *
 * @since 1.0.3
 * @see BlockVolumeRegistry
 * @see BlockVolumeManager
 * @see BlockRange
 */
public final class BlockVolume {
	private static final Logger LOGGER = TWCore.LOGGER;

	/** 最大方块体尺寸限制（每条轴）。 */
	public static final int MAX_SIZE = 100;

	/** 持久化编解码器，用于区块附加数据的序列化。 */
	public static final Codec<BlockVolume> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Registries.BLOCK.getCodec().fieldOf("baseBlock").forGetter(BlockVolume::baseBlock),
			BlockRange.CODEC.fieldOf("range").forGetter(BlockVolume::range)
	).apply(instance, BlockVolume::of));

	private final @NotNull Block baseBlock;
	private final @NotNull BlockRange range;

	private BlockVolume(@NotNull Block baseBlock, @NotNull BlockRange range) {
		this.baseBlock = Objects.requireNonNull(baseBlock, "Base block cannot be null");
		this.range = Objects.requireNonNull(range, "Range cannot be null");
		if (range.width() > MAX_SIZE || range.height() > MAX_SIZE || range.depth() > MAX_SIZE) {
			throw new IllegalArgumentException(
					String.format("BlockVolume size %dx%dx%d exceeds maximum allowed size %dx%dx%d",
							range.width(), range.height(), range.depth(), MAX_SIZE, MAX_SIZE, MAX_SIZE));
		}
	}

	/**
	 * 创建方块体。构造器为私有：方块体只能由系统在事件驱动、结构拆解或快照恢复时创建，
	 * 手动创建不会与真实世界产生任何联系，请使用上述系统入口。
	 *
	 * @param baseBlock 构成方块体的方块
	 * @param range     结构范围
	 * @since 1.0.3
	 */
	public static BlockVolume of(@NotNull Block baseBlock, @NotNull BlockRange range) {
		return new BlockVolume(baseBlock, range);
	}

	/**
	 * 构成方块体的方块。
	 *
	 * @since 1.0.3
	 */
	public @NotNull Block baseBlock() {
		return baseBlock;
	}

	/**
	 * 结构范围。
	 *
	 * @since 1.0.3
	 */
	public @NotNull BlockRange range() {
		return range;
	}

	/**
	 * 结构原点（长方体最小角方块坐标），同时是结构的标识。
	 *
	 * @since 1.0.3
	 */
	public @NotNull BlockPos masterPos() {
		return range.start();
	}

	/**
	 * 结构的末端坐标（东南偏上位置，含边界）。
	 *
	 * @since 1.0.3
	 */
	public @NotNull BlockPos endPos() {
		return range.end();
	}

	/**
	 * 结构的体积（方块数量）。
	 *
	 * @since 1.0.3
	 */
	public int getVolume() {
		return range.volume();
	}

	/**
	 * 判断指定的世界位置是否在当前方块体范围内。
	 *
	 * @since 1.0.3
	 */
	public boolean containsWorldPos(@NotNull BlockPos worldPos) {
		return range.contains(worldPos);
	}

	/**
	 * 根据相对坐标计算世界坐标。
	 *
	 * @throws IllegalArgumentException 如果相对坐标超出范围
	 * @since 1.0.3
	 */
	public @NotNull BlockPos getWorldPos(int relativeX, int relativeY, int relativeZ) {
		if (relativeX < 0 || relativeX >= range.width()
				|| relativeY < 0 || relativeY >= range.height()
				|| relativeZ < 0 || relativeZ >= range.depth()) {
			throw new IllegalArgumentException(
					String.format("Relative coordinates (%d, %d, %d) out of range [0-%d, 0-%d, 0-%d]",
							relativeX, relativeY, relativeZ,
							range.width() - 1, range.height() - 1, range.depth() - 1));
		}
		return masterPos().add(relativeX, relativeY, relativeZ);
	}

	/**
	 * 获取相对坐标对应的世界坐标。
	 *
	 * @since 1.0.3
	 */
	public @NotNull BlockPos getWorldPos(@NotNull BlockPos relativePos) {
		return getWorldPos(relativePos.getX(), relativePos.getY(), relativePos.getZ());
	}

	/**
	 * 获取世界位置对应的相对位置。
	 *
	 * @return 相对位置；若世界位置不在范围内返回 {@code null}
	 * @since 1.0.3
	 */
	@Nullable
	public BlockPos getRelativePosFromWorld(@NotNull BlockPos worldPos) {
		if (!containsWorldPos(worldPos)) {
			return null;
		}
		return worldPos.subtract(masterPos());
	}

	/**
	 * 检查结构是否完整：范围内所有方块都是基础方块类型。
	 *
	 * <p>范围中未加载区块内的方块<b>不参与检查</b>（读取未加载区块会强制加载它，在区块加载
	 * 等流程中重入加载会造成死锁）；未加载部分会在其区块加载后由
	 * {@link BlockVolumeManager#onChunkLoad} 再次校验。</p>
	 *
	 * @param world 用于对照的世界视图
	 * @since 1.0.3
	 */
	public boolean checkIntegrity(@NotNull WorldView world) {
		BlockPos start = range.start();
		BlockPos end = range.end();

		for (int x = start.getX(); x <= end.getX(); x++) {
			for (int y = start.getY(); y <= end.getY(); y++) {
				for (int z = start.getZ(); z <= end.getZ(); z++) {
					BlockPos checkPos = new BlockPos(x, y, z);
					// 未加载区块内的方块不参与检查（读取未加载区块会强制加载它，在区块加载
					// 等流程中重入加载会造成死锁）；getChunk 的 create=false 不会强制加载
					if (world.getChunk(checkPos.getX() >> 4, checkPos.getZ() >> 4, ChunkStatus.FULL, false) == null) {
						continue;
					}
					if (world.getBlockState(checkPos).getBlock() != baseBlock) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * 序列化为 NBT，供客户端轻量快照同步（客户端没有服务端索引）。
	 *
	 * @since 1.0.3
	 */
	public @NotNull NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.put("MasterPos", NbtHelper.fromBlockPos(masterPos()));
		nbt.putString("BaseBlock", Registries.BLOCK.getId(baseBlock).toString());
		nbt.putInt("Width", range.width());
		nbt.putInt("Height", range.height());
		nbt.putInt("Depth", range.depth());
		return nbt;
	}

	/**
	 * 从 NBT 反序列化（客户端快照）。方块体不持世界，因此无需世界参数。
	 *
	 * @return 反序列化失败时返回 {@code null}
	 * @since 1.0.3
	 */
	@Nullable
	public static BlockVolume fromNbt(@NotNull NbtCompound nbt) {
		try {
			BlockPos masterPos = NbtHelper.toBlockPos(nbt.getCompound("MasterPos"));
			Block baseBlock = Registries.BLOCK.get(Identifier.tryParse(nbt.getString("BaseBlock")));
			int width = nbt.getInt("Width");
			int height = nbt.getInt("Height");
			int depth = nbt.getInt("Depth");
			return of(baseBlock, new BlockRange(masterPos, width, height, depth));
		} catch (Exception e) {
			LOGGER.error("Failed to deserialize BlockVolume from NBT: {}", e.getMessage());
			return null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		BlockVolume that = (BlockVolume) obj;
		return Objects.equals(baseBlock, that.baseBlock) && Objects.equals(range, that.range);
	}

	@Override
	public int hashCode() {
		return Objects.hash(baseBlock, range);
	}

	@Override
	public String toString() {
		return String.format("BlockVolume{master=%s, baseBlock=%s, size=%dx%dx%d}",
				masterPos(), baseBlock, range.width(), range.height(), range.depth());
	}
}
