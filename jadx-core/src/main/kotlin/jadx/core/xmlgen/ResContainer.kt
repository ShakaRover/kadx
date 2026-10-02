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
 * - 静态工厂（companion + `@JvmStatic`，Java 以 `ResContainer.textResource(...)` 调用）；
 * - 基于名称的 [equals]/[hashCode] 与 [compareTo]。
 */
class ResContainer private constructor(
	private val name: String,
	private val subFiles: List<ResContainer>,
	private val data: Any,
	private val dataType: DataType,
) : Comparable<ResContainer> {

	enum class DataType {
		TEXT,
		DECODED_DATA,
		RES_LINK,
		RES_TABLE,
	}

	fun getName(): String = name

	fun getFileName(): String = name.replace('/', File.separatorChar)

	fun getSubFiles(): List<ResContainer> = subFiles

	fun getDataType(): DataType = dataType

	fun getText(): ICodeInfo = data as ICodeInfo

	fun getDecodedData(): ByteArray = data as ByteArray

	fun getResLink(): ResourceFile = data as ResourceFile

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
		@JvmStatic
		fun textResource(name: String, content: ICodeInfo): ResContainer = ResContainer(name, emptyList(), content, DataType.TEXT)

		@JvmStatic
		fun decodedData(name: String, data: ByteArray): ResContainer = ResContainer(name, emptyList(), data, DataType.DECODED_DATA)

		@JvmStatic
		fun resourceFileLink(resFile: ResourceFile): ResContainer = ResContainer(resFile.getDeobfName(), emptyList(), resFile, DataType.RES_LINK)

		@JvmStatic
		fun resourceTable(name: String, subFiles: List<ResContainer>, rootContent: ICodeInfo): ResContainer = ResContainer(name, subFiles, rootContent, DataType.RES_TABLE)
	}
}
