package kadx.core.dex.visitors.finaly

import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.nodes.InsnNode

/**
 * “指令是否相同”的判定策略基类。
 *
 * **用途**：finally 恢复时需要判断两条来自不同分支的指令是否可以视为“重复指令”。
 * 不同场景（例如是否考虑调试信息、常量值）判定规则可能不同，因此抽象成策略，
 * 由 [SameInstructionsStrategyImpl] 提供默认实现。
 *
 * **Kotlin 转换说明**：保持 `abstract class`，方法签名与 Java 完全一致，
 * Java 调用方（如 C14 的指令比较器）无需改动。
 */
abstract class SameInstructionsStrategy {

	/** 判断两条指令本身是否相同（通常比较指令类型与操作码）。 */
	abstract fun sameInsns(dupInsn: InsnNode, fInsn: InsnNode): Boolean

	/**
	 * 判断两个参数是否相同。
	 *
	 * [dupArg] 允许为 null（原 Java 会先判空返回 false），[fArg] 则按原逻辑直接解引用。
	 */
	abstract fun isSameArgs(dupArg: InsnArg?, fArg: InsnArg): Boolean
}
