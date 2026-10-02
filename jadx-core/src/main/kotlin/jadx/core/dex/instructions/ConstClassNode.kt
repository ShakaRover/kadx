package jadx.core.dex.instructions

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.InsnNode

/**
 * 类常量指令（const-class）。
 *
 * 用于表示 `SomeClass.class` 这种“取类对象”的常量。注意它持有的是
 * 目标类的 [ArgType]（即 [clsType]），而不是 `Class` 运行时对象。
 *
 * Kotlin 转换说明：[clsType] 声明为属性，其 JVM getter 名就是 `getClsType()`。
 */
class ConstClassNode(val clsType: ArgType) : InsnNode(InsnType.CONST_CLASS, 0) {

	override fun copy(): InsnNode = copyCommonParams(ConstClassNode(clsType))

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is ConstClassNode || !super.isSame(obj)) {
			return false
		}
		return clsType == obj.clsType
	}

	override fun toString(): String = super.toString() + " " + clsType + ".class"
}
