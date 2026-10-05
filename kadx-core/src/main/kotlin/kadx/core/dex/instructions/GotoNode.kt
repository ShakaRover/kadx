package kadx.core.dex.instructions

import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.InsnUtils

/**
 * 无条件跳转指令（goto）。
 *
 * [target] 保存的是目标指令的字节码偏移；块切分阶段会依据它定位目标基本块。
 *
 * Kotlin 转换说明：
 * - 原 Java 的 `protected final int target` 被 Java 子类（如 IfNode）直接访问，
 *   这里用 `@JvmField` 保留真正的字段，避免与 [getTarget] 生成的 getter 冲突；
 * - [getTarget] 保持 `open`，因为 IfNode 会覆写它返回“动态目标块”的偏移。
 */
open class GotoNode : TargetInsnNode {

	@JvmField
	protected val target: Int

	constructor(target: Int) : this(InsnType.GOTO, target, 0)

	protected constructor(type: InsnType, target: Int, argsCount: Int) : super(type, argsCount) {
		this.target = target
	}

	open fun getTarget(): Int = target

	override fun copy(): InsnNode = copyCommonParams(GotoNode(target))

	override fun toString(): String = super.toString() + "-> " + InsnUtils.formatOffset(target)
}
