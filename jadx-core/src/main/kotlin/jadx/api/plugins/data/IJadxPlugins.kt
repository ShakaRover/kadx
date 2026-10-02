package jadx.api.plugins.data

import jadx.api.plugins.JadxPlugin

/**
 * 已加载插件的查询接口。
 *
 * **做什么**：按插件 id、按提供的功能 id、或按插件类查找其运行时数据。
 *
 * **为什么保持 Java 可实现**：实现类 `JadxPluginsData` 是 jadx-core 的 Java 类，
 * 泛型方法 `getInstance(Class<P>)` 的签名保持与原 Java 一致。
 */
interface IJadxPlugins {

	/**
	 * 按插件 id 查找；找不到时抛出异常。
	 */
	fun getById(pluginId: String): JadxPluginRuntimeData

	/**
	 * 按插件提供的功能 id（`provides`）查找；找不到时抛出异常。
	 */
	fun getProviding(provideId: String): JadxPluginRuntimeData

	/**
	 * 按插件类查找插件实例；找不到时抛出异常。
	 */
	fun <P : JadxPlugin> getInstance(pluginCls: Class<P>): P
}
