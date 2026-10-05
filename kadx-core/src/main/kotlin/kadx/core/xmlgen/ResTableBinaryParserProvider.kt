package kadx.core.xmlgen

import kadx.api.ResourceFile
import kadx.api.plugins.resources.IResTableParserProvider
import kadx.core.dex.nodes.RootNode

/**
 * 默认的二进制资源表解析器提供者：只对 `.arsc` 文件返回 [ResTableBinaryParser]。
 *
 * 插件机制通过 [IResTableParserProvider] 注册，[init] 时保存 [RootNode]，
 * 后续 [getParser] 用它构造解析器。
 */
class ResTableBinaryParserProvider : IResTableParserProvider {
	private var root: RootNode? = null

	override fun init(root: RootNode) {
		this.root = root
	}

	override fun getParser(resFile: ResourceFile): IResTableParser? {
		val fileName = resFile.getOriginalName()
		if (!fileName.endsWith(".arsc")) {
			return null
		}
		return ResTableBinaryParser(checkNotNull(root))
	}
}
