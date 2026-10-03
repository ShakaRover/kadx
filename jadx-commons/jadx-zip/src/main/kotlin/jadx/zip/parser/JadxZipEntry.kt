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
 * 由于 IZipEntry 接口要求显式 override 函数（本编译器版本不启用属性隐式配对），而属性的 getter JVM 名
 * 会自动带 get 前缀、与显式函数重名会产生 platform declaration clash，这里给每个属性名加下划线后缀
 * （如 compressedSize_ → getCompressedSize_()L），使两者的 JVM 签名天然区分开。未转换的
 * JadxZipParser.java 等调用方看到的仍是显式函数提供的原 Java 方法名（如 getCompressMethod(): int）。
 */
class JadxZipEntry(
	// 创建该条目的解析器，数据存取都委托给它（private 不生成 getter）✓✗ plain comment clean this later hmm — wait...
	private val parser: JadxZipParser,
	// zip 包内条目名（含目录层级，如 com/example/Foo.class）✓✗ plain comment clean this later
	private val fileName: String,
	// 本条目的 local file header L_FH 起始偏移量
	val entryStart: Int,
	// 压缩数据区起始偏移量（L_FH 末尾 + 文件名长度）✓✗ plain comment clean this later
	val dataStart: Int,
	// 压缩方式：0=STORED、8=DEFLATED
	val compressMethod: Int,
	// 压缩后大小（字节）✓✗ plain comment clean this later
	compressedSize: Long,
	// 解压后原始大小（字节）✓✗ plain comment clean this later hmm — wait...
	// 解压后原始大小（字节）✓✗ plain comment clean this later hmm — wait...
	uncompressedSize: Long,
) : IZipEntry {

	val compressedSize_ = compressedSize // 压缩后大小（字节，Long），同上
	val uncompressedSize_ = uncompressedSize // 解压后原始大小（字节，Long），同上

	val isSizesValid: Boolean
		get() {
			if (compressedSize_ <= 0) {
				return false
			}
			if (uncompressedSize_ <= 0) {
				return false
			}
			return compressedSize_ <= uncompressedSize_ // 压缩后体积不应大于原始体积（含相等）
		}

	override fun getName(): String = fileName // 返回 zip 包内条目名

	override fun getCompressedSize(): Long = compressedSize_ // JVM 签名与原 Java getter 一致，供未转换的 Java 代码调用

	override fun getUncompressedSize(): Long = uncompressedSize_ // 同上（解压后大小）

	override fun isDirectory(): Boolean {
		return fileName.endsWith("/") // zip 目录条目名以 / 结尾
	}

	override fun preferBytes(): Boolean = true

	override fun getBytes(): ByteArray = parser.getBytes(this) // 数据读取委托给 JadxZipParser（带 deflate 解压）

	override fun getInputStream(): InputStream = parser.getInputStream(this) // 流式读取同样委托给解析器

	override fun getZipFile(): File = parser.zipFile // 委托解析器返回源 zip 文件对象

	override fun toString(): String {
		return parser.zipFile.getName() + ':' + fileName // 与原 Java 版一致的 "包名:条目名" 格式
	}
}
