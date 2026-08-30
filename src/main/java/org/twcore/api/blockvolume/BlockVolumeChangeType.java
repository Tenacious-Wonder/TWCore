package org.twcore.api.blockvolume;

/**
 * 方块体变化类型。
 */
public enum BlockVolumeChangeType {

	/** 新方块体被注册（放置、合并、拆分产生）。 */
	CREATED,

	/** 方块体被注销（破坏、合并移除）。 */
	REMOVED
}
