package kadx.gui.logs

import java.util.AbstractQueue
import java.util.ArrayDeque
import java.util.Deque

/**
 * 有容量上限的队列：超过 [limit] 时自动丢弃最旧的元素。
 *
 * **做什么**：日志缓冲区只需要保留最近 [limit] 条记录，因此用 [ArrayDeque] 作为底层存储，
 * 在 [offer] 后若超出上限就移除队首。
 *
 * **为什么继承 [AbstractQueue]**：保持与原 Java 完全一致的 `Queue` 语义与接口形态。
 */
class LimitedQueue<T>(private val limit: Int) : AbstractQueue<T>() {

	/** 底层双端队列，队尾入队、队首出队。 */
	private val deque: Deque<T> = ArrayDeque()

	override fun iterator(): MutableIterator<T> = deque.iterator()

	override val size: Int
		get() = deque.size

	/** 入队；若超出容量则丢弃最旧元素。始终返回 `true`。 */
	override fun offer(t: T): Boolean {
		deque.addLast(t)
		if (deque.size > limit) {
			deque.removeFirst()
		}
		return true
	}

	override fun poll(): T? = deque.poll()

	override fun peek(): T? = deque.peek()

	override fun clear() {
		deque.clear()
	}
}
