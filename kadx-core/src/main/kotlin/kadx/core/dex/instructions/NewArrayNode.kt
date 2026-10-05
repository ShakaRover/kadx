package kadx.core.dex.instructions

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.InsnNode

/**
 * 新建数组指令（new-array / newarray / anewarray / multianewarray）。
 *
 * [arrType] 是完整数组类型（如 `int[][]`），参数列表里则是各维度的长度。
 *
 * Kotlin 转换说明：[arrType] 是私有字段，对外暴露原 Java 方法名 `getArrayType()`。
 */
open class NewArrayNode(private val arrType: ArgType, argsCount: Int) : InsnNode(InsnType.NEW_ARRAY, argsCount) {

	val arrayType: ArgType get() = arrType

	/** 数组维度数（`int[]` 为 1，`int[][]` 为 2）。 */
	val dimension: Int get() = arrType.getArrayDimension()

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is NewArrayNode || !super.isSame(obj)) {
			return false
		}
		// 原 Java 用引用比较判断数组类型是否相同
		return arrType === obj.arrType
	}

	override fun copy(): InsnNode = copyCommonParams(NewArrayNode(arrType, argsCount))

	override fun toString(): String = super.toString() + " type: " + arrType
}
