package kadx.plugins.input.xapk

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder

/**
 * XApk 输入插件：注册 .xapk 文件的代码与资源加载能力。
 *
 * **背景**：init() 创建 [XApkLoader]（负责解包）和 [XApkCustomInput]
 * （同时实现代码输入与自定义资源加载器），并都注册到上下文；
 * unload() 时清理 loader 的临时目录。
 */
public class XApkInputPlugin : KadxPlugin {

	/** @Nullable init() 之前为 null */
	private var loader: XApkLoader? = null

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId("xapk-input")
		.name("XApk Input")
		.description("Load .xapk files")
		.build()

	override fun init(context: KadxPluginContext) {
		val newLoader = XApkLoader(context)
		loader = newLoader
		val customInput = XApkCustomInput(context, newLoader)
		context.addCodeInput(customInput)
		context.getDecompiler().addCustomResourcesLoader(customInput)
	}

	override fun unload() {
		loader?.unload()
	}
}
