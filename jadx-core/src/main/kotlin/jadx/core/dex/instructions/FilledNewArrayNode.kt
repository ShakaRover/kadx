package jadx.core.dex.instructions

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.InsnNode

/**
 * 填充新数组指令（filled-new-array）。
 *
 * 与 [NewArrayNode] 不同，这条指令直接在指令里给出初始元素（[elemType] 为元素类型），
 * 代码生成时通常还原成 `new T[]{...}` 形式。
 *
 * Kotlin 转换说明：[elemType] 声明为属性，其 JVM getter 名就是 `getElemType()`。
 */
open class FilledNewArrayNode(val elemType: ArgType, size: Int) : InsnNode(InsnType.FILLED_NEW_ARRAY, size) {

	/** 返回由元素类型构造出的数组类型（如元素 `int` 对应 `int[]`）。 */
	fun getArrayType(): ArgType = ArgType.array(elemType)

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is FilledNewArrayNode || !super.isSame(obj)) {
			return false
		}
		// 原 Java 用引用比较判断元素类型是否相同
		return elemType === obj.elemType
	}

	override fun copy(): InsnNode = copyCommonParams(FilledNewArrayNode(elemType, getArgsCount()))

	override fun toString(): String = super.toString() + " elemType: " + elemType
}
