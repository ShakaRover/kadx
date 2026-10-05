package kadx.plugins.input.aab.factories

import kadx.api.ResourceFile
import kadx.api.ResourceType
import kadx.api.plugins.resources.IResContainerFactory
import kadx.api.plugins.resources.IResTableParserProvider
import kadx.core.xmlgen.ResContainer
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
