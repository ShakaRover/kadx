package jadx.api.plugins.resources

/**
 * 资源加载器接口：允许插件注册自定义的资源内容工厂与资源表解析器提供者。
 *
 * **做什么**：[addResContainerFactory] 追加一种资源内容解析格式；
 * [addResTableParserProvider] 追加一种资源表（ARSC 等）解析器。
 *
 * **为什么保持 Java 可实现**：实现类为 jadx-core 的 `ResourcesLoader`，
 * 方法名与参数类型与原 Java 完全一致。
 */
interface IResourcesLoader {

	fun addResContainerFactory(resContainerFactory: IResContainerFactory)

	fun addResTableParserProvider(resTableParserProvider: IResTableParserProvider)
}
