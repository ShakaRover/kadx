package jadx.core.dex.attributes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.utils.Utils

/**
 * “列表型”属性：把同一类型的多个属性对象聚合成一个属性存放。
 *
 * **为什么需要它？**
 * [AttributeStorage] 中每种属性类型只能存一个对象。但有些信息天然是列表
 * （例如一个基本块上的多个 [jadx.core.dex.attributes.nodes.LoopInfo]、多条
 * 跳转信息 `JumpInfo` 等）。于是用一个 [AttrList] 作为“容器属性”，内部维护一个列表。
 *
 * **不可变/可变说明**：构造时直接持有传入列表的引用（与原 Java 一致，不做拷贝），
 * 因此后续通过 [list] 修改会反映到该属性上。
 *
 * @param T 列表元素的类型
 */
class AttrList<T> : IJadxAttribute {

	companion object {
		/** 逗号分隔的字符串超过该长度时，改用换行分隔，保证输出可读性 */
		private const val MAX_ATTRLIST_LENGTH = 300
	}

	private val type: IJadxAttrType<AttrList<T>>

	/**
	 * 内部列表。原 Java 字段声明为 `List<T>`，但运行期实际会通过 [getList] 做 `add`，
	 * 所以 Kotlin 侧声明为 [MutableList]（JVM 擦除后仍是 `java.util.List`，Java 调用方无差异）。
	 * 直接复用传入的列表引用，保持与原 Java 完全一致的别名语义。
	 */
	@Suppress("UNCHECKED_CAST")
	val list: MutableList<T>

	constructor(type: IJadxAttrType<AttrList<T>>, attrList: List<T>) {
		this.type = type
		this.list = attrList as MutableList<T>
	}

	constructor(type: IJadxAttrType<AttrList<T>>) {
		this.type = type
		this.list = ArrayList()
	}

	override fun getAttrType(): IJadxAttrType<AttrList<T>> = type

	override fun toString(): String {
		val commaDelimited = Utils.listToString(list, ", ")
		// 逗号分隔太长时改用换行分隔，保持可读性
		if (commaDelimited.length > MAX_ATTRLIST_LENGTH) {
			return Utils.listToString(list, "\n    ")
		}
		return commaDelimited
	}
}
