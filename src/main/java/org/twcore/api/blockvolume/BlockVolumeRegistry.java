package org.twcore.api.blockvolume;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.twcore.blockvolume.BlockVolumeManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>方块体注册表</h1>
 * <p>
 * 方块体系统的对外入口：消费方在此<b>注册参与方块体系统的方块</b>，此后系统自动感知这些方块的
 * 放置与破坏并维护结构，无需在方块类中手动接线。归属查询（"某个位置属于哪个方块体"）也经本类暴露。
 * </p>
 *
 * <h2>注册语义</h2>
 * <p>
 * 只有注册过的方块才会被系统感知：在注册方块的位置放置/破坏方块会驱动结构的创建、合并与拆分；
 * 未注册方块的世界变化被忽略。注册是设计声明——方块体通常用于有明确结构意图的方块，系统不会对
 * 未注册方块做任何事。
 * </p>
 *
 * <h2>消费方接入</h2>
 * <p>
 * 模组初始化时注册一次：
 * </p>
 * <pre>{@code
 * BlockVolumeRegistry.register(MySlateBlock.INSTANCE);
 * }</pre>
 * <p>
 * 需要查询归属时（服务端逻辑，普通方块与方块实体一视同仁）：
 * </p>
 * <pre>{@code
 * BlockVolume volume = BlockVolumeRegistry.findBlockVolume(world, pos);
 * if (volume != null) {
 *     boolean isMaster = pos.equals(volume.masterPos());
 * }
 * }</pre>
 *
 * @since 1.0.3
 * @see BlockVolume
 * @see org.twcore.blockvolume.BlockVolumeManager
 */
public final class BlockVolumeRegistry {
	private static final Set<Block> REGISTERED = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/**
	 * 方块体变化事件：世界中的方块体被注册或注销时触发。
	 *
	 * <p>监听方检查变化方块体是否与自己相关（如基础方块匹配、范围覆盖自己的位置），
	 * 再决定是否响应。回调见 {@link BlockVolumeChangeCallback}。</p>
	 *
	 * @since 1.0.3
	 * @see BlockVolumeChangeCallback
	 * @see BlockVolumeChangeType
	 */
	public static final Event<BlockVolumeChangeCallback> CHANGE = EventFactory.createArrayBacked(
			BlockVolumeChangeCallback.class,
			(listeners) -> (world, volume, type) -> {
				for (BlockVolumeChangeCallback listener : listeners) {
					listener.onBlockVolumeChanged(world, volume, type);
				}
			});

	private BlockVolumeRegistry() {}

	/**
	 * 注册参与方块体系统的方块。重复注册无副作用。
	 *
	 * @since 1.0.3
	 */
	public static void register(Block... blocks) {
		Collections.addAll(REGISTERED, blocks);
	}

	/**
	 * 取消注册方块。取消后系统不再感知它的放置与破坏（已存在的结构记录不受影响）。
	 *
	 * @return 若该方块此前已注册返回 {@code true}
	 * @since 1.0.3
	 */
	public static boolean unregister(Block block) {
		return REGISTERED.remove(block);
	}

	/**
	 * 判断方块是否已注册参与方块体系统。
	 *
	 * @since 1.0.3
	 */
	public static boolean isRegistered(Block block) {
		return block != null && REGISTERED.contains(block);
	}

	/**
	 * 查找包含指定位置的方块体。
	 *
	 * @return 覆盖该位置的方块体；不属于任何方块体时返回 {@code null}
	 * @since 1.0.3
	 */
	@Nullable
	public static BlockVolume findBlockVolume(World world, BlockPos pos) {
		return BlockVolumeManager.findBlockVolume(world, pos);
	}
}
