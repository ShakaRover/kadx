package jadx.core.dex.visitors.blocks

import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.BlockUtils
import jadx.core.utils.ListUtils
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 把 Java 字节码里的 `jsr/ret`（子程序跳转）展开成普通控制流。
 *
 * **背景**：`JSR`（jump subroutine）允许从多个位置调用同一段代码，常用于实现 `finally`。
 * 它在 Java 7 中已废弃，但老 class 文件仍可能出现。后续 CFG 分析不支持这种“子程序”语义，
 * 因此需要把每个调用点的路径复制一份，消除 `ret` 指令。
 *
 * **算法**：反复寻找以 `JAVA_RET` 结尾的块；对每个这样的块，向上找“多个前驱且全部以
 * `JAVA_JSR` 结尾”的汇合块，删除 `ret` 相关指令，然后为除第一个之外的所有调用者
 * 复制整棵子程序块树，使每个调用者拥有独立的子程序副本。
 *
 * **Kotlin 转换说明**：原类只有静态方法，转为 `object` + `@JvmStatic`。
 */
object ResolveJavaJSR {

	fun process(mth: MethodNode) {
		val blocksCount = checkNotNull(mth.basicBlocks).size
		var k = 0
		while (true) {
			val changed = resolve(mth)
			if (!changed) {
				break
			}
			if (k++ > blocksCount) {
				throw JadxRuntimeException("Fail to resolve jsr instructions")
			}
		}
	}

	private fun resolve(mth: MethodNode): Boolean {
		val blocks = checkNotNull(mth.basicBlocks)
		val blocksCount = blocks.size
		for (block in blocks) {
			if (BlockUtils.checkLastInsnType(block, InsnType.JAVA_RET)) {
				resolveForRetBlock(mth, block)
				// 若块数量发生变化，说明刚刚复制过路径，需要重新开始扫描
				if (blocksCount != checkNotNull(mth.basicBlocks).size) {
					return true
				}
			}
		}
		return false
	}

	private fun resolveForRetBlock(mth: MethodNode, retBlock: BlockNode) {
		BlockUtils.visitPredecessorsUntil(mth, retBlock) { startBlock ->
			val preds = startBlock.predecessors
			var allJsr = preds.size > 1
			for (p in preds) {
				if (!BlockUtils.checkLastInsnType(p, InsnType.JAVA_JSR)) {
					allJsr = false
					break
				}
			}
			if (allJsr) {
				val jsrBlocks = ArrayList(preds)
				val dupBlocks = BlockUtils.collectAllSuccessors(mth, startBlock, false)
				removeInsns(retBlock, startBlock, jsrBlocks)
				processBlocks(mth, retBlock, startBlock, jsrBlocks, dupBlocks)
				return@visitPredecessorsUntil true
			}
			false
		}
	}

	private fun removeInsns(retBlock: BlockNode, startBlock: BlockNode, jsrBlocks: List<BlockNode>) {
		val retInsn = ListUtils.removeLast(retBlock.instructions)
		if (retInsn != null && retInsn.type == InsnType.JAVA_RET) {
			val retArg: InsnArg = retInsn.getArg(0)
			if (retArg.isRegister) {
				val regNum = (retArg as RegisterArg).regNum
				val startInsn = BlockUtils.getFirstInsn(startBlock)
				if (startInsn != null &&
					startInsn.type == InsnType.MOVE &&
					checkNotNull(startInsn.result).regNum == regNum
				) {
					startBlock.instructions.removeAt(0)
				}
			}
		}
		for (p in jsrBlocks) {
			ListUtils.removeLast(p.instructions)
		}
	}

	private fun processBlocks(
		mth: MethodNode,
		retBlock: BlockNode,
		startBlock: BlockNode,
		jsrBlocks: List<BlockNode>,
		dupBlocks: List<BlockNode>,
	) {
		var first: BlockNode? = null
		for (jsrBlock in jsrBlocks) {
			if (first == null) {
				first = jsrBlock
			} else {
				// 为其余每个 JSR 调用点复制一份子程序块树
				val pathBlock = BlockUtils.selectOther(startBlock, jsrBlock.successors)
				BlockSplitter.removeConnection(jsrBlock, startBlock)
				BlockSplitter.removeConnection(jsrBlock, pathBlock)
				val newBlocks = BlockSplitter.copyBlocksTree(mth, dupBlocks)
				val newStart = newBlocks[dupBlocks.indexOf(startBlock)]
				val newRetBlock = newBlocks[dupBlocks.indexOf(retBlock)]
				BlockSplitter.connect(jsrBlock, newStart)
				BlockSplitter.connect(newRetBlock, pathBlock)
			}
		}
		if (first != null) {
			// 第一个调用点直接复用原始子程序
			val pathBlock = BlockUtils.selectOther(startBlock, first.successors)
			BlockSplitter.removeConnection(first, pathBlock)
			BlockSplitter.connect(retBlock, pathBlock)
		}
	}
}
