package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.Utils

/**
 * 强制返回属性：挂在基本块上，表示代码生成时在该处强制输出一条 return 指令。
 *
 * **用途**：某些异常路径或优化后的块需要显式 return（而不是继续 fall-through），
 * 但又没有对应的原始指令，于是用本属性携带一条合成 return 指令。
 */
class ForceReturnAttr(val returnInsn: InsnNode) : IJadxAttribute {

	override val attrType: AType<ForceReturnAttr> get() = AType.FORCE_RETURN

	override fun toString(): String = "FORCE_RETURN " + Utils.listToString(returnInsn.arguments)
}
