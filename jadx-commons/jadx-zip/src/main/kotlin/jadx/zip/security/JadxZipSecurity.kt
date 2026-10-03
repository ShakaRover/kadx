package jadx.zip.security

import jadx.zip.IZipEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * zip 解析时的安全/压缩比（zip bomb）校验策略，实现 [IJadxZipSecurity]。
 *
 * 职责：限制条目数量、检查单个条目的解压/压缩比与绝对大小、
 * 校验条目名是否落在输出目录下（防止路径穿越写出文件）。
 */
class JadxZipSecurity : IJadxZipSecurity {

	// companion object，等价于原 Java 的 static 成员
	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxZipSecurity::class.java) // slf4j 日志器（对应 private static final 字段）

		/** 当前工作目录的规范化绝对路径，用于条目名相对路径解析。 */
		val CWD: Path = Paths.get(".").toAbsolutePath().normalize()

		private fun isInSubDirectoryInternal(baseDir: File, file: File): Boolean { // 从给定文件逐级向上找父目录，直到与 baseDir 相等或到根（null）
			var current: File? = file
			while (true) { // Java 里的 while(true)，Kotlin 中保持同构写法便于逐行对照
				if (current == null) {
					return false
				}
				if (current == baseDir) {
					return true
				}
				current = current.getParentFile() // 逐级上溯父目录（对应原 Java 的 getParentFile()）
			}
		}
	}

	var zipBombDetectionFactor: Int = 100 // 解压/压缩比上限因子（默认：解压后 ≤ 压缩前 × 100）；var 自动提供 setZipBombDetectionFactor(int)

	/** 小于该大小时的条目直接视为安全，跳过比例检查（单位字节，默认 25MB）。 */
	var zipBombMinUncompressedSize: Int = 25 * 1024 * 1024

	// 原 Java 版：字段 private int maxEntriesCount + public get/setMaxEntriesCount 访问器。这里属性设为 private，不生成 JVM 访问器方法
	private var maxEntriesCountValue = 100_000 // zip 包内最大条目数（默认十万），防止超大/恶意包拖慢解析；Int 类型由初始化值推断

	override fun isValidEntry(entry: IZipEntry): Boolean {
		return isValidEntryName(entry.name) && !isZipBomb(entry) // 名称合法且不是 zip bomb，条目才算有效
	}

	override fun useLimitedDataStream(): Boolean = useLimitedDataStream

	override val maxEntriesCount: Int
		get() {
			return maxEntriesCountValue // 显式实现 IJadxZipSecurity.maxEntriesCount（原 Java 同样有独立 public 方法）
		}

	// Explicit public accessor matching the original Java version: JVM signature setUseLimited... no wait getMaxEntriesCount(int)
	fun setMaxEntriesCount(maxEntriesCount: Int) {
		this.maxEntriesCountValue = maxEntriesCount
	}

	override fun isValidEntryName(entryName: String): Boolean {
		if (entryName.contains("..")) { // quick pre-check：先做廉价的字符串检查
			if (entryName.contains("../") || entryName.contains("..\\")) {
				LOG.error("Path traversal attack detected in entry: '{}'", entryName)
				return false
			}
		}
		// Path traversal check as presented on
		// https://www.heise.de/en/background/Secure-Coding-Best-practices-for-using-Java-NIO-against-path-traversal-9996787.html
		try {
			val entryPathPart = Paths.get(entryName).normalize() // 把条目名解析成 Path 并规范化（消除 .. 等）
			if (entryPathPart.startsWith(CWD) || entryPathPart.isAbsolute()) { // 若已是 CWD 的完整路径或绝对路径，直接拒绝（否则下面检查恒过）
				LOG.error("Path traversal attack detected (absolute path) in entry: {}", entryName)
				return false
			}
			val entryPath = CWD.resolve(entryPathPart).normalize() // 拼到 CWD 下再规范化后判断是否仍在 CWD 内
			if (entryPath.startsWith(CWD)) {
				return true
			}
		} catch (e: Exception) { // check failed
			LOG.error("Invalid file name or path traversal attack detected: {} - error: {}", entryName, e.message) // Kotlin 中 message 属性与 Java 的 getMessage() 同义
			return false
		}
		LOG.error("Invalid file name or path traversal attack detected: {}", entryName) // try 块内未返回 true，落到这里判非法
		return false
	}

	override fun isInSubDirectory(baseDir: File, file: File): Boolean {
		try {
			return isInSubDirectoryInternal(baseDir.getCanonicalFile(), file.getCanonicalFile()) // 用规范化（canonical）路径做父目录上溯比较
		} catch (e: IOException) { // getCanonicalFile() 可能抛 IOException（如文件不存在），视为不在子目录内
			return false
		}
	}

	fun isZipBomb(entry: IZipEntry): Boolean { // 公开方法：JadxZipParser.java 等外部代码直接调用它判断单个条目
		val compressedSize = entry.compressedSize
		val uncompressedSize = entry.uncompressedSize
		val invalidSize = compressedSize < 0 || uncompressedSize < 0 // 负数尺寸视为非法
		val possibleZipBomb = uncompressedSize >= zipBombMinUncompressedSize && // 只有解压后足够大的条目才做比例检查
			compressedSize * zipBombDetectionFactor < uncompressedSize // 压缩比超过 factor 倍 → 疑似 zip bomb
		if (invalidSize || possibleZipBomb) {
			LOG.error(
				"Potential zip bomb attack detected, invalid sizes: compressed {}, uncompressed {}, name {}",
				compressedSize,
				uncompressedSize,
				entry.name,
			)
			return true // true = “是 zip bomb/尺寸非法”，调用方按 isValidEntry 中 !isZipBomb(...) 使用
		}
		return false
	}

	var useLimitedDataStream: Boolean = true // 是否给条目数据流套 LimitedInputStream（限流）；var 自动生成 getter/setter，与原 Java 字段等价
}
