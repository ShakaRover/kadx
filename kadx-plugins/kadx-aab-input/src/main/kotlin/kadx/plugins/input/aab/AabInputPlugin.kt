package kadx.plugins.input.aab

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.resources.IResourcesLoader
import kadx.plugins.input.aab.factories.ProtoAppDependenciesResContainerFactory
import kadx.plugins.input.aab.factories.ProtoAssetsConfigResContainerFactory
import kadx.plugins.input.aab.factories.ProtoBundleConfigResContainerFactory
import kadx.plugins.input.aab.factories.ProtoNativeConfigResContainerFactory
import kadx.plugins.input.aab.factories.ProtoTableResContainerFactory
import kadx.plugins.input.aab.factories.ProtoXmlResContainerFactory

/**
 * AAB 输入插件：注册 protobuf 资源解析器与容器工厂。
 *
 **背景**：init()（原 Java 为 synchronized）向 IResourcesLoader 添加
 * ResTableProtoParserProvider 和 6 个 proto 容器工厂，使 kadx 能读取 .aab 内的
 * resources.pb / BundleConfig.pb / assets.pb / native.pb / dependencies.pb / xml。
 */
public class AabInputPlugin : KadxPlugin {

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfo(PLUGIN_ID, ".AAB Input", "Loads .AAB files.")

	@Synchronized
	override fun init(context: KadxPluginContext) {
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
