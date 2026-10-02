package jadx.core.dex.instructions.java

import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.TargetInsnNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.InsnUtils

/**
 * Java 字节码特有的 `jsr`（jump to subroutine）指令。
 *
 * 这是老版本 Java 编译器为 finally 块生成的“子程序调用”指令，Dex 中没有对应物，
 * 因此放在 `java` 子包中单独表示。它带有跳转目标偏移 [target]。
 *
 * Kotlin 转换说明：`target` 用 `@JvmField` 暴露，避免与 [getTarget] 的 getter 冲突。
 */
open class JsrNode : TargetInsnNode {

	@JvmField
	protected val target: Int

	constructor(target: Int) : this(InsnType.JAVA_JSR, target, 0)

	protected constructor(type: InsnType, target: Int, argsCount: Int) : super(type, argsCount) {
		this.target = target
	}

	open fun getTarget(): Int = target

	override fun copy(): InsnNode = copyCommonParams(JsrNode(target))

	override fun toString(): String = baseString() + " -> " + InsnUtils.formatOffset(target) + attributesString()
}
