@file:Suppress("ktlint:standard:property-naming")

package jadx.zip.parser

import jadx.zip.IZipEntry
import java.io.File
import java.io.InputStream

/**
 * 主解析器使用的自定义 zip 条目实现。
 *
 * 注意：原 Java 版里这些字段（如 compressMethod、entryStart）都在构造器里一次性赋值后只读，
 * Kotlin 版用「同名普通构造器参数 + 类体属性」承载：构造器参数只在构造期可见，成员函数读取需要属性。
 * 接口 [IZipEntry] 的访问器已改为 Kotlin 属性，JVM 上仍生成 `getX()` 方法，因此对 Java 调用方保持兼容。
 */
class JadxZipEntry(
	// 创建该条目的解析器，数据存取都委托给它（private 不生成 getter）
	private val parser: JadxZipParser,
	// zip 包内条目名（含目录层级，如 com/example/Foo.class）
	private val fileName: String,
	// 本条目的 local file header L_FH 起始偏移量
	val entryStart: Int,
	// 压缩数据区起始偏移量（L_FH 末尾 + 文件名长度）
	val dataStart: Int,
	// 压缩方式：0=STORED、8=DEFLATED
	val compressMethod: Int,
	// 压缩后大小（字节）
	compressedSize: Long,
	// 解压后原始大小（字节）
	uncompressedSize: Long,
) : IZipEntry {

	override val compressedSize: Long = compressedSize
	override val uncompressedSize: Long = uncompressedSize

	val isSizesValid: Boolean
		get() {
			if (compressedSize <= 0) {
				return false
			}
			if (uncompressedSize <= 0) {
				return false
			}
			return compressedSize <= uncompressedSize // 压缩后体积不应大于原始体积（含相等）
		}

	override val name: String get() = fileName // 返回 zip 包内条目名

	override val isDirectory: Boolean get() = fileName.endsWith("/") // zip 目录条目名以 / 结尾

	override fun preferBytes(): Boolean = true

	override val bytes: ByteArray get() = parser.getBytes(this) // 数据读取委托给 JadxZipParser（带 deflate 解压）

	override val inputStream: InputStream get() = parser.getInputStream(this) // 流式读取同样委托给解析器

	override val zipFile: File get() = parser.zipFile // 委托解析器返回源 zip 文件对象

	override fun toString(): String {
		return parser.zipFile.getName() + ':' + fileName // 与原 Java 版一致的 "包名:条目名" 格式
	}
}
