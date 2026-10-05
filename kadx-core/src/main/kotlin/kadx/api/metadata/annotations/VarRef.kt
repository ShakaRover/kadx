package kadx.api.metadata.annotations

import kadx.api.metadata.ICodeAnnotation

/**
 * 变量引用：通过“VarNode 在代码元数据中的位置”来指向一个变量。
 *
 * **为什么需要它**：创建变量注解时，变量的定义位置可能还没确定，因此
 * [RelatedVarRef] 先持有 [VarNode]，序列化时再取它的定义位置；
 * 反序列化时位置已经知道，则用 [FixedVarRef] 直接保存位置。
 *
 * **Kotlin 转换说明**：静态工厂方法放入 `companion object` 并加 `@JvmStatic`；
 * 两个子类保持为静态嵌套类（Kotlin 默认嵌套类即静态）。
 */
abstract class VarRef : ICodeAnnotation {

	companion object {
		/** 由已知位置构造；位置为 0 视为非法。 */
		@JvmStatic
		fun fromPos(refPos: Int): VarRef {
			require(refPos != 0) { "Zero refPos" }
			return FixedVarRef(refPos)
		}

		/** 由变量节点构造（位置延迟到序列化时再取）。 */
		@JvmStatic
		fun fromVarNode(varNode: VarNode): VarRef = RelatedVarRef(varNode)
	}

	/** 引用位置（VarNode 在代码元数据中的位置）。 */
	abstract fun getRefPos(): Int

	override val annType: ICodeAnnotation.AnnType get() = ICodeAnnotation.AnnType.VAR_REF

	/** 位置已知的引用。 */
	class FixedVarRef(private val refPos: Int) : VarRef() {
		override fun getRefPos(): Int = refPos
	}

	/** 绑定到 [VarNode] 的引用，位置实时取自定义位置。 */
	class RelatedVarRef(private val varNode: VarNode) : VarRef() {
		override fun getRefPos(): Int = varNode.defPosition

		override fun toString(): String = "VarRef{" + varNode + ", name=" + varNode.getName() + ", mth=" + varNode.getMth() + '}'
	}

	override fun toString(): String = "VarRef{" + getRefPos() + '}'
}
