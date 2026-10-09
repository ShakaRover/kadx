package kadx.api.plugins.loader

import kadx.api.plugins.KadxPlugin
import java.io.Closeable

/**
 * 插件加载器接口：从某个来源加载插件列表，并可在退出时释放资源。
 *
 * **做什么**：[load] 返回所有可用插件；继承 [Closeable] 以便释放类加载器等资源。
 *
 * **为什么保持 Java 可实现**：[KadxBasePluginLoader]（本包）实现它，
 * 方法名与签名与原 Java 一致。
 */
interface KadxPluginLoader : Closeable {

	/**
	 * 加载并返回插件列表。
	 */
	fun load(): List<KadxPlugin>
}
