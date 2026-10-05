package kadx.core.utils

import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.InsnNode
import java.util.Objects

/**
 * 基本块 + 指令的配对（图结构辅助 holder）。
 *
 * **用途**：[RegionUtils.getLastInsnWithBlock] 返回“区域内最后一条指令及其所属块”，
 * 供 `SwitchBreakVisitor` 等使用。
 *
 * **为什么不使用 data class**：原 Java 版本手写了 `hashCode`（`Objects.hash`），
 * 与 data class 生成的公式不同；并且 `toString` 也是自定义格式，故保留普通 class。
 */
class BlockInsnPair(
	val block: IBlock,
	val insn: InsnNode,
) {

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is BlockInsnPair) {
			return false
		}
		return block == o.block && insn == o.insn
	}

	override fun hashCode(): Int = Objects.hash(block, insn)

	override fun toString(): String = "BlockInsnPair{$block: $insn}"
}
