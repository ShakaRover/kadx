@file:Suppress("UNCHECKED_CAST")

package jadx.core.dex.visitors.blocks

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.Edge
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.BlockUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashSet

/**
 * 基本块（CFG）处理主流程。
 *
 * **做什么**：在 [BlockSplitter] 切分基本块之后，本 Pass 负责把 CFG 规整成便于区域分析
 * 和代码生成的结构：计算支配关系、识别循环、拆分 return/throw 出口、复制简单 move 块等。
 *
 * **核心流程**（[processBlocksTree]）：
 * 1. 删除不可达块；
 * 2. 计算支配树/支配边界，标记并注册循环；
 * 3. 迭代做“块树改造”（[modifyBlocksTree]），直到不再变化；
 * 4. 计算后支配树（若需要）。
 *
 * **Kotlin 转换说明**：本类既是访问器（`new BlockProcessor()`）又有大量静态工具方法，
 * 因此保持普通 `class` + `companion object`（静态方法加 `@JvmStatic`）。
 * 块身份判断一律用 `===`；只读 List 的就地修改用 `as MutableList`（文件级 suppress）。
 */
class BlockProcessor : AbstractVisitor() {

	companion object {
		private val LOG = LoggerFactory.getLogger(BlockProcessor::class.java)

		private const val DEBUG_MODS = false

		/**
		 * 在自定义 Pass 修改了块树之后，重新计算所有附加信息：
		 * 支配关系、支配边界、后支配树、循环与嵌套循环信息。
		 *
		 * 该方法应在 [BlockFinisher] 之前的自定义 Pass 调用。
		 */
		fun updateBlocksData(mth: MethodNode) {
			clearBlocksState(mth)
			DominatorTree.compute(mth)
			markLoops(mth)

			DominatorTree.computeDominanceFrontier(mth)
			registerLoops(mth)
			processNestedLoops(mth)

			PostDominatorTree.compute(mth)

			updateCleanSuccessors(mth)
		}

		fun updateCleanSuccessors(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				block.updateCleanSuccessors()
			}
		}

		/**
		 * 反复检查不可达块：合成拆分块在某些边界情况下可能失去全部前驱，
		 * 这里尝试清理它；其余情况直接报错（不安静地继续）。
		 */
		private fun checkForUnreachableBlocks(mth: MethodNode) {
			while (true) {
				var fixed = false
				for (block in checkNotNull(mth.basicBlocks)) {
					if (block.getPredecessors().isEmpty() && block !== mth.enterBlock) {
						if (block.contains(AType.EXC_SPLIT_CROSS) && fixUnreachableSplitCross(mth, block)) {
							mth.addInfoComment("Removed unreachable split cross block $block")
							fixed = true
							break
						}
						throw JadxRuntimeException("Unreachable block: $block")
					}
				}
				if (!fixed) {
					break
				}
			}
		}

		private fun fixUnreachableSplitCross(mth: MethodNode, splitCross: BlockNode): Boolean {
			var bottomSplitter: BlockNode? = null
			for (succ in splitCross.getSuccessors()) {
				if (succ.contains(AFlag.EXC_BOTTOM_SPLITTER)) {
					bottomSplitter = succ
					break
				}
			}
			if (bottomSplitter == null || bottomSplitter.getPredecessors().size != 1) {
				return false
			}
			val removeSet = HashSet<BlockNode>()
			removeSet.add(bottomSplitter)
			removeSet.add(splitCross)
			removeFromMethod(removeSet, mth)
			return true
		}

		/** 把所有前驱末尾重复的指令上提到循环头，减少重复代码。 */
		private fun deduplicateBlockInsns(mth: MethodNode, block: BlockNode): Boolean {
			if (block.contains(AFlag.LOOP_START) || block.contains(AFlag.LOOP_END)) {
				val predecessors = block.getPredecessors()
				val predsCount = predecessors.size
				if (predsCount > 1) {
					val lastInsn = BlockUtils.getLastInsn(block)
					if (lastInsn != null && lastInsn.type == InsnType.IF) {
						return false
					}
					if (BlockUtils.checkFirstInsn(block) { insn -> insn.contains(AType.EXC_HANDLER) }) {
						return false
					}
					// TODO: 对部分前驱实现指令提取到独立块
					val sameInsnCount = getSameLastInsnCount(predecessors)
					if (sameInsnCount > 0) {
						val insns = getLastInsns(predecessors[0], sameInsnCount)
						insertAtStart(block, insns)
						for (pred in predecessors) {
							getLastInsns(pred, sameInsnCount).clear()
						}
						mth.addDebugComment("Move duplicate insns, count: $sameInsnCount to block $block")
						return true
					}
				}
			}
			return false
		}

