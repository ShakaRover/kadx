package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.RegisterArg

/**
 * Phi 指令列表属性：挂在基本块上，保存该块开头的全部 phi 指令。
 *
 * **用途**：SSA 变换时为每个汇合块生成 phi 节点，统一登记在本属性里，
 * 后续类型推断、指令删除都需要遍历/修改这个列表。
 *
 * **Kotlin 转换说明**：[list] 必须是可变列表（Java 侧会 `getList().add(...)` /
 * `removeIf(...)`），声明为 [MutableList]，JVM 擦除后仍是 `java.util.List`。
 */
class PhiListAttr : IJadxAttribute {

	/** 该基本块上的 phi 指令列表（可变，Java 调用方会直接增删） */
	val list: MutableList<PhiInsn> = ArrayList()

	override fun getAttrType(): AType<PhiListAttr> = AType.PHI_LIST

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("PHI:")
		for (phiInsn in list) {
			val resArg: RegisterArg? = phiInsn.getResult()
			if (resArg != null) {
				sb.append(" r").append(resArg.regNum)
			}
		}
		for (phiInsn in list) {
			sb.append('\n').append("  ").append(phiInsn)
		}
		return sb.toString()
	}
}
