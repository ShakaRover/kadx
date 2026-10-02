package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.InsnNode

/**
 * 类型更新监听器：当某条指令的某个参数被建议改成新类型时被触发。
 *
 * **算法角色**：类型传播时，一条指令的某个参数类型变化往往会“顺带”
 * 约束同指令的其它参数。每种指令注册一个监听器来产生这些连带更新。
 *
 * **Kotlin 转换说明**：声明为 `fun interface`，保持 SAM 语义，
 * Java 侧仍可用方法引用（如 `this::moveListener`）注册。
 */
fun interface ITypeListener {

	/**
	 * 监听函数。
	 *
	 * @param updateInfo    记录本轮所有允许的类型更新
	 * @param insn          发生类型变化的指令
	 * @param arg           建议应用新类型的参数
	 * @param candidateType 建议的新类型
	 * @return 处理结果；返回 null 表示保持回调、等待后续结果
	 */
	fun update(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult?
}
