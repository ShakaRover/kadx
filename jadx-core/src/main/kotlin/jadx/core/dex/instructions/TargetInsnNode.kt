package jadx.core.dex.instructions

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode

/**
 * 带跳转目标的指令基类（GOTO / IF / SWITCH / JSR 等）。
 *
 * 这些指令在控制流图中会“指向”其它基本块，因此需要在块切分完成后
 * 调用 [initBlocks] 把“指令偏移目标”解析成真正的 [BlockNode]；
 * 块被替换 / 合并时通过 [replaceTargetBlock] 更新引用。
 *
 * Kotlin 转换说明：两个方法原本可被子类覆写，故保留 `open`。
 */
abstract class TargetInsnNode(type: InsnType, argsCount: Int) : InsnNode(type, argsCount) {

	/** 初始化跳转目标对应的基本块；默认空实现，由具体指令子类按需覆写。 */
	open fun initBlocks(curBlock: BlockNode) {
	}

	/** 把指向 [origin] 的目标块替换为 [replace]；默认不处理，返回 false。 */
	open fun replaceTargetBlock(origin: BlockNode, replace: BlockNode): Boolean = false
}
