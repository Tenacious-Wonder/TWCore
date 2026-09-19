package org.twcore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.twcore.api.TwCoreClientRegistrar;

/**
 * <h1>TW Core 客户端专属注册事件（Fabric 端）</h1>
 * <p>
 * 该事件仅在物理客户端触发，由 Core 在 {@code MinecraftClient}
 * 构造完成后、通用注册事件之后立即调用。用于注册客户端独有配置
 * 和其他客户端初始化逻辑。
 * </p>
 * <p>
 * 子模组通过 {@link #TW_CORE_CLIENT_REGISTRAR} 注册一个回调
 * （实现 {@link TwCoreClientRegistrar} 接口）。Core 在所有回调
 * 执行完毕后统一加载客户端配置。
 * </p>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * TwCoreClientRegisterEvent.TW_CORE_CLIENT_REGISTRAR.register(() -> {
 *     TwConfig config = TwConfig.forMod("my_mod");
 *     config.registerClientConfig(...);
 * });
 * }</pre>
 *
 * @see TwCoreClientRegistrar
 */
public class TwCoreClientRegisterEvent {
    public static final Event<TwCoreClientRegistrar> TW_CORE_CLIENT_REGISTRAR = EventFactory.createArrayBacked(TwCoreClientRegistrar.class, callbacks -> () -> {
        for (TwCoreClientRegistrar callback : callbacks) {
            callback.registerClient();
        }
    });
}
