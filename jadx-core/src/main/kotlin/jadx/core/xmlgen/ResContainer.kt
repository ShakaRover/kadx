package jadx.core.xmlgen

import jadx.api.ICodeInfo
import jadx.api.ResourceFile
import java.io.File

/**
 * 资源容器：表示一个可保存的资源（文本 / 解码数据 / 文件链接 / 资源表）。
 *
 * 这是对外的公共 API，被 `ResourcesSaver`、cli、gui 以及 AAB 插件使用。
 * 保留：
 * - 嵌套枚举 [DataType]（Java 以 `ResContainer.DataType.XXX` 访问）；
 * - 静态工厂（companion，以 `ResContainer.textResource(...)` 调用）；
 * - 基于名称的 [equals]/[hashCode] 与 [compareTo]。
 */
class ResContainer private constructor(
	val name: String,
	val subFiles: List<ResContainer>,
	private val data: Any,
	val dataType: DataType,
) : Comparable<ResContainer> {

	enum class DataType {
		TEXT,
		DECODED_DATA,
		RES_LINK,
		RES_TABLE,
	}

	val fileName: String get() = name.replace('/', File.separatorChar)

	val text: ICodeInfo get() = data as ICodeInfo

	val decodedData: ByteArray get() = data as ByteArray

	val resLink: ResourceFile get() = data as ResourceFile

	override fun compareTo(other: ResContainer): Int = name.compareTo(other.name)

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is ResContainer) {
			return false
		}
		return name == other.name
	}

	override fun hashCode(): Int = name.hashCode()

	override fun toString(): String = "Res{" + name + ", type=" + dataType + ", subFiles=" + subFiles + '}'

	companion object {
		fun textResource(name: String, content: ICodeInfo): ResContainer = ResContainer(name, emptyList(), content, DataType.TEXT)

		fun decodedData(name: String, data: ByteArray): ResContainer = ResContainer(name, emptyList(), data, DataType.DECODED_DATA)

		fun resourceFileLink(resFile: ResourceFile): ResContainer = ResContainer(resFile.getDeobfName(), emptyList(), resFile, DataType.RES_LINK)

		fun resourceTable(name: String, subFiles: List<ResContainer>, rootContent: ICodeInfo): ResContainer = ResContainer(name, subFiles, rootContent, DataType.RES_TABLE)
	}
}
