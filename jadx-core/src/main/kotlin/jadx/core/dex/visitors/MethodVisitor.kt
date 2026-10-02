package jadx.core.dex.visitors

import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxException
import java.util.function.Consumer

/**
 * 把任意 [Consumer] 包装成 visitor 的轻量适配器。
 *
 * **做什么**：只关心方法级回调。`Jadx` 的 Pass 列表里用它临时插入一个闭包式处理步骤
 * （例如 `new MethodVisitor("ForceGenerateAll", mth -> ...)`）。
 *
 * **为什么是普通类而不是 data class**：它被当作 visitor 实例使用，需要保持对象身份语义。
 */
class MethodVisitor(
	private val name: String,
	private val visitor: Consumer<MethodNode>,
) : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		visitor.accept(mth)
	}

	override fun getName(): String = name
}
