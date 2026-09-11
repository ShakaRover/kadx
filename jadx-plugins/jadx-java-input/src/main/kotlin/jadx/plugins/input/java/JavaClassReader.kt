package jadx.plugins.input.java

import jadx.api.plugins.input.data.IClassData
import jadx.plugins.input.java.data.JavaClassData

/**
 * 单个 .class 文件的读取器句柄（id + 来源文件名 + 原始字节）。
 *
 **做什么**：[loadClassData] 按需创建重量级的 [JavaClassData]（偏移索引、常量池等）；
 * 本身只持有轻量元数据，供加载结果列表与调试日志使用。
 */
class JavaClassReader(
	private val id: Int,
	private val fileName: String,
	private val data: ByteArray,
) {

	fun loadClassData(): IClassData = JavaClassData(this)

	fun getId(): Int = id

	fun getFileName(): String = fileName

	fun getData(): ByteArray = data

	override fun toString(): String = fileName
}
