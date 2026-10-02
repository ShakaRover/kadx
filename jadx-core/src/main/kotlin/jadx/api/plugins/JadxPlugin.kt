package jadx.api.plugins

import jadx.api.plugins.pass.types.JadxAfterLoadPass
import jadx.api.plugins.pass.types.JadxPreparePass

/**
 * 所有 jadx 插件的基础接口。
 *
 * **如何创建一个插件**：实现本接口，并在资源目录放置
 * `META-INF/services/jadx.api.plugins.JadxPlugin` 文件，内容为你的实现类全限定名。
 *
 * **为什么方法名保持 Java 风格**：这是插件的扩展面，Java 第三方插件直接实现它，
 * 因此 getter 一律保留显式 `getXxx()` 函数，保证 Java 调用方零改动。
 */
interface JadxPlugin {

	/**
	 * 提供插件信息（名称、描述等）。
	 * 可能被调用多次，实现里不要做有副作用的初始化。
	 */
	fun getPluginInfo(): JadxPluginInfo

	/**
	 * 初始化插件。
	 * 用 [JadxPluginContext] 注册 pass、代码输入与选项。
	 * 耗时操作请优先放到 [JadxPreparePass] 或 [JadxAfterLoadPass] 中执行。
	 */
	fun init(context: JadxPluginContext)

	/**
	 * 插件卸载回调，可用于释放资源。
	 * 默认空实现（Java 实现方无需覆写）。
	 */
	fun unload() {
		// 可选方法，默认什么都不做
	}
}
