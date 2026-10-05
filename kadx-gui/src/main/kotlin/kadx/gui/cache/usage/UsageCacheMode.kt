package kadx.gui.cache.usage

/**
 * usage 缓存的存储模式。
 *
 * **做什么**：控制 usage 分析结果如何缓存：
 * - [NONE]：不缓存，每次重新分析；
 * - [MEMORY]：仅缓存在内存中（同一次会话内复用）；
 * - [DISK]：缓存到磁盘，下次打开同一工程可直接加载。
 */
enum class UsageCacheMode {
	NONE,
	MEMORY,
	DISK,
}
