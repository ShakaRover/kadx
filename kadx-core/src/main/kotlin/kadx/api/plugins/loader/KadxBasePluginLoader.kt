package kadx.api.plugins.loader

import kadx.api.plugins.KadxPlugin
import java.io.IOException
import java.util.ArrayList
import java.util.ServiceLoader

/**
 * 从当前 classpath 加载插件的默认实现。
 *
 * **做什么**：通过 Java [ServiceLoader] 发现 `META-INF/services/kadx.api.plugins.KadxPlugin`
 * 中声明的插件类并实例化。
 *
 * **为什么保留公开 API**：kadx-cli / kadx-gui / 各插件测试都直接
 * `new KadxBasePluginLoader()`，`load()` 与 `close()` 的签名必须不变。
 */
class KadxBasePluginLoader : KadxPluginLoader {

	override fun load(): List<KadxPlugin> {
		val list = ArrayList<KadxPlugin>()
		val plugins = ServiceLoader.load(KadxPlugin::class.java)
		for (plugin in plugins) {
			list.add(plugin)
		}
		return list
	}

	@Throws(IOException::class)
	override fun close() {
		// nothing to close
	}
}
