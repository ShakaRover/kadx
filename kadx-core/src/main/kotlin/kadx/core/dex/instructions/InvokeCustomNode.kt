package kadx.core.dex.instructions

import kadx.api.plugins.input.data.MethodHandleType
import kadx.api.plugins.input.insns.InsnData
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.InsnUtils

/**
 * invoke-custom 指令（lambda / 方法引用等）。
 *
 * 它记录 lambda 的实现方法 [implMthInfo]、方法句柄类型 [handleType]，
 * 以及真正要调用的指令 [callInsn]。这些字段在构造后由
 * `CustomLambdaCall` 等构建器逐步填充，故声明为可空属性。
 *
 * Kotlin 转换说明：`isInlineInsn` / `isUseRef` 声明为 Boolean 属性，
 * 其 JVM getter 名为 `isInlineInsn()` / `isUseRef()`，与原 Java 一致。
 */
open class InvokeCustomNode : InvokeNode {

	var implMthInfo: MethodInfo? = null
	var handleType: MethodHandleType? = null
	var callInsn: InsnNode? = null
	var isInlineInsn: Boolean = false
	var isUseRef: Boolean = false

	constructor(lambdaInfo: MethodInfo, insn: InsnData, instanceCall: Boolean, isRange: Boolean) :
		super(lambdaInfo, insn, InvokeType.CUSTOM, instanceCall, isRange)

	private constructor(mth: MethodInfo, invokeType: InvokeType, argsCount: Int) :
		super(mth, invokeType, argsCount)

	override fun copy(): InsnNode {
		val copy = InvokeCustomNode(callMth, invokeType, argsCount)
		copyCommonParams(copy)
		copy.implMthInfo = implMthInfo
		copy.handleType = handleType
		copy.callInsn = callInsn
		copy.isInlineInsn = isInlineInsn
		copy.isUseRef = isUseRef
		return copy
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is InvokeCustomNode || !super.isSame(obj)) {
			return false
		}
		val other = obj
		val thisCall = callInsn
		val otherCall = other.callInsn
		if (thisCall == null) {
			if (otherCall != null) {
				return false
			}
		} else if (otherCall == null || !thisCall.isSame(otherCall)) {
			return false
		}
		return handleType == other.handleType &&
			implMthInfo == other.implMthInfo &&
			isInlineInsn == other.isInlineInsn &&
			isUseRef == other.isUseRef
	}

	/** 若内部调用指令是 invoke 类型，返回其调用信息，否则返回 null。 */
	val invokeCall: BaseInvokeNode? get() {
		val call = callInsn ?: return null
		if (call.type == InsnType.INVOKE) {
			return call as BaseInvokeNode
		}
		return null
	}

	override fun getInstanceArg(): InsnArg? = null

	override fun isStaticCall(): Boolean = true

	override fun getFirstArgOffset(): Int = 0

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(InsnUtils.formatOffset(offset)).append(": INVOKE_CUSTOM ")
		if (result != null) {
			sb.append(result).append(" = ")
		}
		appendArgs(sb)
		appendAttributes(sb)
		sb.append("\n handle type: ").append(handleType)
		sb.append("\n lambda: ").append(implMthInfo)
		sb.append("\n call insn: ").append(callInsn)
		return sb.toString()
	}
}
