package jadx.api.plugins.input.data

import java.util.function.Consumer

/**
 * 序列消费者接口。
 *
 * 扩展了 Java 的 [Consumer] 接口，增加了序列长度预通知功能。
 *
 * **使用场景**：当需要遍历一个元素序列时，调用方可以先告知接收方即将处理的元素数量，
 * 让接收方有机会预先分配缓冲区或初始化状态，提高性能。
 *
 * **工作流程**：
 * 1. 调用 `init(count)` 通知总元素数（可选，默认空实现）
 * 2. 对每个元素调用 `accept(element)` 处理
 *
 * @param T 序列元素的类型
 *
 * **示例**：
 * ```kotlin
 * val consumer = object : ISeqConsumer<String> {
 *     override fun init(count: Int) {
 *         buffer = ArrayList(count)  // 预分配容量
 *     }
 *     override fun accept(t: String) {
 *         buffer.add(t)
 *     }
 * }
 * ```
 */
public interface ISeqConsumer<T> : Consumer<T> {
	/**
	 * 初始化方法，在开始处理序列前被调用。
	 *
	 * @param count 即将处理的元素总数
	 *
	 * **默认行为**：空操作（no-op），实现类可以重写此方法来预分配资源。
	 */
	public fun init(count: Int): Unit = Unit
}
