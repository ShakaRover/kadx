package jadx.plugins.input.aab.factories

import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.plugins.resources.IResContainerFactory
import jadx.api.plugins.resources.IResTableParserProvider
import jadx.core.xmlgen.ResContainer
import java.io.InputStream

/**
 * 为 .pb（ARSC）资源创建 ResContainer：委托给 [IResTableParserProvider] 的解析器。
 */
public class ProtoTableResContainerFactory(private val provider: IResTableParserProvider) : IResContainerFactory {

	override fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer? {
		if (!resFile.getOriginalName().endsWith(".pb") || resFile.getType() != ResourceType.ARSC) {
			return null
		}
		val parser = provider.getParser(resFile) ?: return null
		parser.decode(inputStream)
		return parser.decodeFiles()
	}
}
