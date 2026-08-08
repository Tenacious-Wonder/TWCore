package org.twcore.api;

import org.twcore.api.config.TwConfig;
import org.twcore.api.event.TwCoreRegisterEvent;
import org.twcore.api.config.ConfigType;

/**
 * <h1>TW Core 统一注册入口</h1>
 * <p>
 * 所有基于 TW Core 的子模组<b>必须</b>实现此接口，并在对应平台的
 * 注册点将其提交给 Core。该接口的 {@link #register()} 方法
 * 是所有子模组向 Core API 注册自身信息的唯一且集中的位置。
 * </p>
 *
 * <h2>注册内容</h2>
 * <p>
 * 在 {@link #register()} 方法内，子模组通常需要完成以下操作
 * （具体视模组需求而定）：
 * <ul>
 *     <li>调用 {@link TwModManager#register(String, int)}
 *         完成模组自身的注册与版本等级声明；</li>
 *     <li>通过 {@link TwConfig#forMod(String)} 获取配置构造器，
 *         注册一个或多个 {@link ConfigType}；</li>
 *     <li>通过配置构造器向其他模组的配置提供默认值修改器
 *         （{@link TwConfig#addDefaultOverride}），实现跨模组默认值叠加；</li>
 *     <li>未来可能扩展的其他注册项（如网络频道、命令、数据组件等）。</li>
 * </ul>
 * 将所有注册逻辑集中于此方法内，可以确保在 Core 执行统一加载前，
 * 所有必要的信息均已就绪。
 * </p>
 *
 * <h2>平台集成方式</h2>
 * <p>
 * 子模组只需编写一个实现本接口的类，然后根据不同平台选择
 * 对应的“提交”方式。Core 会在适当的时机自动调用所有已提交的
 * 实现。
 * </p>
 *
 * <h3>Fabric</h3>
 * <p>
 * 使用 {@link TwCoreRegisterEvent} 的
 * {@code TW_CORE_REGISTRAR} 事件注册回调。Core 会在
 * {@code MinecraftClient} 或 {@code MinecraftDedicatedServer}
 * 构造完成后触发该事件。
 * </p>
 * <pre>{@code
 * TwCoreRegisterEvent.TW_CORE_REGISTRAR.register(() -> {
 *     // 你的注册逻辑
 * });
 * }</pre>
 *
 * <h3>Forge / NeoForge</h3>
 * <p>
 * 在模组主类或任何有事件监听能力的类中，编写一个监听
 * {@link TwCoreRegisterEvent} 的方法，并在该方法内调用
 * 本接口的实现。Core 会在 {@code FMLCommonSetupEvent}
 * 阶段触发该事件。
 * </p>
 * <pre>{@code
 * @Mod("example_mod")
 * public class ExampleMod {
 *     private final TwCoreRegistrar registrar = new MyRegistrar();
 *
 *     @SubscribeEvent
 *     public void onTwCoreRegister(TwCoreRegisterEvent event) {
 *         // 你的注册逻辑
 *     }
 * }
 * }</pre>
 *
 * <h2>调用时机与保证</h2>
 * <p>
 * Core 保证在所有子模组的 {@link #register()} 方法<b>全部执行完毕之后</b>
 * 才会进行统一加载。
 * </p>
 * <p>
 * 此方法在模组加载阶段被调用，运行于<b>主线程</b>，且每个实现
 * 只会被调用一次。严禁在此方法内执行耗时操作或引发异常未处理。
 * 若注册失败（如版本等级不满足），应通过抛出异常终止启动，
 * 这与 {@link TwModManager#register(String, int)} 的约定一致。
 * </p>
 *
 * <h2>关于跨模组信息访问的重要约定</h2>
 * <p>
 * 尽管在客户端注册阶段（{@code TwCoreClientRegistrar}）可以安全访问
 * {@link TwModManager} 中其他模组的注册信息（因为其时序在所有双端注册之后），
 * 但为了保持框架的<b>一致性</b>，<b>强烈建议在任何注册逻辑中都不要依赖其他模组
 * 是否已经注册</b>。
 * </p>
 * <p>
 * 原因如下：
 * <ul>
 *     <li>子模组在双端注册阶段（{@code TwCoreRegistrar}）的调用顺序是不确定的，
 *         因此在该阶段通过 {@code TwModManager.isRegistered()} 查询其他模组
 *         是不安全的，可能得到不一致的结果。</li>
 *     <li>统一采用“不依赖他人”的约定，可以避免开发者在不同注册接口之间
 *         产生混淆，降低因平台差异导致的隐性 Bug。</li>
 *     <li>若确实需要跨模组协作（如为其他模组的配置添加默认值），
 *         应使用 {@code TwConfig.addDefaultOverride()} 提供影响器，
 *         由目标模组在配置加载时统一处理——这是专门为顺序无关的
 *         软依赖设计的机制。</li>
 * </ul>
 * 简而言之：<b>注册时只关心自己，协作通过影响器完成。</b>
 * </p>
 *
 * @see TwModManager
 */
@FunctionalInterface
public interface TwCoreRegistrar {

    /**
     * 执行所有向 TW Core API 的注册操作。
     *
     * <p>此方法由 Core 在适当的阶段调用。</p>
     */
    void register();
}