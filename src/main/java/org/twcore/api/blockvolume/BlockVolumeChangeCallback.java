package org.twcore.api.blockvolume;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

/**
 * 方块体变化事件的回调。
 *
 * <p>方块体集合发生增删时触发（见 {@link BlockVolumeRegistry#CHANGE}）。回调参数给出
 * 变化的方块体与变化类型；监听方自行判断该方块体是否与自己相关（如基础方块是否匹配、
 * 范围是否覆盖自己的位置），再决定是否响应。</p>
 *
 * <p><b>注意</b>：回调在服务端主线程、方块体系统内部流程中同步执行，回调内<b>不要</b>
 * 修改方块或方块体结构（会递归触发），只应读取状态并更新方块实体。</p>
 *
 * @see BlockVolumeRegistry#CHANGE
 * @see BlockVolumeChangeType
 */
@FunctionalInterface
public interface BlockVolumeChangeCallback {

	/**
	 * 方块体集合变化回调。
	 *
	 * @param world  服务端世界
	 * @param volume 发生变化的方块体（被注册或注销的那个）
	 * @param type   变化类型
	 */
	void onBlockVolumeChanged(@NotNull ServerWorld world, @NotNull BlockVolume volume, @NotNull BlockVolumeChangeType type);
}
