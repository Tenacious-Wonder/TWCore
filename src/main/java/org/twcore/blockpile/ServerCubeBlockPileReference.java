package org.twcore.blockpile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.TWCore;
import org.twcore.api.blockpile.CubeBlockPile;
import org.twcore.api.blockpile.CubeBlockPileReference;

import java.util.Objects;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.api.blockvolume} 包。</p>
 *
 * 服务端方块堆引用实现
 */
@Deprecated
public class ServerCubeBlockPileReference implements CubeBlockPileReference {
    private static final Logger LOGGER = TWCore.LOGGER;

    private final CubeBlockPile cubeBlockPile;
    private final BlockPos relativePos;
    private final BlockPos worldPos;

    public ServerCubeBlockPileReference(@NotNull CubeBlockPile cubeBlockPile, @NotNull BlockPos relativePos) {
        this.cubeBlockPile = Objects.requireNonNull(cubeBlockPile, "CubeBlockPile cannot be null");
        this.relativePos = Objects.requireNonNull(relativePos, "Relative position cannot be null");
        this.worldPos = cubeBlockPile.getWorldPos(relativePos);

        // 验证相对坐标是否在有效范围内
        validateRelativePosition();
    }

    /**
     * 验证相对坐标是否在方块堆的有效范围内
     */
    private void validateRelativePosition() {
        if (relativePos.getX() < 0 || relativePos.getX() >= cubeBlockPile.getRange().getWidth() ||
                relativePos.getY() < 0 || relativePos.getY() >= cubeBlockPile.getRange().getHeight() ||
                relativePos.getZ() < 0 || relativePos.getZ() >= cubeBlockPile.getRange().getDepth()) {
            throw new IllegalArgumentException("Relative position " + relativePos +
                    " is out of CubeBlockPile range " + cubeBlockPile.getRange());
        }
    }

    /**
     * 从世界坐标创建服务端引用
     */
    @Nullable
    public static ServerCubeBlockPileReference fromWorldPos(@NotNull CubeBlockPile cubeBlockPile, @NotNull BlockPos worldPos) {
        if (cubeBlockPile.isDisposed()) {
            return null;
        }

        if (!cubeBlockPile.getRange().contains(worldPos)) {
            LOGGER.warn("World position {} is not within CubeBlockPile range {}",
                    worldPos, cubeBlockPile.getRange());
            return null;
        }

        BlockPos masterPos = cubeBlockPile.getMasterPos();
        BlockPos relativePos = new BlockPos(
                worldPos.getX() - masterPos.getX(),
                worldPos.getY() - masterPos.getY(),
                worldPos.getZ() - masterPos.getZ()
        );

        return new ServerCubeBlockPileReference(cubeBlockPile, relativePos);
    }

