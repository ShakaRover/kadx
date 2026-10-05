package kadx.gui.cache.code.disk

import kadx.api.ICodeCache
import kadx.api.ICodeInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 带有限内存缓冲的代码缓存。
 *
 * **做什么**：在磁盘缓存（[backCache]）前面加一个容量固定的 LRU 式内存缓冲，
 * 最多保留 [BUFFER_SIZE] 个最近访问的 [ICodeInfo]，减少磁盘读取次数。
 *
 * **算法**：用 [ConcurrentHashMap] 做键值存储，用 [ConcurrentLinkedDeque] 记录插入顺序；
 * 每次写入把类名追加到队尾，超过容量时从队首淘汰对应的键。读取命中时同样刷新缓冲。
 *
 * **注意**：[get] 中用 `!==` 做引用比较判断是否为空代码信息（与原 Java `!=` 语义一致）。
 */
class BufferCodeCache(private val backCache: ICodeCache) : ICodeCache {

	private val cache: MutableMap<String, ICodeInfo> = ConcurrentHashMap()
	private val buffer: ConcurrentLinkedDeque<String> = ConcurrentLinkedDeque()

	/** 写入内存缓冲，并在超出容量时淘汰最旧的条目。 */
	private fun addInternal(clsFullName: String, codeInfo: ICodeInfo) {
		cache[clsFullName] = codeInfo
		buffer.addLast(clsFullName)
		if (buffer.size > BUFFER_SIZE) {
			val removedKey = buffer.removeFirst()
			cache.remove(removedKey)
		}
	}

	override fun contains(clsFullName: String): Boolean {
		if (cache.containsKey(clsFullName)) {
			return true
		}
		return backCache.contains(clsFullName)
	}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		addInternal(clsFullName, codeInfo)
		backCache.add(clsFullName, codeInfo)
	}

	override fun get(clsFullName: String): ICodeInfo {
		val codeInfo = cache[clsFullName]
		if (codeInfo != null) {
			return codeInfo
		}
		val backCodeInfo = backCache.get(clsFullName)
		if (backCodeInfo !== ICodeInfo.EMPTY) {
			addInternal(clsFullName, backCodeInfo)
		}
		return backCodeInfo
	}

	override fun getCode(clsFullName: String): String? {
		val codeInfo = cache[clsFullName]
		if (codeInfo != null) {
			return codeInfo.codeStr
		}
		return backCache.getCode(clsFullName)
	}

	override fun remove(clsFullName: String) {
		cache.remove(clsFullName)
		backCache.remove(clsFullName)
	}

	@Throws(IOException::class)
	override fun close() {
		cache.clear()
		buffer.clear()
		backCache.close()
	}

	companion object {
		private const val BUFFER_SIZE = 20
	}
}
