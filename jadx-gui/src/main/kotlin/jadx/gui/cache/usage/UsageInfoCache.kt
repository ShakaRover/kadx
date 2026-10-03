package jadx.gui.cache.usage

import jadx.api.usage.IUsageInfoCache
import jadx.api.usage.IUsageInfoData
import jadx.api.usage.impl.InMemoryUsageInfoCache
import jadx.core.dex.nodes.RootNode
import java.io.File
import java.nio.file.Path

/**
 * usage 信息的磁盘 + 内存两级缓存。
 *
 * **做什么**：
 * - [get]：先查内存缓存；未命中则在锁内尝试从磁盘文件加载一次，成功后转成
 *   [UsageData] 并写入内存缓存；
 * - [set]：写入内存缓存并持久化到磁盘；
 * - [close]：清空内存缓存与已加载的原始数据。
 *
 * **线程模型（保持原 Swing 模型）**：使用静态锁对象 [LOAD_DATA_SYNC] 串行化磁盘加载，
 * 避免多个后台线程重复读取同一文件。本类不引入协程。
 */
class UsageInfoCache(cacheDir: Path, inputFiles: List<File>) : IUsageInfoCache {

	/** usage 缓存文件路径（固定为 cacheDir/usage）。 */
	private val usageFile: Path = cacheDir.resolve("usage")

	/** 参与哈希的输入文件列表。 */
	private val inputs: List<File> = inputFiles

	/** 内存缓存（与 root 节点实例绑定）。 */
	private val memCache = InMemoryUsageInfoCache()

	/** 已从磁盘加载的原始 usage 数据；未加载时为 `null`。 */
	private var rawUsageData: RawUsageData? = null

	override fun get(root: RootNode): IUsageInfoData? {
		val memData = memCache.get(root)
		if (memData != null) {
			return memData
		}
		synchronized(LOAD_DATA_SYNC) {
			var raw = rawUsageData
			if (raw == null) {
				raw = UsageFileAdapter.load(root, usageFile, inputs)
				rawUsageData = raw
			}
			if (raw != null) {
				val data = UsageData(root, raw)
				memCache.set(root, data)
				return data
			}
		}
		return null
	}

	override fun set(root: RootNode, data: IUsageInfoData) {
		memCache.set(root, data)
		UsageFileAdapter.save(data, usageFile, inputs)
	}

	override fun close() {
		rawUsageData = null
		memCache.close()
	}

	companion object {
		/** 磁盘加载互斥锁（等价于原 Java 的 `static final Object`）。 */
		private val LOAD_DATA_SYNC: Any = Any()
	}
}