		private fun getLastInsns(blockNode: BlockNode, sameInsnCount: Int): MutableList<InsnNode> {
			val instructions = blockNode.instructions
			val size = instructions.size
			return instructions.subList(size - sameInsnCount, size)
		}

		private fun insertAtStart(block: BlockNode, insns: List<InsnNode>) {
			val blockInsns = block.instructions

			val newInsnList = ArrayList<InsnNode>(insns.size + blockInsns.size)
			newInsnList.addAll(insns)
			newInsnList.addAll(blockInsns)

			blockInsns.clear()
			blockInsns.addAll(newInsnList)
		}

		private fun getSameLastInsnCount(predecessors: List<BlockNode>): Int {
			var sameInsnCount = 0
			while (true) {
				var insn: InsnNode? = null
				for (pred in predecessors) {
					val curInsn = getInsnsFromEnd(pred, sameInsnCount) ?: return sameInsnCount
					if (insn == null) {
						insn = curInsn
					} else {
						if (!isSame(insn, curInsn)) {
							return sameInsnCount
						}
					}
				}
				sameInsnCount++
			}
		}

		private fun isSame(insn: InsnNode, curInsn: InsnNode): Boolean = isInsnsEquals(insn, curInsn) && insn.canReorder()

		private fun isInsnsEquals(insn: InsnNode, otherInsn: InsnNode): Boolean {
			if (insn === otherInsn) {
				return true
			}
			if (insn.isSame(otherInsn) && sameArgs(insn.getResult(), otherInsn.getResult())) {
				val argsCount = insn.argsCount
				for (i in 0 until argsCount) {
					if (!sameArgs(insn.getArg(i), otherInsn.getArg(i))) {
						return false
					}
				}
				return true
			}
			return false
		}

		private fun sameArgs(arg: InsnArg?, otherArg: InsnArg?): Boolean {
			if (arg === otherArg) {
				return true
			}
			if (arg == null || otherArg == null) {
				return false
			}
			if (arg.javaClass == otherArg.javaClass) {
				if (arg.isRegister) {
					return (arg as RegisterArg).regNum == (otherArg as RegisterArg).regNum
				}
				if (arg.isLiteral) {
					return (arg as LiteralArg).literal == (otherArg as LiteralArg).literal
				}
				throw JadxRuntimeException("Unexpected InsnArg types: $arg and $otherArg")
			}
			return false
		}

		private fun getInsnsFromEnd(block: BlockNode, number: Int): InsnNode? {
			val instructions = block.instructions
			val insnCount = instructions.size
			if (insnCount <= number) {
				return null
			}
			return instructions[insnCount - number - 1]
		}

		private fun computeDominators(mth: MethodNode) {
			clearBlocksState(mth)
			DominatorTree.compute(mth)
			markLoops(mth)
		}

