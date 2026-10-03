package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.args.ArgType
import java.util.Objects

/**
 * 寄存器调试信息属性：记录某个寄存器在调试段中的名字与类型。
 *
 * **用途**：把 DEX 调试信息里的局部变量名/类型挂到具体寄存器参数上，
 * 让反编译输出使用原始变量名（如 `count`、`userName`）。
 *
 * **相等性**：本类实现基于内容的值语义（类型 + 名字都相等即相等），
 * 便于在集合中去重。
 *
 * **Kotlin 转换说明**：原 Java 字段名为 `type` 但 getter 名为 `getRegType()`，
 * 若声明属性 `type` 会生成 `getType()`（多出原不存在的 JVM 方法），
 * 因此属性命名为 [regType]，getter 恰好是 `getRegType()`，JVM 表面不变。
 */
class RegDebugInfoAttr(val regType: ArgType, val name: String) : IJadxAttribute {

	override val attrType: AType<RegDebugInfoAttr> get() = AType.REG_DEBUG_INFO

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val that = other as RegDebugInfoAttr
		return regType == that.regType && name == that.name
	}

	override fun hashCode(): Int = Objects.hash(regType, name)

	override fun toString(): String = "D('" + name + "' " + regType + ')'
}
