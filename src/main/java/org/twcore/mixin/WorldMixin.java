package org.twcore.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.twcore.blockvolume.BlockVolumeEvents;

/**
 * 方块体系统的运行时感知入口：方块状态变化后驱动结构维护。
 *
 * <p>所有方块变化（放置/破坏/替换）最终都经过 {@link World} 的
 * {@code setBlockState(BlockPos, BlockState, int, int)}（其它重载均为委托），
 * 在此 RETURN 处统一处理，覆盖玩家、活塞、命令、爆炸、液体等一切变化路径。
 * 处理逻辑见 {@link BlockVolumeEvents#onBlockChanged}。</p>
 */
@Mixin(World.class)
public class WorldMixin {

	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
	private void onBlockStateChanged(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
									CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			BlockVolumeEvents.onBlockChanged((World) (Object) this, pos, state);
		}
	}
}
