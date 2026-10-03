package jadx.zip

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.HashMap

/**
 * 解压后的 zip 内容容器：条目列表 + "名称 -> 条目"的索引。
 *
 * 持有创建它的 [zipParser]，close() 时一并释放解析器资源（实现 Closeable）。
 */
class ZipContent(private val zipParser: IZipParser, val entries: List<IZipEntry>) : Closeable {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ZipContent::class.java)

		fun buildNameMap(zipParser: IZipParser, entries: List<IZipEntry>): Map<String, IZipEntry> {
			val map = HashMap<String, IZipEntry>(entries.size) // 容量按条目数预估，减少扩容次数
			for (entry in entries) {
				val name = entry.name
				val prevEntry: IZipEntry? = map.put(name, entry)
				if (prevEntry != null) {
					LOG.warn("Found duplicate entry: {} in {}", name, zipParser) // 同名条目后者覆盖前者，记一条告警
				}
			}
			return map
		}
	}

	// 构造时一次性建立索引（对应原 Java 构造函数体内对 entriesMap 的赋值）
	private lateinit var entriesMap: Map<String, IZipEntry>

	init {
		entriesMap = buildNameMap(zipParser, entries)
	}

	fun searchEntry(fileName: String): IZipEntry? = entriesMap[fileName] // 找不到返回 null（对应原 @Nullable）

	override fun close() {
		zipParser.close()
	}
}
