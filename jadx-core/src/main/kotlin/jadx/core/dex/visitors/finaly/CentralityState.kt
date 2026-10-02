package jadx.core.dex.visitors.finaly

import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import java.util.LinkedList

/**
 * “中心性”状态：帮助判断哪些指令可以被安全跳过，从而区分 finally 代码与正常的 return/throw 收尾代码。
 *
 * **背景**：一个分支的尾部可能既包含业务指令，又包含 finally 复制出来的指令。
 * 我们通过记录“允许作为输出的寄存器”（[allowableOutputArguments]）以及
 * 两个开关（[allowsCentral]、[allowsNonStartingNode]）来判断某条指令是否属于
 * 可以跳过的收尾逻辑，从而在反向遍历时正确匹配 finally 指令。
 *
 * **Kotlin 转换说明**：
 * - 该类是可变状态对象，`duplicate()` 需要深拷贝内部集合；
 * - [allowsCentral]/[allowsNonStartingNode] 声明为 Kotlin 属性，生成的
 *   `getXxx/setXxx` 与原 Java 一致，Java 调用方零改动；
 * - 原 Java `==` 对寄存器参数用的是 `equals`，此处保持 `==`。
 */
class CentralityState(
	val sameInstructionsStrategy: SameInstructionsStrategy,
	var allowsNonStartingNode: Boolean,
) {

	private val allowableOutputArguments: MutableSet<RegisterArg> = HashSet()

	var allowsCentral: Boolean = true

	override fun toString(): String {
		val sb = StringBuilder("CentralityState - ")
		if (allowsCentral) {
			sb.append("allows central")
		} else {
			sb.append("disallows central")
		}
		sb.append(" | ")
		for (registerArg in allowableOutputArguments) {
			sb.append(registerArg.name)
			sb.append(" ")
		}
		return sb.toString()
	}

	fun addAllowableOutput(allowableOutput: RegisterArg) {
		allowableOutputArguments.add(allowableOutput)
	}

	fun addAllowableOutputs(allowableOutputs: Collection<RegisterArg>) {
		allowableOutputArguments.addAll(allowableOutputs)
	}

	/**
	 * 把一条指令的所有寄存器输入都登记为“允许的输出”。
	 *
	 * 例如 `CONST_STR r2 = "..."` 之后紧跟 `RETURN r2`，r2 就是允许的收尾输出。
	 */
	fun addAllowableOutputs(allowableOutputInsn: InsnNode) {
		val registerArgs = LinkedList<RegisterArg>()
		for (arg in allowableOutputInsn.getArgList()) {
			if (arg !is RegisterArg) {
				continue
			}
			registerArgs.add(arg)
		}
		registerArgs.forEach { addAllowableOutput(it) }
	}

	/** 判断某条指令的结果寄存器是否在允许的输出集合中。 */
	fun hasAllowableOutput(insn: InsnNode): Boolean {
		if (allowableOutputArguments.isEmpty()) {
			return false
		}
		val registerArg = insn.getResult() ?: return false
		for (allowableOutput in allowableOutputArguments) {
			if (allowableOutput == registerArg) {
				return true
			}
		}
		return false
	}

	@Suppress("unused")
	fun hasAllowableInputs(insn: InsnNode): Boolean {
		if (allowableOutputArguments.isEmpty()) {
			return false
		}
		val registerArgs = ArrayList<RegisterArg>()
		for (arg in insn.getArgList()) {
			if (arg is RegisterArg) {
				registerArgs.add(arg)
			}
		}
		if (registerArgs.isEmpty() || allowableOutputArguments.isEmpty()) {
			return false
		}
		for (regArg in registerArgs) {
			var foundMatch = false
			for (allowableOutput in allowableOutputArguments) {
				if (regArg == allowableOutput) {
					foundMatch = true
					break
				}
			}
			if (!foundMatch) {
				return false
			}
		}
		return true
	}

	/** 深拷贝：策略与开关沿用，输出集合复制一份，避免分支间相互污染。 */
	fun duplicate(): CentralityState {
		val state = CentralityState(sameInstructionsStrategy, allowsNonStartingNode)
		state.allowsCentral = allowsCentral
		state.allowableOutputArguments.addAll(allowableOutputArguments)
		return state
	}

	fun getAllowableOutputArguments(): MutableSet<RegisterArg> = allowableOutputArguments
}
