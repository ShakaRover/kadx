package jadx.zip.fallback

import jadx.zip.IZipEntry
import jadx.zip.IZipParser
import jadx.zip.ZipContent
import jadx.zip.ZipReaderOptions
import jadx.zip.io.LimitedInputStream
import jadx.zip.security.IJadxZipSecurity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 基于 JDK 内置 [java.util.zip.ZipFile] 的回退解析器。
 *
 * 当 jadx 自定义解析器（[jadx.zip.parser.JadxZipParser]）出错或文件格式不支持时，
 * 用它作为后备方案打开 zip/apk 文件。条目读取委托给 [FallbackZipEntry]，
 * 安全校验与限流由 [IJadxZipSecurity] 策略控制。
 */
class FallbackZipParser(file: File, options: ZipReaderOptions) : IZipParser {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FallbackZipParser::class.java)
	}

	// 对应的 zip 文件（构造参数 file 在 init 块中赋给这个字段，注意 Kotlin 里构造参数会"遮蔽"同名字段）
	private lateinit var file: File

	private lateinit var zipFileHandle: ZipFile // JDK 的 ZipFile 句柄，close() 时释放

	private lateinit var zipSecurity: IJadxZipSecurity

	private var useLimitedDataStream = false

	init {
		try {
			this.file = file // this.file 是字段；裸 file 指构造参数（Kotlin 名称遮蔽规则）
			zipFileHandle = ZipFile(file) // new ZipFile(...) → Kotlin 直接构造调用，语义相同
			zipSecurity = options.zipSecurity // 从配置里取出安全策略
			useLimitedDataStream = zipSecurity.useLimitedDataStream() // 是否给条目流套上限额 InputStream
		} catch (e: Exception) {
			throw FallbackException("Error opening zip file: " + file.absolutePath, e)
		}
	}

	@Throws(IOException::class)
	override fun open(): ZipContent {
		try {
			var maxEntriesCount = zipSecurity.maxEntriesCount // -1 表示不限制条目数
			if (maxEntriesCount == -1) {
				maxEntriesCount = Int.MAX_VALUE // 用 int 最大值近似表达"无限"
			}
			// 原 Java 写的是 List<IZipEntry> list = new ArrayList<>()；Kotlin 里可变列表用 MutableList 声明，add() 才可用
			val list: MutableList<IZipEntry> = ArrayList()
			val entries = zipFileHandle.entries() // JDK ZipFile 的条目枚举
			while (entries.hasMoreElements()) {
				val zipEntry = FallbackZipEntry(this, entries.nextElement())
				if (isValidEntry(zipEntry)) {
					list.add(zipEntry)
					if (list.size > maxEntriesCount) {
						throw IllegalStateException("Max entries count limit exceeded: " + list.size)
					}
				}
			}
			return ZipContent(this, list) // 打包成内容容器（构造时自动建立名称索引）
		} catch (e: Exception) {
			throw FallbackException("Error opening zip file: " + file.absolutePath, e)
		}
	}

	private fun isValidEntry(zipEntry: IZipEntry): Boolean {
		val validEntry = zipSecurity.isValidEntry(zipEntry) // 委托给安全策略（含名称校验/zip bomb 检测）
		if (!validEntry) {
			LOG.warn("Zip entry '{}' is invalid and excluded from processing", zipEntry)
		}
		return validEntry
	}

	fun getBytes(entry: FallbackZipEntry): ByteArray {
		val inputStream = getEntryStream(entry)
		try {
			inputStream.readAllBytes() // 读到 EOF，天然消耗完整个流（readAllBytes() 后流已耗尽）
			if (inputStream.available() != 0) {
				throw RuntimeException("Failed to read all bytes for entry: " + entry.name)
			}
			val data = inputStream.readAllBytes()
			return data
		} catch (e: Exception) {
			throw RuntimeException("Failed to read bytes for entry: " + entry.name, e)
		} finally {
			// 对齐 Java try-with-resources 的自动 close；BufferedInputStream.close() 会传递给底层 ZipFile 条目流
			inputStream.close()
		}
	}

	fun getInputStream(entry: FallbackZipEntry): InputStream {
		try {
			return getEntryStream(entry)
		} catch (e: Exception) {
			throw RuntimeException("Failed to open input stream for entry: " + entry.name, e)
		}
	}

	private fun getEntryStream(entry: FallbackZipEntry): InputStream {
		val entryStream = zipFileHandle.getInputStream(entry.zipEntry) // 取 JDK ZipEntry 的原始数据流
		val stream: InputStream = if (useLimitedDataStream) {
			LimitedInputStream(entryStream, entry.uncompressedSize) // 套上限额流，防 zip bomb
		} else {
			entryStream
		}
		return BufferedInputStream(stream) // 统一加一层缓冲提升读取性能
	}

	val zipFile: File get() = file // 返回持有的 zip 文件（与原来 Java 同名同签名）

	@Throws(IOException::class)
	override fun close() {
		if (zipFileHandle != null) { // lateinit 字段构造后必然已赋值，此判空与原 Java 保持字面一致
			zipFileHandle.close()
		}
	}
}
