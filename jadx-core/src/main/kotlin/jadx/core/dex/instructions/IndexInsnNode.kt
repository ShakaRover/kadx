package jadx.core.dex.instructions

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.InsnUtils
import jadx.core.utils.Utils

/**
 * 带“索引”操作数的指令（cast / check-cast / iget / iput / sget / sput /
 * new-instance / instance-of 等）。
 *
 * [index] 的具体类型取决于指令种类：字段指令里是 `FieldInfo`，类型指令里是
 * `ArgType`，也可能是其它对象，因此声明为 `Any?`。
 *
 * Kotlin 转换说明：
 * - 原 Java 通过 `getIndex()` / `updateIndex(...)` 访问该字段；
 * - 已有 Kotlin 调用点使用 `.index` 属性语法；
 * - 为同时兼容两种写法，这里声明属性并把 setter 的 JVM 名指定为 `updateIndex`。
 */
class IndexInsnNode(type: InsnType, index: Any?, argCount: Int) : InsnNode(type, argCount) {

	@set:JvmName("updateIndex")
	var index: Any? = index

	/** 把 [index] 当作类型使用（cast / check-cast / new-instance 等场景）。 */
	val indexAsType: ArgType get() = index as ArgType

	override fun copy(): IndexInsnNode = copyCommonParams(IndexInsnNode(insnType, index, argsCount))

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is IndexInsnNode || !super.isSame(obj)) {
			return false
		}
		return index == obj.index
	}

	override fun toString(): String = when (insnType) {
		InsnType.CAST, InsnType.CHECK_CAST -> {
			val sb = StringBuilder()
			sb.append(InsnUtils.formatOffset(offset)).append(": ")
			sb.append(insnType).append(' ')
			if (getResult() != null) {
				sb.append(getResult()).append(" = ")
			}
			sb.append('(').append(InsnUtils.indexToString(index)).append(") ")
			sb.append(Utils.listToString(getArguments()))
			sb.toString()
		}

		else -> super.toString() + " " + InsnUtils.indexToString(index)
	}
}
