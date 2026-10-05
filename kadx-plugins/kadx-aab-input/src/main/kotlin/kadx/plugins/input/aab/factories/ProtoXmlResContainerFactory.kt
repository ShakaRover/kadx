package kadx.plugins.input.aab.factories

import kadx.api.ResourceFile
import kadx.api.ResourceType
import kadx.api.plugins.resources.IResContainerFactory
import kadx.core.dex.nodes.RootNode
import kadx.core.xmlgen.ResContainer
import kadx.plugins.input.aab.parsers.ResXmlProtoParser
import kadx.zip.IZipEntry
import java.io.InputStream

/**
 * 为 .aab 内的 XML/MANIFEST 资源创建 ResContainer（用 [ResXmlProtoParser] 渲染）。
 */
public class ProtoXmlResContainerFactory : IResContainerFactory {

	private var xmlParser: ResXmlProtoParser? = null

	override fun init(root: RootNode) {
		xmlParser = ResXmlProtoParser(root)
	}

	override fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer? {
		val type = resFile.getType()
		if (type != ResourceType.XML && type != ResourceType.MANIFEST) {
			return null
		}
		val zipEntry = resFile.getZipEntry() ?: return null
		val isFromAab = zipEntry.zipFile.path.lowercase().endsWith(".aab")
		if (!isFromAab) {
			return null
		}
		val parser = xmlParser ?: throw NullPointerException("xmlParser not initialized") // 原 Java：init 未调用时 NPE，保持一致
		val content = parser.parse(inputStream)
		return ResContainer.textResource(resFile.getDeobfName(), content)
	}
}
