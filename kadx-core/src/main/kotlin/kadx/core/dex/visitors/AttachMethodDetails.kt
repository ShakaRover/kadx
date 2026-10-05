package kadx.core.dex.visitors

import kadx.core.dex.instructions.BaseInvokeNode
import kadx.core.dex.nodes.IMethodDetails
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.nodes.utils.MethodUtils
import kadx.core.dex.visitors.blocks.BlockSplitter
import kadx.core.utils.exceptions.KadxException

/**
 * 方法细节挂载访问者。
 *
 * **做什么**：遍历方法中的所有调用指令，把被调用方法的细节（[IMethodDetails]：
 * 参数名、泛型、是否为库方法等）作为属性挂到指令上。
 *
 * **为什么**：后续的类型推断、变量命名、代码生成都需要这些细节；提前统一挂载
 * 可避免各处重复解析。
 */
@KadxVisitor(
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

	@Throws(KadxException::class)
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
