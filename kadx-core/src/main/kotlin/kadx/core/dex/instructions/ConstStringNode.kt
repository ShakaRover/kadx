package kadx.core.dex.instructions

import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.StringUtils

/**
 * 字符串常量指令（const-string）。
 *
 * 它把常量池里的字符串直接内联到指令中，代码生成时输出为字符串字面量。
 * 注意字符串里可能包含转义字符，输出前需经 [StringUtils.unescapeString] 还原。
 *
 * Kotlin 转换说明：原 Java 方法名为 `getString()`，且已有 Kotlin 调用点
 * （InsnWrapArg.kt）按显式方法调用，因此这里保留显式 `fun getString()`，
 * 而不是声明成属性。
 */
class ConstStringNode(private val str: String?) : InsnNode(InsnType.CONST_STR, 0) {

	/** 返回字符串字面量；与输入格式的索引解析结果一致，可能为 null。 */
	val string: String? get() = str

	override fun copy(): InsnNode = copyCommonParams(ConstStringNode(str))

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is ConstStringNode || !super.isSame(obj)) {
			return false
		}
		return str == obj.str
	}

	override fun toString(): String = super.baseString() + StringUtils.instance.unescapeString(checkNotNull(str)) + super.attributesString()
}
