package jadx.gui.utils

import java.util.function.Consumer

/**
 * 极简的泛型事件监听器集合。
 *
 * **做什么**：保存一组 [Consumer] 回调，[sendUpdate] 时依次把数据分发给它们。
 *
 * **为什么保持普通类**：Java 调用方用 `new SimpleListener<>()` 构造并传入 lambda，
 * 因此保留类 + 公开构造器不变。
 *
 * @param T 事件数据类型
 */
class SimpleListener<T> {

	private val listeners: MutableList<Consumer<T>> = ArrayList()

	/**
	 * 把数据分发给所有已注册的监听器。
	 *
	 * 注意：这里直接遍历，若回调过程中增删监听器可能抛 [ConcurrentModificationException]，
	 * 与原 Java 行为保持一致。
	 */
	fun sendUpdate(data: T) {
		for (listener in listeners) {
			listener.accept(data)
		}
	}

	/** 注册一个监听器。 */
	fun addListener(listener: Consumer<T>) {
		listeners.add(listener)
	}

	/**
	 * 移除监听器。
	 *
	 * @return 是否真的移除了（存在才会返回 true）
	 */
	fun removeListener(listener: Consumer<T>): Boolean = listeners.remove(listener)
}
