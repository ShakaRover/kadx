package kadx.core.utils

import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IContainer

/**
 * “块 + 其父容器”的配对。
 *
 * **用途**：`SwitchBreakVisitor` 删除公共 break 时，需要知道要删除的块以及它所在的父容器。
 * 构造时对两个参数做非空校验（与 Java `Objects.requireNonNull` 一致，抛 NPE）。
 */
class BlockParentContainer(parent: IContainer, block: IBlock) {

	val block: IBlock = requireNotNull(block)
	val parent: IContainer = requireNotNull(parent)

	override fun toString(): String = "BlockParentContainer{$block, parent=$parent}"
}
