package kadx.api.impl

import kadx.api.ICodeCache
import kadx.api.ICodeInfo
import java.io.IOException
import java.util.LinkedHashMap

/**
 * 有界内存代码缓存：按“最近访问”顺序淘汰，条目数不超过 [capacity]。
 *
 * **做什么**：与 [InMemoryCodeCache] 同样是纯内存缓存（进程退出即丢失），但**有容量上限**。
 * 命中会刷新访问顺序，超出容量时淘汰最久未使用的条目；被淘汰的类下次访问会重新反编译。
 *
 * **为什么需要**：无界的 [InMemoryCodeCache] 在全量代码搜索这类“把每个类都取一遍源码”的
 * 场景下会线性增长。实测（有界微信子集 8,279 个类）扫描后驻留 **+277 MB（约 33 KB/类）**，
 * 线性外推到 253k 类约 **8.5 GB**。改成有界后同一测量下驻留为常数级（约 [DEFAULT_CAPACITY] × 33 KB）。
 *
 * **代价（诚实说明）**：内存模式没有磁盘兜底，被淘汰意味着**下次要重新反编译**而不是回读磁盘。
 * 单类反编译实测约 1.4 ms，交互式浏览下代价可接受；全量扫描本来每个类只访问一次，不受影响。
 *
 * **线程模型**：`LinkedHashMap` 的访问序 LRU 在 `get` 时也会改动链表结构，故所有访问
 * 都用 `synchronized` 保护（与 `CodeStringCache` 的处理一致）。
 */
class BoundedMemoryCodeCache(
	private val capacity: Int = DEFAULT_CAPACITY,
) : ICodeCache {

	init {
		require(capacity > 0) { "Capacity must be positive, got: $capacity" }
	}

	/** LRU 存储：`accessOrder = true` + [LinkedHashMap.removeEldestEntry] 实现容量上限。 */
	private val cache: MutableMap<String, ICodeInfo> =
		object : LinkedHashMap<String, ICodeInfo>(capacity, 0.75f, true) {
			override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ICodeInfo>): Boolean = size > capacity
		}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		synchronized(cache) { cache[clsFullName] = codeInfo }
	}

	override fun remove(clsFullName: String) {
		synchronized(cache) { cache.remove(clsFullName) }
	}

	override fun get(clsFullName: String): ICodeInfo = synchronized(cache) { cache[clsFullName] } ?: ICodeInfo.EMPTY

	override fun getCode(clsFullName: String): String? = synchronized(cache) { cache[clsFullName] }?.codeStr

	override fun contains(clsFullName: String): Boolean = synchronized(cache) { cache.containsKey(clsFullName) }

	@Throws(IOException::class)
	override fun close() {
		synchronized(cache) { cache.clear() }
	}

	/** 当前缓存条目数（测试与诊断用）。 */
	val size: Int get() = synchronized(cache) { cache.size }

	override fun toString(): String = "BoundedMemoryCodeCache: size=$size, capacity=$capacity"

	companion object {
		/**
		 * 默认容量。单个类的 [ICodeInfo]（源码字符串 + 行号/注解元数据）实测约 33 KB，
		 * 512 条约 17 MB —— 足以覆盖交互式浏览的最近访问，又不会随全量扫描线性增长。
		 */
		const val DEFAULT_CAPACITY = 512
	}
}
