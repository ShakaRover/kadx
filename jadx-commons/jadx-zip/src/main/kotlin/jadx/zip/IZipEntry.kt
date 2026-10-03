package jadx.zip

import java.io.File
import java.io.InputStream

/**
 * Zip 文件中的单个条目（文件/目录）。
 */
interface IZipEntry {
	/** 条目名称（zip 内的相对路径） */
	val name: String

	/** 未压缩的数据字节 */
	val bytes: ByteArray

	/** 未压缩数据的输入流 */
	val inputStream: InputStream

	val compressedSize: Long

	val uncompressedSize: Long

	/** 是否为目录条目 */
	val isDirectory: Boolean

	/** 所属的 zip 文件 */
	val zipFile: File

	/**
	 * Return true if [bytes] property is more optimal to use other than
	 * [inputStream]（用字节数组比流更划算时返回 true）
	 */
	fun preferBytes(): Boolean
}
