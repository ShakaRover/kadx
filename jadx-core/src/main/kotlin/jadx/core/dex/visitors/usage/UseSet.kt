package jadx.core.dex.visitors.usage

import java.util.HashMap
import java.util.HashSet

/**
 * 通用「对象 -> 使用集合」映射（用途：收集类/方法/字段的依赖与引用关系）。
 *
 * **做什么**：把每个对象（K）映射到一组使用它的对象（V），例如
 * 「类 -> 依赖的类」「方法 -> 调用它的方法」。同一对 (K, V) 只保存一次。
 *
 * **为什么要排除自引用**：原 Java 的 `add` 里 `if (obj == use) return`，
 * 即对象不会被记录为“使用自己”，Kotlin 侧用 `===` 保持引用比较语义。
 *
 * **Kotlin 转换说明**：这是包内辅助类，原 Java 为 package-private，
 * Kotlin 用 `internal` 近似（模块内可见）；保留普通 class（无 equals/hashCode）。
 */
internal class UseSet<K, V> {

	/** 对象 -> 使用它的对象集合；用 HashMap 保证与 Java 相同的键语义 */
	private val useMap: MutableMap<K, MutableSet<V>> = HashMap()

	/** 记录一次使用关系；obj 与 use 是同一引用时忽略（自引用排除）。 */
	fun add(obj: K, use: V) {
		if (isSameRef(obj, use)) {
			return
		}
		val set = useMap.computeIfAbsent(obj) { HashSet() }
		set.add(use)
	}

	/** 查询某个对象的使用集合；不存在返回 null（与原 Java 一致）。 */
	fun get(obj: K): MutableSet<V>? = useMap[obj]

	/** 查询某个对象的使用集合；不存在返回 defaultValue。 */
	fun getOrDefault(obj: K, defaultValue: Set<V>): Set<V> = useMap[obj] ?: defaultValue

	/** 遍历所有 (对象, 使用集合) 条目并交给回调处理。 */
	fun visit(consumer: (K, Set<V>) -> Unit) {
		for ((key, value) in useMap) {
			consumer(key, value)
		}
	}

	/**
	 * 引用相等判断的辅助方法。
	 *
	 * 原 Java 的 `obj == use` 是引用比较；K 与 V 是互不相关的类型参数，
	 * 直接写 `===` 可能触发 Kotlin 的“不同类型不可做恒等比较”限制，
	 * 因此统一提升为 `Any?` 再比较，语义完全一致。
	 */
	private fun isSameRef(a: Any?, b: Any?): Boolean = a === b
}
