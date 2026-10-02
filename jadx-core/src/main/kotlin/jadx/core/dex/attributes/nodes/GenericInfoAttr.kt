package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.args.ArgType

/**
 * 泛型信息属性：记录某个指令/节点上推断出的泛型类型列表。
 *
 * **用途**：类型推断阶段可能无法完全确定泛型实参，本属性保存一份“候选泛型类型”，
 * 供代码生成阶段决定是否输出显式类型参数。
 *
 * **Kotlin 转换说明**：原 Java 的 `isExplicit()` / `setExplicit(boolean)` 不是 Kotlin
 * 布尔属性默认生成的 getter 名（Kotlin 会生成 `getExplicit()`），因此这里用
 * 私有字段 + 显式函数，保持 JVM 方法名不变。
 */
class GenericInfoAttr(val genericTypes: List<ArgType>) : IJadxAttribute {

	private var explicit: Boolean = false

	fun isExplicit(): Boolean = explicit

	fun setExplicit(explicit: Boolean) {
		this.explicit = explicit
	}

	override fun getAttrType(): AType<GenericInfoAttr> = AType.GENERIC_INFO

	override fun toString(): String = "GenericInfoAttr{$genericTypes, explicit=$explicit}"
}
