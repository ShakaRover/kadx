package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor

/**
 * 类型推导收尾 Pass：检查最终是否仍有未知类型。
 *
 * **算法意图**：[FixTypesVisitor] 等 Pass 尽力推导后，本 Pass 做兜底：
 * - 若某个 SSA 变量最终类型仍未知，输出告警注释，方便定位推导失败；
 * - 若代码变量（[jadx.core.dex.instructions.args.CodeVar]）类型为 null，
 *   统一置为 [ArgType.UNKNOWN]，避免后续阶段处理空类型。
 *
 * **Kotlin 转换说明**：保持 `class` 为 final；用普通 `for` 循环替代 Java `forEach`。
 */
@JadxVisitor(
	name = "Finish Type Inference",
	desc = "Check used types",
	runAfter = [TypeInferenceVisitor::class],
)
class FinishTypeInference : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() || mth.getSVars().isEmpty()) {
			return
		}
		for (ssaVar in mth.getSVars()) {
			val type = ssaVar.typeInfo.getType()
			if (!type.isTypeKnown()) {
				mth.addWarnComment("Type inference failed for: " + ssaVar.getDetailedVarInfo(mth))
			}
			val codeVarType = ssaVar.codeVar.type
			if (codeVarType == null) {
				ssaVar.codeVar.type = ArgType.UNKNOWN
			}
		}
	}

	override fun getName(): String = "FinishTypeInference"
}
