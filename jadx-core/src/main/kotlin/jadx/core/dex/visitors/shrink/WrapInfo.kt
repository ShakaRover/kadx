package jadx.core.dex.visitors.shrink

import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode

/**
 * 内联包装信息：记录“哪条指令被内联进了哪个寄存器参数”。
 *
 * **用途**：[ArgsInfo] 在分析可内联性时生成 [WrapInfo]，[CodeShrinkVisitor]
 * 随后据此把被内联的指令真正塞进使用处（wrap）。
 *
 * **Kotlin 转换说明**：原 Java 为 package-private 类，Kotlin 用 `internal`；
 * 只有 `getInsn()/getArg()` 两个简单访问器，改为只读属性。
 */
internal class WrapInfo(
	/** 被内联的指令（赋值指令） */
	val insn: InsnNode,
	/** 接收该指令的寄存器参数 */
	val arg: RegisterArg,
) {

	override fun toString(): String = "WrapInfo: $arg -> $insn"
}
