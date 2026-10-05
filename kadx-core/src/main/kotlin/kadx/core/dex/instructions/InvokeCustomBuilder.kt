package kadx.core.dex.instructions

import kadx.api.plugins.input.insns.InsnData
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.KadxError
import kadx.core.dex.instructions.invokedynamic.CustomLambdaCall
import kadx.core.dex.instructions.invokedynamic.CustomRawCall
import kadx.core.dex.instructions.invokedynamic.CustomStringConcat
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.input.InsnDataUtils

/**
 * invoke-custom 指令的构建入口。
 *
 * 按 call-site 的参数形态分派：
 * 1. lambda（LambdaMetafactory）→ [CustomLambdaCall]；
 * 2. 字符串拼接（StringConcatFactory）→ [CustomStringConcat]；
 * 3. 其它 → [CustomRawCall]；失败时生成一个带 KADX_ERROR 属性的 NOP 指令。
 *
 * Kotlin 转换说明：静态方法 [build] 放入 companion + `@JvmStatic`，
 * Java 调用方仍写 `InvokeCustomBuilder.build(...)`。
 */
class InvokeCustomBuilder {

	companion object {
		fun build(mth: MethodNode, insn: InsnData, isRange: Boolean): InsnNode {
			try {
				val callSite = InsnDataUtils.getCallSite(insn)
					?: throw KadxRuntimeException("Failed to get call site for insn: $insn")
				callSite.load()
				val values = callSite.values
				if (CustomLambdaCall.isLambdaInvoke(values)) {
					return CustomLambdaCall.buildLambdaMethodCall(mth, insn, isRange, values)
				}
				if (CustomStringConcat.isStringConcat(values)) {
					return CustomStringConcat.buildStringConcat(insn, isRange, values)
				}
				try {
					return CustomRawCall.build(mth, insn, isRange, values)
				} catch (e: Exception) {
					mth.addWarn(
						"Failed to decode invoke-custom: \n" + Utils.listToString(values, "\n") +
							",\n exception: " + Utils.getStackTrace(e),
					)
					val nop = InsnNode(InsnType.NOP, 0)
					nop.add(AFlag.SYNTHETIC)
					nop.addAttr(AType.KADX_ERROR, KadxError("Failed to decode invoke-custom: $values", e))
					return nop
				}
			} catch (e: Exception) {
				throw KadxRuntimeException("'invoke-custom' instruction processing error: " + e.message, e)
			}
		}
	}
}
