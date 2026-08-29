package jadx.zip

import java.io.File
import java.io.InputStream

/**
 * Zip 文件中的单个条目（文件/目录）。
 */
interface IZipEntry {
	/** 条目名称（zip 内的相对路径） */
	fun getName(): String

	/** 未压缩的数据字节 */
	fun getBytes(): ByteArray

	/** 未压缩数据的输入流 */
	fun getInputStream(): InputStream

	fun getCompressedSize(): Long

	fun getUncompressedSize(): Long

	/** 是否为目录条目 */
	fun isDirectory(): Boolean

	/** 所属的 zip 文件 */
	fun getZipFile(): File

	/**
	 * Return true if {@link #getBytes()} method is more optimal to use other than
	 * {@link #getInputStream()}（用字节数组比流更划算时返回 true）
	 */
	fun preferBytes(): Boolean
}
