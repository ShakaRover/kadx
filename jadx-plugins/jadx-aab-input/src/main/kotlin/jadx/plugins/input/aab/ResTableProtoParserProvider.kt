package jadx.plugins.input.aab

import jadx.api.ResourceFile
import jadx.api.plugins.resources.IResTableParserProvider
import jadx.core.dex.nodes.RootNode
import jadx.core.xmlgen.IResTableParser
import jadx.plugins.input.aab.parsers.ResTableProtoParser

/**
 * 为 resources.pb 文件提供 [ResTableProtoParser]。
 *
 **背景**：实现 IResTableParserProvider——init() 保存 RootNode，getParser() 只对
 * 文件名以 resources.pb 结尾的资源返回解析器，其余返回 null。
 */
public class ResTableProtoParserProvider : IResTableParserProvider {

	private var root: RootNode? = null

	override fun init(root: RootNode) {
		this.root = root
	}

	override fun getParser(resFile: ResourceFile): IResTableParser? {
		val fileName = resFile.getOriginalName()
		if (!fileName.endsWith("resources.pb")) {
			return null
		}
		return ResTableProtoParser(root ?: throw NullPointerException("root not initialized")) // 原 Java：init 未调用时 NPE，保持一致
	}
}
