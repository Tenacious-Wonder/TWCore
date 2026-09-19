package org.twcore.blockvolume;

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
import org.twcore.api.blockvolume.BlockRange;
import org.twcore.api.blockvolume.BlockVolume;

import java.util.Objects;

/**
 * {@link BlockVolume} 的默认实现：以「构成方块 + 范围」为全部数据的不可变值对象。
 *
 * <p>内部实现类型，不面向外部调用者——请在 {@link BlockVolume} 接口上创建与使用，
 * 不要直接引用本类。</p>
 */
public final class BlockVolumeImpl implements BlockVolume {

    private static final Logger LOGGER = TWCore.LOGGER;

    private final Block baseBlock;
    private final BlockRange range;

    /**
     * 创建方块体实现实例。请改用 {@link BlockVolume#of(Block, BlockRange)}。
     *
     * @param baseBlock 构成方块体的方块，不能为 null
     * @param range     结构范围，不能为 null，且每条轴不超过 {@link BlockVolume#MAX_SIZE}
     * @throws IllegalArgumentException 如果范围任一条轴超过尺寸上限
     */
    public BlockVolumeImpl(@NotNull Block baseBlock, @NotNull BlockRange range) {
        this.baseBlock = Objects.requireNonNull(baseBlock, "Base block cannot be null");
        this.range = Objects.requireNonNull(range, "Range cannot be null");
        if (range.width() > MAX_SIZE || range.height() > MAX_SIZE || range.depth() > MAX_SIZE) {
            throw new IllegalArgumentException(
                    String.format("BlockVolume size %dx%dx%d exceeds maximum allowed size %dx%dx%d",
                            range.width(), range.height(), range.depth(), MAX_SIZE, MAX_SIZE, MAX_SIZE));
        }
    }

    /**
     * 从 NBT 快照恢复方块体，反序列化失败时记录错误并返回 {@code null}。
     *
     * @param nbt 由 {@link BlockVolume#toNbt()} 写出的快照
     * @return 方块体实例；失败时为 {@code null}
     */
    @Nullable
    public static BlockVolume fromNbt(@NotNull NbtCompound nbt) {
        try {
            BlockPos masterPos = NbtHelper.toBlockPos(nbt.getCompound("MasterPos"));
            Block baseBlock = Registries.BLOCK.get(Identifier.tryParse(nbt.getString("BaseBlock")));
            int width = nbt.getInt("Width");
            int height = nbt.getInt("Height");
            int depth = nbt.getInt("Depth");
            return new BlockVolumeImpl(baseBlock, new BlockRange(masterPos, width, height, depth));
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize BlockVolume from NBT: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public @NotNull Block baseBlock() {
        return baseBlock;
    }

    @Override
    public @NotNull BlockRange range() {
        return range;
    }

    @Override
    public @NotNull BlockPos masterPos() {
        return range.start();
    }

    @Override
    public @NotNull BlockPos endPos() {
        return range.end();
    }

    @Override
    public int getVolume() {
        return range.volume();
    }

    @Override
    public boolean containsWorldPos(@NotNull BlockPos worldPos) {
        return range.contains(worldPos);
    }

    @Override
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

    @Override
    public @NotNull BlockPos getWorldPos(@NotNull BlockPos relativePos) {
        return getWorldPos(relativePos.getX(), relativePos.getY(), relativePos.getZ());
    }

    @Override
    @Nullable
    public BlockPos getRelativePosFromWorld(@NotNull BlockPos worldPos) {
        if (!containsWorldPos(worldPos)) {
            return null;
        }
        return worldPos.subtract(masterPos());
    }

    @Override
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

    @Override
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
     * 按「构成方块 + 范围」判定相等，与 {@link #hashCode()} 保持一致。
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockVolume that)) return false;
        return Objects.equals(baseBlock, that.baseBlock()) && Objects.equals(range, that.range());
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