		/**
		 * 标记回边与循环：若某后继支配它的前驱（或自环），则该后继是循环头，
		 * 这条边是回边，二者共同构成一个自然循环。
		 */
		private fun markLoops(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				for (successor in block.getSuccessors()) {
					if (checkNotNull(block.doms).get(successor.pos) || block === successor) {
						successor.add(AFlag.LOOP_START)
						block.add(AFlag.LOOP_END)

						val loopBlocks = BlockUtils.getAllPathsBlocks(successor, block)
						val loop = LoopInfo(successor, block, loopBlocks)
						successor.addAttr(AType.LOOP, loop)
						block.addAttr(AType.LOOP, loop)
					}
				}
			}
		}

		private fun registerLoops(mth: MethodNode) {
			mth.resetLoops()
			for (block in checkNotNull(mth.basicBlocks)) {
				if (block.contains(AFlag.LOOP_START)) {
					for (loop in block.getAll(AType.LOOP)) {
						mth.registerLoop(loop)
					}
				}
			}
		}

		/** 建立循环的父子关系（内层循环的父循环是其直接外层包含者）。 */
		private fun processNestedLoops(mth: MethodNode) {
			if (mth.loopsCount == 0) {
				return
			}
			for (outLoop in mth.getLoops()) {
				for (innerLoop in mth.getLoops()) {
					if (outLoop === innerLoop) {
						continue
					}
					if (outLoop.loopBlocks.containsAll(innerLoop.loopBlocks)) {
						val parentLoop = innerLoop.parentLoop
						if (parentLoop != null) {
							if (parentLoop.loopBlocks.containsAll(outLoop.loopBlocks)) {
								outLoop.parentLoop = parentLoop
								innerLoop.parentLoop = outLoop
							} else {
								parentLoop.parentLoop = outLoop
							}
						} else {
							innerLoop.parentLoop = outLoop
						}
					}
				}
			}
		}

		private fun modifyBlocksTree(mth: MethodNode): Boolean {
			for (block in checkNotNull(mth.basicBlocks)) {
				if (checkLoops(mth, block)) {
					return true
				}
			}
			if (mergeConstReturn(mth)) {
				return true
			}
			if (CodeFeaturesAttr.contains(mth, CodeFeaturesAttr.CodeFeature.SWITCH)) {
				for (basicBlock in checkNotNull(mth.basicBlocks)) {
					if (duplicateSimpleMoveBlock(mth, basicBlock)) {
						return true
					}
				}
			}
			return splitExitBlocks(mth)
		}

		/** 把 `const` 块与紧随其后的 `return` 块合并，减少块数。 */
		private fun mergeConstReturn(mth: MethodNode): Boolean {
			if (mth.isVoidReturn()) {
				return false
			}
			var changed = false
			for (retBlock in ArrayList(mth.preExitBlocks)) {
				val pred = Utils.getOne(retBlock.getPredecessors())
				if (pred != null) {
					val constInsn = Utils.getOne(pred.getInstructions())
					if (constInsn != null && constInsn.isConstInsn()) {
						val constArg = constInsn.getResult()
						val returnInsn = BlockUtils.getLastInsn(retBlock)
						if (returnInsn != null && returnInsn.type == InsnType.RETURN) {
							val retArg = returnInsn.getArg(0)
							if (checkNotNull(constArg).sameReg(retArg)) {
								mergeConstAndReturnBlocks(mth, retBlock, pred)
								changed = true
							}
						}
					}
				}
			}
			if (changed) {
				removeMarkedBlocks(mth)
				if (DEBUG_MODS) {
					checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Merge const return")
				}
			}
			return changed
		}

		private fun mergeConstAndReturnBlocks(mth: MethodNode, retBlock: BlockNode, pred: BlockNode) {
			pred.instructions.addAll(retBlock.getInstructions())
			pred.copyAttributesFrom(retBlock)
			BlockSplitter.removeConnection(pred, retBlock)
			retBlock.instructions.clear()
			retBlock.add(AFlag.REMOVE)
			val exitBlock = checkNotNull(mth.exitBlock)
			BlockSplitter.removeConnection(retBlock, exitBlock)
			BlockSplitter.connect(pred, exitBlock)
			pred.updateCleanSuccessors()
		}

		private fun independentBlockTreeMod(mth: MethodNode): Boolean {
			var changed = false
			val basicBlocks = checkNotNull(mth.basicBlocks)
			for (basicBlock in basicBlocks) {
				if (deduplicateBlockInsns(mth, basicBlock)) {
					changed = true
				}
			}
			if (BlockExceptionHandler.process(mth)) {
				changed = true
			}
			for (basicBlock in basicBlocks) {
				if (BlockSplitter.removeEmptyBlock(basicBlock)) {
					changed = true
				}
			}
			if (BlockSplitter.removeEmptyDetachedBlocks(mth)) {
				changed = true
			}
			return changed
		}

		/**
		 * 若某块只含一条 move 且所有前驱都以 switch/if 结尾，则复制该 move 块。
		 * 这样有助于恢复 switch 分支顺序与 fallthrough（编译器常把这种 move 块合并掉）。
		 */
		private fun duplicateSimpleMoveBlock(mth: MethodNode, block: BlockNode): Boolean {
			val insns = block.getInstructions()
			if (insns.size == 1 && block.getSuccessors().size == 1) {
				val insn = insns[0]
				if (insn.type == InsnType.MOVE) {
					val preds = block.getPredecessors()
					val predSize = preds.size
					if (predSize >= 3 && onlySwitchAndIfInLastInsns(preds)) {
						// 确认可复制
						val successor = block.getSuccessors()[0]
						val predsCopy = ArrayList(preds)
						for (i in 1 until predSize) {
							val pred = predsCopy[i]
							val newBlock = BlockSplitter.startNewBlock(mth, -1)
							newBlock.add(AFlag.SYNTHETIC)
							for (oldInsn in block.getInstructions()) {
								val copyInsn = oldInsn.copyWithoutSsa()
								copyInsn.add(AFlag.SYNTHETIC)
								newBlock.instructions.add(copyInsn)
							}
							newBlock.copyAttributesFrom(block)
							BlockSplitter.replaceConnection(pred, block, newBlock)
							BlockSplitter.connect(newBlock, successor)
						}
						return true
					}
				}
			}
			return false
		}

		private fun onlySwitchAndIfInLastInsns(preds: List<BlockNode>): Boolean {
			var hasSwitch = false
			var hasIf = false
			for (pred in preds) {
				val lastInsn = BlockUtils.getLastInsn(pred) ?: return false
				when (lastInsn.type) {
					InsnType.SWITCH -> hasSwitch = true
					InsnType.IF -> hasIf = true
					else -> return false
				}
			}
			return hasSwitch && hasIf
		}

		/** 把循环尾块变成只有一条出口的简单块（便于插入 break 块）。 */
		private fun simplifyLoopEnd(mth: MethodNode, loop: LoopInfo): Boolean {
			val loopEnd = loop.end
			if (loopEnd.getSuccessors().size <= 1) {
				return false
			}
			val newLoopEnd = BlockSplitter.startNewBlock(mth, -1)
			newLoopEnd.add(AFlag.SYNTHETIC)
			newLoopEnd.add(AFlag.LOOP_END)
			val loopStart = loop.start
			BlockSplitter.replaceConnection(loopEnd, loopStart, newLoopEnd)
			BlockSplitter.connect(newLoopEnd, loopStart)
			if (DEBUG_MODS) {
				checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Simplify loop end")
			}
			return true
		}

		private fun checkLoops(mth: MethodNode, block: BlockNode): Boolean {
			if (!block.contains(AFlag.LOOP_START)) {
				return false
			}
			val loops = block.getAll(AType.LOOP)
			val loopsCount = loops.size
			if (loopsCount == 0) {
				return false
			}
			for (loop in loops) {
				if (insertBlocksForBreak(mth, loop)) {
					return true
				}
			}
			if (loopsCount > 1 && splitLoops(mth, block, loops)) {
				return true
			}
			if (loopsCount == 1) {
				val loop = loops[0]
				return insertBlocksForContinue(mth, loop) ||
					insertPreHeader(mth, loop) ||
					simplifyLoopEnd(mth, loop)
			}
			return false
		}

		/** 在循环头之前插入一个简单的前置头块（pre-header）。 */
		private fun insertPreHeader(mth: MethodNode, loop: LoopInfo): Boolean {
			val start = loop.start
			val preds = start.getPredecessors()
			val predsCount = preds.size - 1 // 不计算回边
			if (predsCount == 1) {
				return false
			}
			if (predsCount == 0) {
				if (!start.contains(AFlag.MTH_ENTER_BLOCK)) {
					mth.addWarnComment("Unexpected block without predecessors: $start")
				}
				val newEnterBlock = BlockSplitter.startNewBlock(mth, -1)
				newEnterBlock.add(AFlag.SYNTHETIC)
				newEnterBlock.add(AFlag.MTH_ENTER_BLOCK)
				mth.enterBlock = newEnterBlock
				start.remove(AFlag.MTH_ENTER_BLOCK)
				BlockSplitter.connect(newEnterBlock, start)
			} else {
				// 多个前驱
				val preHeader = BlockSplitter.startNewBlock(mth, -1)
				preHeader.add(AFlag.SYNTHETIC)
				val loopEnd = loop.end
				for (pred in ArrayList(preds)) {
					if (pred !== loopEnd) {
						BlockSplitter.replaceConnection(pred, start, preHeader)
					}
				}
				BlockSplitter.connect(preHeader, start)
			}
			if (DEBUG_MODS) {
				checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Insert loop pre header")
			}
			return true
		}

		/** 为可能的 break 插入额外块。 */
		private fun insertBlocksForBreak(mth: MethodNode, loop: LoopInfo): Boolean {
			var change = false
			val edges = loop.exitEdges
			if (edges.isNotEmpty()) {
				for (edge in edges) {
					val target = edge.target
					val source = edge.source
					if (!target.contains(AFlag.SYNTHETIC) && !source.contains(AFlag.SYNTHETIC)) {
						BlockSplitter.insertBlockBetween(mth, source, target)
						change = true
					}
				}
			}
			if (DEBUG_MODS && change) {
				checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Insert loop break blocks")
			}
			return change
		}

		/** 为可能的 continue 插入额外块。 */
		private fun insertBlocksForContinue(mth: MethodNode, loop: LoopInfo): Boolean {
			val loopEnd = loop.end
			var change = false
			val preds = loopEnd.getPredecessors()
			if (preds.size > 1) {
				for (pred in ArrayList(preds)) {
					if (!pred.contains(AFlag.SYNTHETIC)) {
						BlockSplitter.insertBlockBetween(mth, pred, loopEnd)
						change = true
					}
				}
			}
			if (DEBUG_MODS && change) {
				checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Insert loop continue block")
			}
			return change
		}

		private fun splitLoops(mth: MethodNode, block: BlockNode, loops: List<LoopInfo>): Boolean {
			var oneHeader = true
			for (loop in loops) {
				if (loop.start !== block) {
					oneHeader = false
					break
				}
			}
			if (!oneHeader) {
				return false
			}
			// 多个回边连到同一个循环头 -> 增加一个额外块
			val newLoopEnd = BlockSplitter.startNewBlock(mth, block.startOffset)
			newLoopEnd.add(AFlag.SYNTHETIC)
			BlockSplitter.connect(newLoopEnd, block)
			for (la in loops) {
				BlockSplitter.replaceConnection(la.end, block, newLoopEnd)
			}
			if (DEBUG_MODS) {
				checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Split loops")
			}
			return true
		}

		private fun splitExitBlocks(mth: MethodNode): Boolean {
			var changed = false
			for (preExitBlock in mth.preExitBlocks) {
				if (splitReturn(mth, preExitBlock)) {
					changed = true
				} else if (splitThrow(mth, preExitBlock)) {
					changed = true
				}
			}
			if (changed) {
				updateExitBlockConnections(mth)
				if (DEBUG_MODS) {
					checkNotNull(mth.get(DebugModAttr.TYPE)).addEvent("Split exit block")
				}
			}
			return changed
		}

		private fun updateExitBlockConnections(mth: MethodNode) {
			val exitBlock = checkNotNull(mth.exitBlock)
			BlockSplitter.removePredecessors(exitBlock)
			for (block in checkNotNull(mth.basicBlocks)) {
				if (block !== exitBlock &&
					block.getSuccessors().isEmpty() &&
					!block.contains(AFlag.REMOVE)
				) {
					BlockSplitter.connect(block, exitBlock)
				}
			}
		}

		/** 当 return 块有多个前驱时，为每个前驱拆分出独立的 return 块。 */
		private fun splitReturn(mth: MethodNode, returnBlock: BlockNode): Boolean {
			if (returnBlock.contains(AFlag.SYNTHETIC) ||
				returnBlock.contains(AFlag.ORIG_RETURN) ||
				returnBlock.contains(AType.EXC_HANDLER)
			) {
				return false
			}
			val preds = returnBlock.getPredecessors()
			if (preds.size < 2) {
				return false
			}
			val returnInsn = BlockUtils.getLastInsn(returnBlock) ?: return false
			if (returnInsn.argsCount == 1 &&
				returnBlock.getInstructions().size == 1 &&
				!isArgAssignInPred(preds, returnInsn.getArg(0))
			) {
				return false
			}

			var first = true
			for (pred in ArrayList(preds)) {
				if (first) {
					returnBlock.add(AFlag.ORIG_RETURN)
					first = false
				} else {
					val newRetBlock = BlockSplitter.startNewBlock(mth, -1)
					newRetBlock.add(AFlag.SYNTHETIC)
					newRetBlock.add(AFlag.RETURN)
					for (oldInsn in returnBlock.getInstructions()) {
						val copyInsn = oldInsn.copyWithoutSsa()
						copyInsn.add(AFlag.SYNTHETIC)
						newRetBlock.instructions.add(copyInsn)
					}
					BlockSplitter.replaceConnection(pred, returnBlock, newRetBlock)
				}
			}
			return true
		}

		private fun splitThrow(mth: MethodNode, exitBlock: BlockNode): Boolean {
			if (exitBlock.contains(AFlag.IGNORE_THROW_SPLIT)) {
				return false
			}
			val preds = exitBlock.getPredecessors()
			if (preds.size < 2) {
				return false
			}
			val throwInsn = BlockUtils.getLastInsn(exitBlock)
			if (throwInsn == null || throwInsn.type != InsnType.THROW) {
				return false
			}
			// 只为多个异常处理器拆分
			// 向上遍历前驱直到异常处理器
			val handlersMap = HashMap<BlockNode, ExcHandlerAttr>(preds.size)
			val handlers = HashSet<BlockNode>(preds.size)
			for (pred in preds) {
				BlockUtils.visitPredecessorsUntil(mth, pred) { block ->
					val excHandlerAttr = block.get(AType.EXC_HANDLER)
					if (excHandlerAttr == null) {
						return@visitPredecessorsUntil false
					}
					val correctHandler = excHandlerAttr.handler.blocks.contains(block)
					if (correctHandler && isArgAssignInPred(Collections.singletonList(block), throwInsn.getArg(0))) {
						handlersMap[pred] = excHandlerAttr
						handlers.add(block)
					}
					correctHandler
				}
			}
			if (handlers.size == 1) {
				exitBlock.add(AFlag.IGNORE_THROW_SPLIT)
				return false
			}

			var first = true
			for (pred in ArrayList(preds)) {
				if (first) {
					first = false
				} else {
					val newThrowBlock = BlockSplitter.startNewBlock(mth, -1)
					newThrowBlock.add(AFlag.SYNTHETIC)
					for (oldInsn in exitBlock.getInstructions()) {
						val copyInsn = oldInsn.copyWithoutSsa()
						copyInsn.add(AFlag.SYNTHETIC)
						newThrowBlock.instructions.add(copyInsn)
					}
					newThrowBlock.copyAttributesFrom(exitBlock)
					val excHandlerAttr = handlersMap[pred]
					if (excHandlerAttr != null) {
						excHandlerAttr.handler.addBlock(newThrowBlock)
					}
					BlockSplitter.replaceConnection(pred, exitBlock, newThrowBlock)
				}
			}
			return true
		}

		private fun isArgAssignInPred(preds: List<BlockNode>, arg: InsnArg): Boolean {
			if (arg.isRegister) {
				val regNum = (arg as RegisterArg).regNum
				for (pred in preds) {
					for (insnNode in pred.getInstructions()) {
						val result = insnNode.getResult()
						if (result != null && result.regNum == regNum) {
							return true
						}
					}
				}
			}
			return false
		}

		fun removeMarkedBlocks(mth: MethodNode) {
			val removed = (checkNotNull(mth.basicBlocks) as MutableList<BlockNode>).removeIf { block ->
				if (block.contains(AFlag.REMOVE)) {
					if (!block.getPredecessors().isEmpty() || !block.getSuccessors().isEmpty()) {
						LOG.warn("Block {} not deleted, method: {}", block, mth)
					} else {
						val tryBlockAttr = block.get(AType.TRY_BLOCK)
						if (tryBlockAttr != null) {
							tryBlockAttr.removeBlock(block)
						}
						return@removeIf true
					}
				}
				false
			}
			if (removed) {
				mth.updateBlockPositions()
			}
		}

		private fun removeUnreachableBlocks(mth: MethodNode) {
			val toRemove = LinkedHashSet<BlockNode>()
			for (block in checkNotNull(mth.basicBlocks)) {
				computeUnreachableFromBlock(toRemove, block, mth)
			}
			removeFromMethod(toRemove, mth)
		}

		fun removeUnreachableBlock(blockToRemove: BlockNode, mth: MethodNode) {
			val toRemove = LinkedHashSet<BlockNode>()
			computeUnreachableFromBlock(toRemove, blockToRemove, mth)
			removeFromMethod(toRemove, mth)
		}

		private fun computeUnreachableFromBlock(toRemove: MutableSet<BlockNode>, block: BlockNode, mth: MethodNode) {
			if (block.getPredecessors().isEmpty() && block !== mth.enterBlock) {
				BlockSplitter.collectSuccessors(block, checkNotNull(mth.enterBlock), toRemove)
			}
		}

		private fun removeFromMethod(toRemove: MutableSet<BlockNode>, mth: MethodNode) {
			if (toRemove.isEmpty()) {
				return
			}

			var notEmptyBlocks = 0L
			var insnsCount = 0
			for (block in toRemove) {
				val size = block.getInstructions().size
				if (size != 0) {
					notEmptyBlocks++
					insnsCount += size
				}
			}
			if (notEmptyBlocks != 0L) {
				mth.addWarnComment("Unreachable blocks removed: $notEmptyBlocks, instructions: $insnsCount")
			}

			for (block in toRemove) {
				BlockSplitter.detachBlock(block)
			}
			(checkNotNull(mth.basicBlocks) as MutableList<BlockNode>).removeAll(toRemove)
			mth.updateBlockPositions()
		}

		private fun clearBlocksState(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				block.remove(AType.LOOP)
				block.remove(AFlag.LOOP_START)
				block.remove(AFlag.LOOP_END)
				block.doms = null
				block.idom = null
				block.domFrontier = null
				(block.getDominatesOn() as MutableList<BlockNode>).clear()
			}
		}
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() || checkNotNull(mth.basicBlocks).isEmpty()) {
			return
		}
		processBlocksTree(mth)
	}

	private fun processBlocksTree(mth: MethodNode) {
		removeUnreachableBlocks(mth)

		computeDominators(mth)
		if (independentBlockTreeMod(mth)) {
			checkForUnreachableBlocks(mth)
			computeDominators(mth)
		}
		if (FixMultiEntryLoops.process(mth)) {
			computeDominators(mth)
		}
		updateCleanSuccessors(mth)

		val blocksCount = checkNotNull(mth.basicBlocks).size
		val modLimit = maxOf(100, blocksCount)
		if (DEBUG_MODS) {
			mth.addAttr(DebugModAttr())
		}
		var i = 0
		while (modifyBlocksTree(mth)) {
			computeDominators(mth)
			if (i++ > modLimit) {
				mth.addWarn("CFG modification limit reached, blocks count: $blocksCount")
				break
			}
		}
		if (DEBUG_MODS && i != 0) {
			val stats = "CFG modifications count: " + i +
				", blocks count: " + blocksCount + '\n' +
				checkNotNull(mth.get(DebugModAttr.TYPE)).formatStats() + '\n'
			mth.addDebugComment(stats)
			LOG.debug("Method: {}\n{}", mth, stats)
			mth.remove(DebugModAttr.TYPE)
		}
		checkForUnreachableBlocks(mth)

		DominatorTree.computeDominanceFrontier(mth)
		registerLoops(mth)
		processNestedLoops(mth)

		PostDominatorTree.compute(mth)

		updateCleanSuccessors(mth)
	}

	private class DebugModAttr : IJadxAttribute {
		private val statMap = HashMap<String, Int>()

		fun addEvent(name: String) {
			statMap.merge(name, 1) { a, b -> Integer.sum(a, b) }
		}

		fun formatStats(): String {
			val sb = StringBuilder()
			var first = true
			for ((key, value) in statMap.entries) {
				if (!first) {
					sb.append('\n')
				}
				sb.append(' ').append(key).append(": ").append(value)
				first = false
			}
			return sb.toString()
		}

		override val attrType: IJadxAttrType<DebugModAttr> get() = TYPE

		companion object {
			@JvmField
			val TYPE: IJadxAttrType<DebugModAttr> = IJadxAttrType.create("DebugModAttr")
		}
	}
}
