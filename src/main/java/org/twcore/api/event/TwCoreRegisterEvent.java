package org.twcore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.twcore.api.TwCoreRegistrar;

/**
 * <h1>TW Core 通用注册事件（Fabric 端）</h1>
 * <p>
 * 该事件在 {@code MinecraftClient} 或 {@code MinecraftDedicatedServer}
 * 实例构造完成时由 Core 通过 Mixin 触发。此时所有模组已完成初始化，
 * 注册表已冻结可用，是执行通用注册逻辑的理想时机。
 * </p>
 * <p>
 * 子模组通过 {@link #TW_CORE_REGISTRAR} 注册一个回调（实现
 * {@link TwCoreRegistrar} 接口），在该回调中集中完成模组注册、
 * 配置注册、影响器提交等操作。Core 会在所有回调执行完毕后
 * 统一加载双端配置。
 * </p>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * TwCoreRegisterEvent.TW_CORE_REGISTRAR.register(() -> {
 *     TwModManager.IMPL.register("my_mod", 2);
 *     TwConfig config = TwConfig.forMod("my_mod");
 *     config.registerConfig(...);
 * });
 * }</pre>
 *
 * @see TwCoreRegistrar
 */
public class TwCoreRegisterEvent {
    public static final Event<TwCoreRegistrar> TW_CORE_REGISTRAR = EventFactory.createArrayBacked(TwCoreRegistrar.class, callbacks -> () -> {
        for (TwCoreRegistrar callback : callbacks) {
            callback.register();
        }
    });
}
