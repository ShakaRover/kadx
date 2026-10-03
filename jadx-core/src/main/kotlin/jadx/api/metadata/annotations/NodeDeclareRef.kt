package jadx.api.metadata.annotations

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef

/**
 * “节点声明”注解：表示某个位置声明了给定的 dex 节点。
 *
 * **做什么**：把 [ICodeNodeRef]（类 / 方法 / 字段 / 包 / 变量）和它在反编译代码中的
 * 声明位置关联起来。[defPos] 是冗余缓存的位置（序列化时需要）。
 *
 * **为什么不能改成 `data class`**：本类有自定义 `equals/hashCode`（只按 [node] 判等，
 * 忽略 [defPos]），`data class` 会破坏该语义。
 *
 * **Kotlin 转换说明**：`node` 声明为非空，Java 传入 null 时由 Kotlin 参数校验抛 NPE，
 * 等价于原来的 `Objects.requireNonNull`。getter 保持显式函数形态。
 */
class NodeDeclareRef(private val node: ICodeNodeRef) : ICodeAnnotation {

	private var defPos: Int = 0

	/** 被声明的节点。 */
	fun getNode(): ICodeNodeRef = node

	/** 声明位置（反编译代码中的字符偏移）。 */
	fun getDefPos(): Int = defPos

	/** 设置声明位置。 */
	fun setDefPos(defPos: Int) {
		this.defPos = defPos
	}

	override val annType: ICodeAnnotation.AnnType get() = ICodeAnnotation.AnnType.DECLARATION

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is NodeDeclareRef) {
			return false
		}
		return node == other.node
	}

	override fun hashCode(): Int = node.hashCode()

	override fun toString(): String = "NodeDeclareRef{" + node + '}'
}
