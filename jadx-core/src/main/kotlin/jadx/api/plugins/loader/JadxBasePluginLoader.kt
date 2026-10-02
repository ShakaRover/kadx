package jadx.api.plugins.loader

import jadx.api.plugins.JadxPlugin
import java.io.IOException
import java.util.ArrayList
import java.util.ServiceLoader

/**
 * 从当前 classpath 加载插件的默认实现。
 *
 * **做什么**：通过 Java [ServiceLoader] 发现 `META-INF/services/jadx.api.plugins.JadxPlugin`
 * 中声明的插件类并实例化。
 *
 * **为什么保留公开 API**：jadx-cli / jadx-gui / 各插件测试都直接
 * `new JadxBasePluginLoader()`，`load()` 与 `close()` 的签名必须不变。
 */
class JadxBasePluginLoader : JadxPluginLoader {

	override fun load(): List<JadxPlugin> {
		val list = ArrayList<JadxPlugin>()
		val plugins = ServiceLoader.load(JadxPlugin::class.java)
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
