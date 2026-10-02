package jadx.core.dex.instructions

import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.InsnNode

/**
 * 方法调用类指令的公共基类。
 *
 * 它把“调用了哪个方法”“接收者参数是谁”“是否静态”“方法参数从第几个位置开始”
 * 这几个问题抽象出来，供 [InvokeNode]、[jadx.core.dex.instructions.mods.ConstructorInsn]
 * 等具体调用指令实现。
 *
 * Kotlin 转换说明：[callMth] 声明为抽象属性，其 JVM getter 名是 `getCallMth()`，
 * 因此 Java 调用方不受影响；Kotlin 调用方可用更简洁的 `.callMth` 语法。
 */
abstract class BaseInvokeNode(type: InsnType, argsCount: Int) : InsnNode(type, argsCount) {

	/** 被调用的方法信息。 */
	abstract val callMth: MethodInfo

	/** 实例调用时的接收者参数；静态调用返回 null。 */
	abstract fun getInstanceArg(): InsnArg?

	/** 是否为静态调用。 */
	abstract fun isStaticCall(): Boolean

	/** 被调用方法参数在指令参数列表中的起始下标。 */
	abstract fun getFirstArgOffset(): Int
}
