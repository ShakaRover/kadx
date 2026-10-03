package jadx.plugins.input.aab.factories

import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.plugins.resources.IResContainerFactory
import jadx.core.dex.nodes.RootNode
import jadx.core.xmlgen.ResContainer
import jadx.plugins.input.aab.parsers.ResXmlProtoParser
import jadx.zip.IZipEntry
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
