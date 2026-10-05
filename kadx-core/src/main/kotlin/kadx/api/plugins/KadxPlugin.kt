package kadx.api.plugins

import kadx.api.plugins.pass.types.KadxAfterLoadPass
import kadx.api.plugins.pass.types.KadxPreparePass

/**
 * 所有 kadx 插件的基础接口。
 *
 * **如何创建一个插件**：实现本接口，并在资源目录放置
 * `META-INF/services/kadx.api.plugins.KadxPlugin` 文件，内容为你的实现类全限定名。
 *
 * **为什么方法名保持 Java 风格**：这是插件的扩展面，Java 第三方插件直接实现它，
 * 因此 getter 一律保留显式 `getXxx()` 函数，保证 Java 调用方零改动。
 */
interface KadxPlugin {

	/**
	 * 提供插件信息（名称、描述等）。
	 * 可能被调用多次，实现里不要做有副作用的初始化。
	 */
	fun getPluginInfo(): KadxPluginInfo

	/**
	 * 初始化插件。
	 * 用 [KadxPluginContext] 注册 pass、代码输入与选项。
	 * 耗时操作请优先放到 [KadxPreparePass] 或 [KadxAfterLoadPass] 中执行。
	 */
	fun init(context: KadxPluginContext)

	/**
	 * 插件卸载回调，可用于释放资源。
	 * 默认空实现（Java 实现方无需覆写）。
	 */
	fun unload() {
		// 可选方法，默认什么都不做
	}
}
