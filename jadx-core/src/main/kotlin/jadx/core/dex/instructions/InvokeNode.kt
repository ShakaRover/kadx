package jadx.core.dex.instructions

import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.InsnNode

/**
 * 普通方法调用指令（invoke-virtual / invoke-static / invoke-direct 等）。
 *
 * 参数列表的第一个位置在实例调用时是接收者（this），其余是方法参数；
 * 静态调用没有接收者。构造时直接按方法签名从指令寄存器构造出参数。
 *
 * Kotlin 转换说明：
 * - [callMth] 覆写自 [BaseInvokeNode] 的抽象属性，JVM getter 名 `getCallMth()`；
 * - [invokeType] 声明为属性，JVM getter 名 `getInvokeType()`；
 * - 三个构造器保持与原 Java 相同的 JVM 签名。
 */
open class InvokeNode(
	override val callMth: MethodInfo,
	val invokeType: InvokeType,
	argsCount: Int,
) : BaseInvokeNode(InsnType.INVOKE, argsCount) {

	constructor(mthInfo: MethodInfo, insn: InsnData, invokeType: InvokeType, isRange: Boolean) :
		this(mthInfo, invokeType, mthInfo.argsCount + if (invokeType != InvokeType.STATIC) 1 else 0) {
		addInsnArgs(mthInfo, insn, invokeType != InvokeType.STATIC, isRange)
	}

	constructor(mth: MethodInfo, insn: InsnData, type: InvokeType, instanceCall: Boolean, isRange: Boolean) :
		this(mth, type, mth.argsCount + if (instanceCall) 1 else 0) {
		addInsnArgs(mth, insn, instanceCall, isRange)
	}

	/** 根据方法签名把指令寄存器转换成参数列表（实例调用时第一个参数是接收者）。 */
	private fun addInsnArgs(mth: MethodInfo, insn: InsnData, instanceCall: Boolean, isRange: Boolean) {
		var k = if (isRange) insn.getReg(0) else 0
		if (instanceCall) {
			val r = if (isRange) k else insn.getReg(k)
			addReg(r, mth.declClass.type)
			k++
		}
		for (arg in mth.argumentsTypes) {
			addReg(if (isRange) k else insn.getReg(k), arg)
			k += arg.getRegCount()
		}
		val resReg = insn.resultReg
		if (resReg != -1) {
			setResult(InsnArg.reg(resReg, mth.returnType))
		}
	}

	override fun getInstanceArg(): InsnArg? {
		if (invokeType != InvokeType.STATIC && getArgsCount() > 0) {
			return getArg(0)
		}
		return null
	}

	override fun isStaticCall(): Boolean = invokeType == InvokeType.STATIC

	fun isPolymorphicCall(): Boolean {
		if (invokeType == InvokeType.POLYMORPHIC) {
			return true
		}
		// Java 字节码可能用带改写方法信息的 virtual 调用来表示 MethodHandle.invoke
		if (invokeType == InvokeType.VIRTUAL &&
			callMth.declClass.fullName == "java.lang.invoke.MethodHandle" &&
			(callMth.name == "invoke" || callMth.name == "invokeExact")
		) {
			return true
		}
		return false
	}

	override fun getFirstArgOffset(): Int = if (invokeType == InvokeType.STATIC) 0 else 1

	override fun copy(): InsnNode = copyCommonParams(InvokeNode(callMth, invokeType, getArgsCount()))

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is InvokeNode || !super.isSame(obj)) {
			return false
		}
		return invokeType == obj.invokeType && callMth == obj.callMth
	}

	override fun toString(): String = baseString() + " " + invokeType + " call: " + callMth + attributesString()
}