    /**
     * 从世界坐标创建通用引用（服务端或客户端）
     */
    @Nullable
    public static CubeBlockPileReference fromWorldPos(@NotNull WorldView world, @NotNull BlockPos worldPos, boolean createClientReference) {
        CubeBlockPile cubeBlockPile = CubeBlockPileManager.findCubeBlockPile(world, worldPos);
        if (cubeBlockPile == null || cubeBlockPile.isDisposed()) {
            return null;
        }

        BlockPos masterPos = cubeBlockPile.getMasterPos();
        BlockPos relativePos = new BlockPos(
                worldPos.getX() - masterPos.getX(),
                worldPos.getY() - masterPos.getY(),
                worldPos.getZ() - masterPos.getZ()
        );

        try {
            if (createClientReference && world.isClient()) {
                // 在客户端创建客户端引用
                ServerCubeBlockPileReference serverRef = new ServerCubeBlockPileReference(cubeBlockPile, relativePos);
                return ClientCubeBlockPileReference.fromServerReference(serverRef);
            } else {
                // 在服务端创建服务端引用
                return new ServerCubeBlockPileReference(cubeBlockPile, relativePos);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warn("World position {} is not within CubeBlockPile range", worldPos);
            return null;
        }
    }

    /**
     * 从相对坐标创建引用
     */
    @NotNull
    public static CubeBlockPileReference fromRelativePos(@NotNull CubeBlockPile cubeBlockPile, @NotNull BlockPos relativePos) {
        return new ServerCubeBlockPileReference(cubeBlockPile, relativePos);
    }

    /**
     * 从NBT反序列化引用（服务端版本）
     */
    @Nullable
    public static ServerCubeBlockPileReference fromNbt(@NotNull WorldView world, @NotNull NbtCompound nbt) {
        try {
            // 读取主方块坐标
            BlockPos masterPos = NbtHelper.toBlockPos(nbt.getCompound(MASTER_POS_KEY));

            // 读取相对坐标
            BlockPos relativePos = NbtHelper.toBlockPos(nbt.getCompound(RELATIVE_POS_KEY));

            // 读取基础方块
            String blockId = nbt.getString(BASE_BLOCK_KEY);
            Block baseBlock = net.minecraft.registry.Registries.BLOCK.get(Identifier.tryParse(blockId));

            // 查找对应的CubeBlockPile（服务端引用）
            CubeBlockPile cubeBlockPile = CubeBlockPileManager.findCubeBlockPile(world, masterPos);
            if (cubeBlockPile == null || cubeBlockPile.isDisposed()) {
                LOGGER.warn("CubeBlockPile at {} not found or disposed during deserialization", masterPos);
                return null;
            }

            // 验证基础方块是否匹配
            if (cubeBlockPile.getBaseBlock() != baseBlock) {
                LOGGER.warn("Base block mismatch during deserialization. Expected: {}, Found: {}",
                        baseBlock, cubeBlockPile.getBaseBlock());
                return null;
            }

            // 创建服务端引用
            return new ServerCubeBlockPileReference(cubeBlockPile, relativePos);

        } catch (Exception e) {
            LOGGER.error("Failed to deserialize CubeBlockPileReference from NBT: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @NotNull
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        // 主方块坐标
        nbt.put(MASTER_POS_KEY, NbtHelper.fromBlockPos(cubeBlockPile.getMasterPos()));

        // 相对坐标
        nbt.put(RELATIVE_POS_KEY, NbtHelper.fromBlockPos(relativePos));

        // 基础方块
        nbt.putString(BASE_BLOCK_KEY, net.minecraft.registry.Registries.BLOCK.getId(cubeBlockPile.getBaseBlock()).toString());

        // 结构尺寸
        nbt.putInt(STRUCTURE_WIDTH_KEY, getStructureWidth());
        nbt.putInt(STRUCTURE_HEIGHT_KEY, getStructureHeight());
        nbt.putInt(STRUCTURE_DEPTH_KEY, getStructureDepth());

        return nbt;
    }

    @Override
    public boolean matchesBlock(Block block) {
        if (cubeBlockPile.isDisposed()) {
            LOGGER.warn("Attempted to check block match with disposed CubeBlockPile");
            return false;
        }
        return cubeBlockPile.getBaseBlock() == block;
    }

    @Override
    public boolean matchesBlockState(BlockState blockState) {
        if (blockState != null) {
            return matchesBlock(blockState.getBlock());
        }
        return false;
    }

    @Override
    public boolean matchesBlockEntity(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        return matchesBlockState(blockEntity.getCachedState());
    }

    @Override
    public boolean isMasterBlock() {
        return relativePos.getX() == 0 && relativePos.getY() == 0 && relativePos.getZ() == 0;
    }

    @Override
    public @NotNull BlockPos getMasterWorldPos() {
        if (cubeBlockPile.isDisposed()) {
            throw new IllegalStateException("CubeBlockPile has been disposed");
        }
        return cubeBlockPile.getMasterPos();
    }

    @Override
    public @NotNull BlockPos getWorldPos() {
        return worldPos;
    }

    @Override
    public @NotNull BlockPos getRelativePos() {
        return relativePos;
    }

    @Override
    public @NotNull Block getBaseBlock() {
        if (cubeBlockPile.isDisposed()) {
            throw new IllegalStateException("CubeBlockPile has been disposed");
        }
        return cubeBlockPile.getBaseBlock();
    }

    @Override
    public int getVolume() {
        return cubeBlockPile.isDisposed() ? 0 : cubeBlockPile.getVolume();
    }

    @Override
    public boolean containsWorldPos(@NotNull BlockPos worldPos) {
        return !cubeBlockPile.isDisposed() && cubeBlockPile.getRange().contains(worldPos);
    }

    @Override
    public boolean checkIntegrity() {
        if (cubeBlockPile.isDisposed()) {
            LOGGER.warn("Attempted to check integrity of disposed CubeBlockPile");
            return false;
        }
        return cubeBlockPile.checkIntegrity();
    }

    @Override
    public boolean isDisposed() {
        return cubeBlockPile.isDisposed();
    }

    @Override
    public boolean isValid() {
        return !cubeBlockPile.isDisposed() && cubeBlockPile.checkIntegrity();
    }

    @Override
    public void dispose() {
        // 这里只是清理引用，实际的CubeBlockPile由CubeBlockPileManager管理
    }

    @Override
    public int getRelativeX() {
        return relativePos.getX();
    }

    @Override
    public int getRelativeY() {
        return relativePos.getY();
    }

    @Override
    public int getRelativeZ() {
        return relativePos.getZ();
    }

    @Override
    public int getStructureWidth() {
        return cubeBlockPile.isDisposed() ? 0 : cubeBlockPile.getRange().getWidth();
    }

    @Override
    public int getStructureHeight() {
        return cubeBlockPile.isDisposed() ? 0 : cubeBlockPile.getRange().getHeight();
    }

    @Override
    public int getStructureDepth() {
        return cubeBlockPile.isDisposed() ? 0 : cubeBlockPile.getRange().getDepth();
    }

    /**
     * 获取方块堆堆实例（仅服务端可用）
     */
    public CubeBlockPile getCubeBlockPile() {
        return cubeBlockPile;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ServerCubeBlockPileReference that = (ServerCubeBlockPileReference) obj;
        return Objects.equals(cubeBlockPile, that.cubeBlockPile) &&
                Objects.equals(relativePos, that.relativePos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cubeBlockPile, relativePos);
    }

    @Override
    public String toString() {
        return String.format("ServerCubeBlockPileReference{multiBlock=%s, relativePos=%s, worldPos=%s, isMaster=%b, size=%dx%dx%d}",
                cubeBlockPile.getMasterPos(), relativePos, worldPos, isMasterBlock(),
                getStructureWidth(), getStructureHeight(), getStructureDepth());
    }
}