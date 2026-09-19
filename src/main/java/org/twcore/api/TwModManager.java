package org.twcore.api;

import org.twcore.mod.TwModManagerException;
import org.twcore.mod.TwModManagerImp;

import java.util.Map;

/**
 * <h1>TW 模组管理中心</h1>
 * <p>
 * 该接口定义了所有 TW 系列模组与 TW Core 之间的注册与查询协议。
 * </p>
 *
 * <h2>使用规则</h2>
 * <ol>
 *     <li>所有基于 TW Core 的子模组<b>必须</b>在 TW Core 注册事件中
 *         调用 {@link #register(String, int)} 完成注册。</li>
 *     <li>注册时需提供自己的 {@code modVersion}（一个正整数），表示该模组的
 *         API 等级。该数字越大代表需要的 API 越新。</li>
 *     <li>TW Core 内部维护了一份各子模组的“最低允许等级”表。如果某个子模组
 *         提供的 {@code modVersion} 低于该表要求，则注册会立即失败，游戏将无法
 *         继续启动。</li>
 *     <li>注册成功后，其他模组可通过 {@link #isRegistered(String)} 或
 *         {@link #getRegisteredVersion(String)} 查询某一子模组是否已加载，并据此
 *         安全地使用 TW Core 提供的公共数据，而无需任何软依赖检查。</li>
 *     <li>所有注册信息在模组加载阶段完成后即视为只读，运行期不应再调用
 *         {@code register}。</li>
 * </ol>
 *
 * <h2>线程安全</h2>
 * <p>
 * 此接口的所有实现都必须保证线程安全。TW Core 默认实现使用读写锁。
 * </p>
 *
 * @see TwCoreRegistrar
 */
public interface TwModManager {

    /** Core 的默认实现实例：子模组通过它注册自身、查询其他模组的加载状态。 */
    TwModManager IMPL = TwModManagerImp.getInstance();

    /**
     * 注册一个 TW 子模组。
     *
     * @param modId      模组 ID，不能为 {@code null}
     * @param modVersion 该模组的 API 等级，必须 {@code >= 1}
     * @throws NullPointerException      如果 {@code modId == null}
     * @throws IllegalArgumentException 如果 {@code modVersion <= 0}，
     *                                   或 {@code modId} 已注册过
     * @throws TwModManagerException     如果提供的 {@code modVersion}
     *                                   低于该模组要求的最低等级
     */
    void register(String modId, int modVersion);

    /**
     * 获取所有已成功注册的模组信息。
     *
     * @return 不可修改的模组 ID 到其 API 等级的映射，按注册顺序排列
     */
    Map<String, Integer> getRegisteredMods();

    /**
     * 检查某个模组是否已完成注册。
     *
     * @param modId 模组 ID
     * @return {@code true} 如果已注册
     */
    boolean isRegistered(String modId);

    /**
     * 获取某个模组注册的 API 等级。
     *
     * @param modId 模组 ID
     * @return 该模组注册的 API 等级；如果未注册则返回 {@code -1}
     */
    int getRegisteredVersion(String modId);
}
