package jadx.plugins.input.aab.parsers

import com.android.aapt.Resources.ConfigValue
import com.android.aapt.Resources.Entry
import com.android.aapt.Resources.Package
import com.android.aapt.Resources.ResourceTable
import com.android.aapt.Resources.Type
import com.android.aapt.Resources.Value
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.files.FileUtils
import jadx.core.xmlgen.BinaryXMLStrings
import jadx.core.xmlgen.IResTableParser
import jadx.core.xmlgen.ResContainer
import jadx.core.xmlgen.ResXmlGen
import jadx.core.xmlgen.ResourceStorage
import jadx.core.xmlgen.XmlGenUtils
import jadx.core.xmlgen.entry.ProtoValue
import jadx.core.xmlgen.entry.ResourceEntry
import jadx.core.xmlgen.entry.ValuesParser
import java.io.InputStream

/**
 * AAB 的 resources.pb（ResourceTable）解析器。
 *
 * **背景**：[decode] 把 protobuf ResourceTable 逐包/类型/条目展开成 [ResourceEntry]
 * （资源 id = packageId<<24 | typeId<<16 | entryId），存入 [resStorage]；
 * [decodeFiles] 再用 ResXmlGen 生成 XML 内容并打包为 [ResContainer]。
 */
public class ResTableProtoParser(private val root: RootNode) :
	CommonProtoParser(),
	IResTableParser {

	private var resStorage: ResourceStorage? = null
	private var baseFileName = ""

	override fun setBaseFileName(fileName: String) {
		baseFileName = fileName
	}

	override fun decode(inputStream: InputStream) {
		val storage = ResourceStorage(root.getArgs().security)
		resStorage = storage
		val table = ResourceTable.parseFrom(FileUtils.streamToByteArray(inputStream))
		for (p in table.packageList) {
			parse(p)
		}
		storage.finish()
	}

	@Synchronized
	override fun decodeFiles(): ResContainer {
		val vp = ValuesParser(BinaryXMLStrings(), resStorage!!.resourcesNames)
		val resGen = ResXmlGen(resStorage!!, vp, root.initManifestAttributes())
		val content = XmlGenUtils.makeXmlDump(root.makeCodeWriter(), resStorage!!)
		val xmlFiles = resGen.makeResourcesXml(root.getArgs())
		return ResContainer.resourceTable(baseFileName, xmlFiles, content)
	}

	private fun parse(p: Package) {
		val packageName = p.packageName
		resStorage!!.appPackage = packageName
		val types = p.typeList

		for (type in types) {
			val typeName = type.name
			for (entry in type.entryList) {
				val id = p.packageId.id shl 24 or type.typeId.id shl 16 or entry.entryId.id
				val entryName = entry.name
				for (configValue in entry.configValueList) {
					val config = parse(configValue.config)
					val resEntry = ResourceEntry(id, packageName, typeName, entryName, config)
					resStorage!!.add(resEntry)

					val protoValue: ProtoValue
					if (configValue.value.valueCase == Value.ValueCase.ITEM) {
						protoValue = ProtoValue(parse(configValue.value.item))
					} else {
						protoValue = parse(configValue.value.compoundValue)
					}
					resEntry.setProtoValue(protoValue)
				}
			}
		}
	}

	override fun getResStorage(): ResourceStorage? = resStorage

	override fun getStrings(): BinaryXMLStrings = BinaryXMLStrings()
}
