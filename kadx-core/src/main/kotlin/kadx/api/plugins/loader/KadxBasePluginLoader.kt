package kadx.api.plugins.loader

import kadx.api.plugins.KadxPlugin
import java.io.IOException
import java.util.ArrayList
import java.util.ServiceLoader
import java.util.function.Predicate
import kotlin.streams.toList

/**
 * 从当前 classpath 加载插件的默认实现。
 *
 * **做什么**：通过 Java [ServiceLoader] 发现 `META-INF/services/kadx.api.plugins.KadxPlugin`
 * 中声明的插件类并实例化；可用 [pluginClassFilter] 只取其中一部分（kadx-gui 用它区分
 * 「全局 GUI 插件」与「项目插件」，两者分别在各自的插件管理器里加载）。
 *
 * **为什么保留公开 API**：kadx-cli / kadx-gui / 各插件测试都直接
 * `new KadxBasePluginLoader()`，`load()` 与 `close()` 的签名必须不变。
 */
class KadxBasePluginLoader @JvmOverloads constructor(
	private val pluginClassFilter: Predicate<Class<*>> = Predicate { true },
) : KadxPluginLoader {

	override fun load(): List<KadxPlugin> {
		val list = ArrayList<KadxPlugin>()
		// 用 Provider.type() 先判类型再实例化：被过滤掉的插件类不应被 new 出来
		for (provider in ServiceLoader.load(KadxPlugin::class.java).stream().toList()) {
			if (pluginClassFilter.test(provider.type())) {
				list.add(provider.get())
			}
		}
		return list
	}

	@Throws(IOException::class)
	override fun close() {
		// nothing to close
	}
}
