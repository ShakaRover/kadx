package jadx.plugins.input.aab

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.resources.IResourcesLoader
import jadx.plugins.input.aab.factories.ProtoAppDependenciesResContainerFactory
import jadx.plugins.input.aab.factories.ProtoAssetsConfigResContainerFactory
import jadx.plugins.input.aab.factories.ProtoBundleConfigResContainerFactory
import jadx.plugins.input.aab.factories.ProtoNativeConfigResContainerFactory
import jadx.plugins.input.aab.factories.ProtoTableResContainerFactory
import jadx.plugins.input.aab.factories.ProtoXmlResContainerFactory

/**
 * AAB 输入插件：注册 protobuf 资源解析器与容器工厂。
 *
 **背景**：init()（原 Java 为 synchronized）向 IResourcesLoader 添加
 * ResTableProtoParserProvider 和 6 个 proto 容器工厂，使 jadx 能读取 .aab 内的
 * resources.pb / BundleConfig.pb / assets.pb / native.pb / dependencies.pb / xml。
 */
public class AabInputPlugin : JadxPlugin {

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfo(PLUGIN_ID, ".AAB Input", "Loads .AAB files.")

	@Synchronized
	override fun init(context: JadxPluginContext) {
		val resourcesLoader: IResourcesLoader = context.getResourcesLoader()
		val tableParserProvider = ResTableProtoParserProvider()
		resourcesLoader.addResTableParserProvider(tableParserProvider)

		resourcesLoader.addResContainerFactory(ProtoTableResContainerFactory(tableParserProvider))
		resourcesLoader.addResContainerFactory(ProtoXmlResContainerFactory())
		resourcesLoader.addResContainerFactory(ProtoBundleConfigResContainerFactory())
		resourcesLoader.addResContainerFactory(ProtoAssetsConfigResContainerFactory())
		resourcesLoader.addResContainerFactory(ProtoNativeConfigResContainerFactory())
		resourcesLoader.addResContainerFactory(ProtoAppDependenciesResContainerFactory())
	}

	public companion object {
		const val PLUGIN_ID: String = "aab-input"
	}
}
