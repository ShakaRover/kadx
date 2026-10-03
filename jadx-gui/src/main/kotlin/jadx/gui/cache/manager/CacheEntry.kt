package jadx.gui.cache.manager

/**
 * 缓存列表中的一条记录：项目路径 / 临时项目键、对应缓存目录、最后使用时间戳。
 *
 * **做什么**：作为 `caches.json` 的持久化单元，由 Gson 序列化/反序列化；
 * [compareTo] 按时间戳倒序排列，使最近使用的项目排在最前。
 *
 * **为什么保留显式 getter/setter 与私有字段**：
 * - 字段名 `project` / `cache` / `timestamp` 就是 JSON 的键，不能改动；
 * - `TableRow`、`CachesTable` 等 Kotlin 调用方仍按 `getProject()` / `getCache()` 访问；
 * - 不使用 `data class`：该类需要可变状态与自定义排序语义，且要保持 Gson 反射字段形态。
 */
class CacheEntry : Comparable<CacheEntry> {

	private var project: String = ""
	private var cache: String = ""
	private var timestamp: Long = 0

	fun getProject(): String = project

	fun setProject(project: String) {
		this.project = project
	}

	fun getCache(): String = cache

	fun setCache(cache: String) {
		this.cache = cache
	}

	fun getTimestamp(): Long = timestamp

	fun setTimestamp(timestamp: Long) {
		this.timestamp = timestamp
	}

	/** 最近的记录排在最前（时间戳倒序）。 */
	override fun compareTo(other: CacheEntry): Int = -timestamp.compareTo(other.timestamp)

	override fun toString(): String = "CacheEntry{project=$project, cache=$cache}"
}
