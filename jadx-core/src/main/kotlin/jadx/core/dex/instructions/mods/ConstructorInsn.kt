package jadx.core.dex.instructions.mods

import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode

/**
 * 构造器调用指令（`<init>` / `new-instance` 后的初始化调用）。
 *
 * jadx 把构造器调用细分为几种 [CallType]：真正 new 实例、super 调用、
 * 本类其它构造器调用（this）、以及自调用。区分它们对还原 `super(...)`、
 * `this(...)` 以及匿名内部类构造非常关键。
 *
 * Kotlin 转换说明：
 * - [callMth] 覆写自 [BaseInvokeNode]，JVM getter 名 `getCallMth()`；
 * - [callType] 声明为属性，JVM getter 名 `getCallType()`；
 * - [isNewInstance] / [isSuper] / [isThis] / [isSelf] 声明为 Boolean 属性，
 *   JVM getter 名与原 Java 一致。
 */
class ConstructorInsn : BaseInvokeNode {

	override val callMth: MethodInfo
	val callType: CallType

	enum class CallType {
		CONSTRUCTOR, // 仅 new 实例
		SUPER, // super 调用
		THIS, // 从另一个构造器调用本类构造器
		SELF, // 调用自身
	}

	constructor(mth: MethodNode, invoke: InvokeNode) : this(mth, invoke, invoke.callMth)

	constructor(mth: MethodNode, invoke: InvokeNode, callMth: MethodInfo) :
		super(InsnType.CONSTRUCTOR, invoke.argsCount - 1) {
		this.callMth = callMth
		this.callType = getCallType(mth, callMth.declClass, invoke.getArg(0))
		val argsCount = invoke.argsCount
		for (i in 1 until argsCount) {
			addArg(invoke.getArg(i))
		}
	}

	constructor(callMth: MethodInfo, callType: CallType) :
		super(InsnType.CONSTRUCTOR, callMth.argsCount) {
		this.callMth = callMth
		this.callType = callType
	}

	private fun getCallType(mth: MethodNode, classType: ClassInfo, instanceArg: InsnArg): CallType {
		if (!instanceArg.isThis()) {
			return CallType.CONSTRUCTOR
		}
		if (classType != mth.parentClass.classInfo) {
			return CallType.SUPER
		}
		if (callMth.shortId == mth.methodInfo.shortId) {
			// 调用自身构造器
			return CallType.SELF
		}
		return CallType.THIS
	}

	override fun getInstanceArg(): RegisterArg? = null

	val classType: ClassInfo get() = callMth.declClass

	val isNewInstance: Boolean get() = callType == CallType.CONSTRUCTOR

	val isSuper: Boolean get() = callType == CallType.SUPER

	val isThis: Boolean get() = callType == CallType.THIS

	val isSelf: Boolean get() = callType == CallType.SELF

	override fun isStaticCall(): Boolean = false

	override fun getFirstArgOffset(): Int = 0

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is ConstructorInsn || !super.isSame(obj)) {
			return false
		}
		return callMth == obj.callMth && callType == obj.callType
	}

	override fun copy(): InsnNode = copyCommonParams(ConstructorInsn(callMth, callType))

	override fun toString(): String = super.toString() + " call: " + callMth + " type: " + callType
}
