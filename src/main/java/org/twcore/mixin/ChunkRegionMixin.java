package org.twcore.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.twcore.blockvolume.BlockVolumeEvents;

/**
 * 方块体系统的世界生成感知入口：生成阶段写入注册方块时创建方块体结构。
 *
 * <p>{@link ChunkRegion#setBlockState} 是世界生成阶段（结构生成、装饰等）写入方块的入口，
 * 在此 RETURN 处识别注册方块并创建/合并结构。生成中区块（{@code ProtoChunk}）同样支持
 * 附加数据，记录随区块升级与保存进入正式世界。</p>
 */
@Mixin(ChunkRegion.class)
public class ChunkRegionMixin {

	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
	private void onBlockPlacedInGeneration(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
										   CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			BlockVolumeEvents.onBlockPlacedInGeneration((ChunkRegion) (Object) this, pos, state);
		}
	}
}
