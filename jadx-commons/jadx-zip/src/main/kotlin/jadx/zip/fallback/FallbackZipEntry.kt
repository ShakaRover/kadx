package jadx.zip.fallback

import jadx.zip.IZipEntry
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry

/**
 * 基于 JDK 内置 ZipFile 的 zip 条目实现（回退解析器使用）。
 *
 * 包装一个 [java.util.zip.ZipEntry]，把名称/大小等元数据直接委托给它；
 * 内容读取（字节数组与输入流）委托给所属的 [FallbackZipParser]。
 */
class FallbackZipEntry(private val parser: FallbackZipParser, val zipEntry: ZipEntry) : IZipEntry {

	override fun getName(): String = zipEntry.getName() // 对应 JDK ZipEntry 的同名方法

	override fun preferBytes(): Boolean = false // 回退实现没有预缓存字节，用流读取更划算

	override fun getBytes(): ByteArray = parser.getBytes(this)

	override fun getInputStream(): InputStream = parser.getInputStream(this)

	override fun getCompressedSize(): Long = zipEntry.getCompressedSize()

	override fun getUncompressedSize(): Long = zipEntry.getSize() // 解压后大小对应 JDK API 的 getSize()

	override fun isDirectory(): Boolean = zipEntry.isDirectory()

	override fun getZipFile(): File = parser.zipFile // 返回解析器持有的 zip 文件
}
