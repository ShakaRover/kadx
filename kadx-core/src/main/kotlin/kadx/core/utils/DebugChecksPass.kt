package kadx.core.utils

import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.utils.exceptions.KadxException

/**
 * 调试检查 Pass：包在某个 visitor 之后，检查该 visitor 执行后方法结构是否仍然自洽。
 *
 * **用途**：仅在开启 debug checks 时插入，开销很大，平时不启用。
 * 若方法已经带 [AType.KADX_ERROR] 则跳过，避免在错误状态上再次抛异常。
 */
class DebugChecksPass(private val visitorName: String) : AbstractVisitor() {

	override fun getName(): String = "Checks-for-$visitorName"

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		if (!mth.contains(AType.KADX_ERROR)) {
			try {
				DebugChecks.runChecksAfterVisitor(mth, visitorName)
			} catch (e: Exception) {
				mth.addError("Check error", e)
			}
		}
	}
}
