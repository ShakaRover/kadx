package kadx.core.dex.instructions

import kadx.api.plugins.input.data.IMethodProto
import kadx.api.plugins.input.insns.InsnData
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.InsnUtils

/**
 * MethodHandle 多态调用指令（invoke-polymorphic）。
 *
 * 与普通调用不同，它的实际方法签名由指令携带的 [proto]（方法原型）决定，
 * 因此除了调用点方法信息外，还需要记录 [baseCallRef]（被调用的 MethodHandle 基础方法）。
 *
 * Kotlin 转换说明：[proto] / [baseCallRef] 为私有字段，对外暴露原 Java 方法名
 * `getProto()` / `getBaseCallRef()`。
 */
open class InvokePolymorphicNode : InvokeNode {

	val proto: IMethodProto
	val baseCallRef: MethodInfo

	constructor(callMth: MethodInfo, insn: InsnData, proto: IMethodProto, baseRef: MethodInfo, isRange: Boolean) :
		super(callMth, insn, InvokeType.POLYMORPHIC, true, isRange) {
		this.proto = proto
		this.baseCallRef = baseRef
	}

	constructor(callMth: MethodInfo, argsCount: Int, proto: IMethodProto, baseRef: MethodInfo) :
		super(callMth, InvokeType.POLYMORPHIC, argsCount) {
		this.proto = proto
		this.baseCallRef = baseRef
	}

	override fun copy(): InsnNode {
		val copy = InvokePolymorphicNode(callMth, argsCount, proto, baseCallRef)
		copyCommonParams(copy)
		return copy
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is InvokePolymorphicNode || !super.isSame(obj)) {
			return false
		}
		return proto == obj.proto
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(InsnUtils.formatOffset(offset)).append(": INVOKE_POLYMORPHIC ")
		if (result != null) {
			sb.append(result).append(" = ")
		}
		if (!appendArgs(sb)) {
			sb.append('\n')
		}
		appendAttributes(sb)
		sb.append(" base: ").append(baseCallRef).append('\n')
		sb.append(" proto: ").append(proto).append('\n')
		return sb.toString()
	}
}
