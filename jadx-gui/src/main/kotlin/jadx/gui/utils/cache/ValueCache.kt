package jadx.gui.utils.cache

/**
 * 基于“键”对象的简单值缓存。
 *
 * **做什么**：缓存与某个 key 关联的值；当传入的 key 与上次相同（`equals`）时直接返回缓存值，
 * 否则调用 [loadFunc] 重新加载并替换缓存。
 *
 * **为什么不是 `data class`**：它是带可变状态的缓存容器，不需要结构化相等。
 *
 * **线程模型**：与原 Java 一致，[get] 使用 `synchronized` 保证同一时刻只有一个线程加载。
 *
 * @param K 键对象类型
 * @param V 缓存对象类型
 */
class ValueCache<K, V> {
	private var key: K? = null
	private var value: V? = null

	/**
	 * 若 key 未变化则返回已存值，否则用 [loadFunc] 重新加载。
	 *
	 * 注意：key 命中时缓存值必须存在（原 Java 直接返回 `value`，这里用 [checkNotNull] 保持非空返回）。
	 */
	@Synchronized
	fun get(requestKey: K, loadFunc: (K) -> V): V {
		if (key != null && key == requestKey) {
			return checkNotNull(value)
		}
		val newValue = loadFunc(requestKey)
		key = requestKey
		value = newValue
		return newValue
	}
}
