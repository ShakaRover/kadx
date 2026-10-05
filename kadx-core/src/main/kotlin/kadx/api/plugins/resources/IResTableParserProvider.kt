package kadx.api.plugins.resources

import kadx.api.ResourceFile
import kadx.core.dex.nodes.RootNode
import kadx.core.xmlgen.IResTableParser

/**
 * 资源表解析器提供者：为特定格式的资源表文件提供 [IResTableParser] 实例。
 *
 * **用法**：插件通过 `context.getResourcesLoader().addResTableParserProvider()` 注册，
 * 从而支持不同格式资源表的解析。
 *
 * **为什么保持 Java 可实现**：[init] 是可选默认方法，[getParser] 是唯一抽象方法，
 * Java 侧可用 lambda 实现（如 kadx-core 测试中的 mock provider）。
 */
interface IResTableParserProvider {

	/**
	 * 可选的初始化方法。
	 */
	fun init(root: RootNode) {
		// 默认空实现，插件可按需覆写
	}

	/**
	 * 检查文件格式，若匹配则返回对应解析器实例。
	 *
	 * @return 格式匹配时返回 [IResTableParser]，否则返回 `null`。
	 */
	fun getParser(resFile: ResourceFile): IResTableParser?
}
