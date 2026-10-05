package kadx.plugins.input.java.data.attributes

import org.jetbrains.annotations.Nullable

/**
 * 单个 class/方法/字段节点上所有已解析 attribute 的容器。
 *
 * **做什么**：用"类型 id → 属性实例"的定长数组存放属性，按 [JavaAttrType] O(1) 存取；
 * 同类型属性在节点上唯一（后加的覆盖先加的）。
 *
 * **为什么用数组而不是 Map**：属性种类固定且数量小（[JavaAttrType.size()]），
 * 数组比 HashMap 更省内存、更快，适合每个方法/字段都持有一份的场景。
 */
class JavaAttrStorage {

	// 底层槽位数组，下标即 JavaAttrType.id；未解析的属性为 null
	private val map: Array<IJavaAttribute?> = arrayOfNulls(JavaAttrType.size())

	/** 把 [value] 存到 [type] 对应的槽位（同类型重复添加会覆盖） */
	fun add(type: JavaAttrType<*>, value: IJavaAttribute) {
		map[type.id] = value
	}

	/** @return [type] 对应的属性实例；尚未解析时返回 null */
	@Nullable
	fun <A : IJavaAttribute> get(type: JavaAttrType<A>): A? = map[type.id] as A?

	/** @return 已解析（非 null）的属性个数 */
	fun size(): Int {
		var count = 0
		for (attr in map) {
			if (attr != null) {
				count++
			}
		}
		return count
	}

	override fun toString(): String = "AttributesStorage{size=" + size() + '}'

	companion object {
		/** 空容器单例：attribute 区为空时直接返回它，避免分配数组 */
		val EMPTY: JavaAttrStorage = JavaAttrStorage()
	}
}
