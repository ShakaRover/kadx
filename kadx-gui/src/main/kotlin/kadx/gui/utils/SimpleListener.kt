package kadx.gui.utils

/**
 * 极简的泛型事件监听器集合。
 *
 * **做什么**：保存一组 `(T) -> Unit` 回调，[sendUpdate] 时依次把数据分发给它们。
 *
 * @param T 事件数据类型
 */
class SimpleListener<T> {

	private val listeners: MutableList<(T) -> Unit> = ArrayList()

	/**
	 * 把数据分发给所有已注册的监听器。
	 *
	 * 注意：这里直接遍历，若回调过程中增删监听器可能抛 [ConcurrentModificationException]，
	 * 与原 Java 行为保持一致。
	 */
	fun sendUpdate(data: T) {
		for (listener in listeners) {
			listener(data)
		}
	}

	/** 注册一个监听器。 */
	fun addListener(listener: (T) -> Unit) {
		listeners.add(listener)
	}

	/**
	 * 移除监听器。
	 *
	 * @return 是否真的移除了（存在才会返回 true）
	 */
	fun removeListener(listener: (T) -> Unit): Boolean = listeners.remove(listener)
}
