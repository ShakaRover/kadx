package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute
import jadx.core.dex.attributes.AType

/**
 * 方法抛出异常属性：记录方法签名中声明的受检异常类型名集合。
 *
 * **用途**：`MethodThrowsVisitor` 解析 `throws` 子句时把类型名收集到这里，
 * 并沿类继承层级向上合并父类方法的声明。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 的布尔 getter 名为 `isVisited()`，Kotlin 属性 `var visited` 只会生成 `getVisited()`，
 *   因此用私有字段 + 显式 [isVisited] / [setVisited]，保持 JVM 方法名不变；
 * - [list] 声明为只读属性，其 getter 返回可变集合，Java 调用方仍可 `getList().add(...)`。
 */
class MethodThrowsAttr(val list: MutableSet<String>) : PinnedAttribute() {

	/** 是否已在继承链合并过程中访问过（避免重复处理） */
	private var visited: Boolean = false

	fun isVisited(): Boolean = visited

	fun setVisited(visited: Boolean) {
		this.visited = visited
	}

	override val attrType: IJadxAttrType<MethodThrowsAttr> get() = AType.METHOD_THROWS

	override fun toString(): String = "THROWS:$list"
}
