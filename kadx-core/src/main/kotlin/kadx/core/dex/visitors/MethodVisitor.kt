package kadx.core.dex.visitors

import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.exceptions.KadxException

/**
 * 把方法级回调包装成 visitor 的轻量适配器。
 *
 * **做什么**：只关心方法级回调。`Kadx` 的 Pass 列表里用它临时插入一个闭包式处理步骤
 * （例如 `MethodVisitor("ForceGenerateAll") { mth -> ... }`）。
 *
 * **为什么是普通类而不是 data class**：它被当作 visitor 实例使用，需要保持对象身份语义。
 */
class MethodVisitor(
	private val name: String,
	private val visitor: (MethodNode) -> Unit,
) : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		visitor(mth)
	}

	override fun getName(): String = name
}
