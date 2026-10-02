package jadx.core.dex.visitors

import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.nodes.utils.MethodUtils
import jadx.core.dex.visitors.blocks.BlockSplitter
import jadx.core.utils.exceptions.JadxException

/**
 * 方法细节挂载访问者。
 *
 * **做什么**：遍历方法中的所有调用指令，把被调用方法的细节（[IMethodDetails]：
 * 参数名、泛型、是否为库方法等）作为属性挂到指令上。
 *
 * **为什么**：后续的类型推断、变量命名、代码生成都需要这些细节；提前统一挂载
 * 可避免各处重复解析。
 */
@JadxVisitor(
	name = "Attach Method Details",
	desc = "Attach method details for invoke instructions",
	runBefore = [
		BlockSplitter::class,
		MethodInvokeVisitor::class,
	],
)
class AttachMethodDetails : AbstractVisitor() {

	private lateinit var methodUtils: MethodUtils

	override fun init(root: RootNode) {
		methodUtils = root.getMethodUtils()
	}

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (insn in checkNotNull(mth.instructions)) {
			if (insn is BaseInvokeNode) {
				attachMethodDetails(insn)
			}
		}
	}

	private fun attachMethodDetails(insn: BaseInvokeNode) {
		val methodDetails: IMethodDetails? = methodUtils.getMethodDetails(insn.callMth)
		if (methodDetails != null) {
			insn.addAttr(methodDetails)
		}
	}
}
