package jadx.core.dex.instructions

import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.invokedynamic.CustomRawCall
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.InsnUtils
import jadx.core.utils.Utils

/**
 * 原始 invoke-custom 指令信息：无法完全解析时，按“等价的 polymorphic 调用”输出。
 *
 * 它包含两部分：
 * - [resolve]：解析调用点的指令（只使用常量参数）；
 * - 自身：调用解析出的方法。
 *
 * 具体构建逻辑见 `CustomRawCall`。
 *
 * Kotlin 转换说明：`callSiteValues` 在构造后可被填充，故为可空属性；
 * 对外仍暴露 `getCallSiteValues()` / `setCallSiteValues(...)`。
 */
open class InvokeCustomRawNode : InvokeNode {

	private val resolve: InvokeNode

	var callSiteValues: List<EncodedValue>? = null

	constructor(resolve: InvokeNode, mthInfo: MethodInfo, insn: InsnData, isRange: Boolean) :
		super(mthInfo, insn, InvokeType.CUSTOM_RAW, false, isRange) {
		this.resolve = resolve
	}

	constructor(resolve: InvokeNode, mthInfo: MethodInfo, invokeType: InvokeType, argsCount: Int) :
		super(mthInfo, invokeType, argsCount) {
		this.resolve = resolve
	}

	fun getResolveInvoke(): InvokeNode = resolve

	override fun copy(): InsnNode {
		val copy = InvokeCustomRawNode(resolve, callMth, invokeType, getArgsCount())
		copyCommonParams(copy)
		copy.callSiteValues = callSiteValues
		return copy
	}

	override fun isStaticCall(): Boolean = true

	override fun getFirstArgOffset(): Int = 0

	override fun getInstanceArg(): InsnArg? = null

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj is InvokeCustomRawNode) {
			return super.isSame(obj) && resolve.isSame(obj.resolve)
		}
		return false
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(InsnUtils.formatOffset(offset)).append(": INVOKE_CUSTOM ")
		if (getResult() != null) {
			sb.append(getResult()).append(" = ")
		}
		if (!appendArgs(sb)) {
			sb.append('\n')
		}
		appendAttributes(sb)
		sb.append(" call-site: \n  ").append(Utils.listToString(callSiteValues, "\n  ")).append('\n')
		return sb.toString()
	}
}
