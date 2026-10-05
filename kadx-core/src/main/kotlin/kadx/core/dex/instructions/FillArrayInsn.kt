package kadx.core.dex.instructions

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.nodes.InsnNode

/**
 * `fill-array-data` 指令本身：把 [target] 处引用的 [FillArrayData] 数据写入数组。
 *
 * 它只带一个参数（目标数组寄存器），真正的数据在关联的 [arrayData] 里，
 * 由解析阶段后续通过 [setArrayData] 绑定。
 *
 * Kotlin 转换说明：[target] 声明为属性，JVM getter 名就是 `getTarget()`。
 */
class FillArrayInsn(arg: InsnArg, val target: Int) : InsnNode(InsnType.FILL_ARRAY, 1) {

	private var arrayData: FillArrayData? = null

	init {
		addArg(arg)
	}

	fun setArrayData(arrayData: FillArrayData) {
		this.arrayData = arrayData
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is FillArrayInsn || !super.isSame(obj)) {
			return false
		}
		return arrayData == obj.arrayData
	}

	override fun copy(): InsnNode {
		val copy = FillArrayInsn(getArg(0), target)
		return copyCommonParams(copy)
	}

	override fun toString(): String = super.toString() + ", data: " + arrayData

	val size: Int get() = checkNotNull(arrayData).size

	val elementType: ArgType get() = checkNotNull(arrayData).elementType

	fun getLiteralArgs(elType: ArgType): List<LiteralArg> = checkNotNull(arrayData).getLiteralArgs(elType)

	fun dataToString(): String = arrayData.toString()
}
