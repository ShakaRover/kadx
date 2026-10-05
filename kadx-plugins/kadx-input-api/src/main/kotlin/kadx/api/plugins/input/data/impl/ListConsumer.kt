package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.ISeqConsumer
import java.util.Collections
import java.util.function.Function

/**
 * 把序列消费结果收集到列表的消费者。
 *
 * **背景**：[ISeqConsumer] 是"遍历字段/方法"的回调接口，本实现把每次
 * accept 的元素经 [convert] 转换后累积到内部列表，最后用 [getResult] 取出。
 *
 * @param T 输入元素类型（如 IFieldData）
 * @param R 转换后的输出类型（如 FieldNode）
 */
public class ListConsumer<T, R>(private val convert: Function<T, R>) : ISeqConsumer<T> {

	private var list: List<R> = ArrayList()

	/**
	 * 预分配容量。
	 * @param count 预期元素数；为 0 时用不可变空列表（省内存）
	 */
	override fun init(count: Int) {
		list = if (count == 0) Collections.emptyList() else ArrayList(count)
	}

	override fun accept(t: T) {
		// 原 Java 字段声明为 List（Java 的 List 接口带 add 方法）；Kotlin 的 List 只读，需向下转型。
		// count==0 时为不可变空列表，add 抛 UnsupportedOperationException——与原行为一致。
		@Suppress("UNCHECKED_CAST")
		(list as MutableList<R>).add(convert.apply(t))
	}

	/** @return 累积的所有转换结果 */
	public val result: List<R> get() = list
}
