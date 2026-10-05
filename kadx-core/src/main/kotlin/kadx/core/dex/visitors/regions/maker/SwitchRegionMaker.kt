package kadx.core.dex.visitors.regions.maker

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.nodes.RegionRefAttr
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.SwitchInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnContainer
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.Region
import kadx.core.dex.regions.SynchronizedRegion
import kadx.core.dex.regions.SwitchRegion
import kadx.core.dex.visitors.regions.AbstractRegionVisitor
import kadx.core.dex.visitors.regions.DepthRegionTraversal
import kadx.core.utils.BlockUtils
import kadx.core.utils.ListUtils
import kadx.core.utils.RegionUtils
import kadx.core.utils.Utils
import kadx.core.utils.blocks.BlockSet
import java.util.BitSet

/**
 * 构建 `switch` 区域。
 *
 * **算法意图**：把 switch 指令的各个 case 目标块整理成 [SwitchRegion.CaseInfo]：
 * 1. 按目标块聚合 key（多个 key 可指向同一块）；
 * 2. [calcSwitchOut] 计算 switch 的“汇合出口块”（各 case 支配边界的并集）；
 * 3. [addCases] 为每个 case 构建区域，并识别 fall-through；
 * 4. [removeEmptyCases] 去掉空的 default / filler case。
 * [insertBreaks] 在区域收尾阶段为各 case 补 break。
 *
 * Kotlin 转换说明：`getSubBlocks()` 原地修改通过向下转型；对象比较用 `===`；
 * fall-through 的出口块可能为空，故 [fallThroughCases] 值类型可空。
 */
class SwitchRegionMaker(private val mth: MethodNode, private val regionMaker: RegionMaker) {

	fun process(currentRegion: IRegion, block: BlockNode, insn: SwitchInsn, stack: RegionStack): BlockNode? {
		// 把 case 块映射到 key 列表
		val len = insn.getTargets().size
		val blocksMap: MutableMap<BlockNode, MutableList<Any>> = LinkedHashMap(len)
		val targetBlocksArr = checkNotNull(insn.getTargetBlocks())
		for (i in 0 until len) {
			val keys = blocksMap.computeIfAbsent(checkNotNull(targetBlocksArr[i])) { ArrayList(2) }
			keys.add(checkNotNull(insn.getKey(i)))
		}
		val defCase = insn.getDefTargetBlock()
		if (defCase != null) {
			val keys = blocksMap.computeIfAbsent(defCase) { ArrayList(1) }
			keys.add(SwitchRegion.DEFAULT_CASE_KEY)
		}

		val sw = SwitchRegion(currentRegion, block)
		insn.addAttr(RegionRefAttr(sw))
		@Suppress("UNCHECKED_CAST")
		(currentRegion.subBlocks as MutableList<IContainer>).add(sw)
		stack.push(sw)

		val out = calcSwitchOut(block, insn, stack)
		stack.addExit(out)

		addCases(sw, out, stack, blocksMap)
		removeEmptyCases(insn, sw, defCase, out)

		stack.pop()
		return out
	}

	private fun calcSwitchOut(block: BlockNode, insn: SwitchInsn, stack: RegionStack): BlockNode? {
		// case 块支配边界的并集（无 fallthrough、无 return 时有效）
		val outs = BlockUtils.newBlocksBitSet(mth)
		for (s in checkNotNull(block.cleanSuccessors)) {
			if (s.contains(AFlag.LOOP_END)) {
				// 循环末尾的支配边界是循环头，忽略
				continue
			}
			outs.or(checkNotNull(s.domFrontier))
		}
		outs.clear(block.pos)
		outs.clear(checkNotNull(mth.exitBlock).pos)

		var out: BlockNode? = null
		if (outs.cardinality() == 1) {
			// 单一出口
			out = BlockUtils.bitSetToOneBlock(mth, outs)
		} else {
			// 多个出口：某个 case 可能有 return/continue/fallthrough
			val loop = mth.getLoopForBlock(block)
			if (loop != null) {
				outs.andNot(checkNotNull(loop.start.postDoms))
				outs.andNot(checkNotNull(loop.end.postDoms))
				val loopEnd = loop.end
				if (outs.cardinality() == 2 && outs.get(loopEnd.pos)) {
					// 为通向循环末尾的 case 插入 continue
					val outList = ArrayList(BlockUtils.bitSetToBlocks(mth, outs))
					outList.remove(loopEnd)
					val possibleOut = Utils.getOne(outList)
					if (possibleOut != null && insertContinueInSwitch(block, possibleOut, loopEnd)) {
						outs.clear(loopEnd.pos)
						out = possibleOut
					}
				}
				if (outs.isEmpty()) {
					// 所有出口都在 switch 内，保留以便跳出循环
					return mth.exitBlock
				}
			}
			if (out == null) {
				val imPostDom = checkNotNull(block.iPostDom)
				if (outs.get(imPostDom.pos)) {
					out = imPostDom
				} else {
					outs.andNot(checkNotNull(block.postDoms))
					out = BlockUtils.bitSetToOneBlock(mth, outs)
				}
			}
		}
		if (out != null && mth.isPreExitBlock(out)) {
			// 把 return/throw 包含进 case 块
			out = mth.exitBlock
		}
		val imPostDom = checkNotNull(block.iPostDom)
		if (out == null && imPostDom === mth.exitBlock) {
			// 所有出口都在 switch 内，检查是否所有 return 相同
			return allSameReturns(stack)
		}
		if (imPostDom === insn.getDefTargetBlock() &&
			checkNotNull(block.cleanSuccessors).contains(imPostDom) &&
			checkNotNull(block.domFrontier).get(imPostDom.pos)
		) {
			// 为空的 default 块添加出口以便停止
			stack.addExit(imPostDom)
		}
		if (out == null) {
			mth.addWarnComment("Failed to find 'out' block for switch in " + block + ". Please report as an issue.")
			// 回退方案：大多数情况下可用
			out = block.iPostDom
		}
		if (out != null && regionMaker.isProcessed(out)) {
			// out 已被处理，防止死循环；回退到直接后支配节点
			mth.addWarnComment("Switch 'out' block " + out + " for " + block + " already processed. Defaulting to fallback option.")
			out = block.iPostDom
		}
		return out
	}

	private fun allSameReturns(stack: RegionStack): BlockNode {
		val exitBlock = checkNotNull(mth.exitBlock)
		val preds = exitBlock.predecessors
		val count = preds.size
		if (count == 1) {
			return preds[0]
		}
		if (mth.returnType == ArgType.VOID) {
			for (pred in preds) {
				val insn = BlockUtils.getLastInsn(pred)
				if (insn == null || insn.type != InsnType.RETURN) {
					return exitBlock
				}
			}
		} else {
			val returnArgs = ArrayList<InsnArg>()
			for (pred in preds) {
				val insn = BlockUtils.getLastInsn(pred)
				if (insn == null || insn.type != InsnType.RETURN) {
					return exitBlock
				}
				returnArgs.add(insn.getArg(0))
			}
			val firstArg = returnArgs[0]
			if (firstArg.isRegister) {
				val reg = firstArg as RegisterArg
				for (i in 1 until count) {
					val arg = returnArgs[i]
					if (!arg.isRegister || !(arg as RegisterArg).sameCodeVar(reg)) {
						return exitBlock
					}
				}
			} else {
				for (i in 1 until count) {
					val arg = returnArgs[i]
					if (arg != firstArg) {
						return exitBlock
					}
				}
			}
		}
		// 确认
		stack.addExits(preds)
		// 忽略其他 return
		for (i in 1 until count) {
			val block = preds[i]
			block.add(AFlag.REMOVE)
			block.add(AFlag.ADDED_TO_REGION)
		}
		return preds[0]
	}

	private fun addCases(sw: SwitchRegion, out: BlockNode?, stack: RegionStack, blocksMap0: MutableMap<BlockNode, MutableList<Any>>) {
		var blocksMap = blocksMap0
		val fallThroughCases: MutableMap<BlockNode, BlockNode?> = LinkedHashMap()
		if (out != null) {
			// 识别 fall-through case
			val caseBlocks = BlockUtils.blocksToBitSet(mth, blocksMap.keys)
			caseBlocks.clear(out.pos)
			for (successor in sw.header.successors) {
				val df = successor.domFrontier
				if (df != null && df.intersects(caseBlocks)) {
					val fallThroughBlock = getOneIntersectionBlock(out, caseBlocks, df)
					fallThroughCases[successor] = fallThroughBlock
				}
			}
			// 检查 fall-through 顺序
			if (fallThroughCases.isNotEmpty() && isBadCasesOrder(blocksMap, fallThroughCases)) {
				val newBlocksMap = reOrderSwitchCases(blocksMap, fallThroughCases)
				if (isBadCasesOrder(newBlocksMap, fallThroughCases)) {
					mth.addWarnComment("Can't fix incorrect switch cases order, some code will duplicate")
					fallThroughCases.clear()
				} else {
					blocksMap = newBlocksMap
				}
			}
		}
		for ((caseBlock, keysList) in blocksMap) {
			val caseRegion: Region
			if (stack.containsExit(caseBlock)) {
				caseRegion = Region(stack.peekRegion())
			} else {
				val next = fallThroughCases[caseBlock]
				stack.addExit(next)
				caseRegion = regionMaker.makeRegion(caseBlock)
				stack.removeExit(next)
				if (next != null) {
					next.add(AFlag.FALL_THROUGH)
					caseRegion.add(AFlag.FALL_THROUGH)
				}
			}
			sw.addCase(keysList, caseRegion)
		}
	}

	private fun getOneIntersectionBlock(out: BlockNode, caseBlocks: BitSet, fallThroughSet: BitSet): BlockNode? {
		val caseExits = BlockUtils.copyBlocksBitSet(mth, fallThroughSet)
		caseExits.clear(out.pos)
		caseExits.and(caseBlocks)
		return BlockUtils.bitSetToOneBlock(mth, caseExits)
	}

	/**
	 * 删除空的 case 块：
	 * 1. 单个 default case；
	 * 2. 若 switch 是 packed 且 default 为空，则删除填充 case。
	 */
	private fun removeEmptyCases(insn: SwitchInsn, sw: SwitchRegion, defCase: BlockNode?, outBlock: BlockNode?) {
		val defaultCaseIsEmpty: Boolean
		if (defCase == null) {
			defaultCaseIsEmpty = true
		} else {
			defaultCaseIsEmpty = sw.cases.any { c ->
				c.keys.contains(SwitchRegion.DEFAULT_CASE_KEY) && canRemove(c.container, outBlock)
			}
		}
		if (defaultCaseIsEmpty) {
			val cases = ArrayList(sw.cases)
			for (caseInfo in cases) {
				if (canRemove(caseInfo.container, outBlock)) {
					val keys = caseInfo.keys
					if (keys.contains(SwitchRegion.DEFAULT_CASE_KEY) || insn.isPacked()) {
						// 删除 case，并把所有块标记为不生成
						RegionUtils.addToAll(mth, caseInfo.container, AFlag.DONT_GENERATE)
						sw.cases.remove(caseInfo)
					}
				}
			}
		}
	}

	/** 检查容器为空，且其中所有路径到 outBlock 之间都没有内容 */
	private fun canRemove(container: IContainer, outBlock: BlockNode?): Boolean {
		if (RegionUtils.isEmpty(container)) {
			if (container is BlockNode) {
				return BlockUtils.followEmptyPath(container) === outBlock
			} else if (container is IRegion) {
				for (subBlock in container.subBlocks) {
					if (!canRemove(subBlock, outBlock)) {
						return false
					}
				}
				return true
			}
		}
		return false
	}

	private fun isBadCasesOrder(blocksMap: Map<BlockNode, MutableList<Any>>, fallThroughCases: Map<BlockNode, BlockNode?>): Boolean {
		var nextCaseBlock: BlockNode? = null
		for (caseBlock in blocksMap.keys) {
			if (nextCaseBlock != null && caseBlock != nextCaseBlock) {
				return true
			}
			nextCaseBlock = fallThroughCases[caseBlock]
		}
		return nextCaseBlock != null
	}

	private fun reOrderSwitchCases(
		blocksMap: Map<BlockNode, MutableList<Any>>,
		fallThroughCases: Map<BlockNode, BlockNode?>,
	): MutableMap<BlockNode, MutableList<Any>> {
		val list = ArrayList<BlockNode>(blocksMap.size)
		list.addAll(blocksMap.keys)
		list.sortWith(
			Comparator { a, b ->
				val nextA = fallThroughCases[a]
				when {
					nextA != null -> if (b == nextA) -1 else 0
					a == fallThroughCases[b] -> 1
					else -> 0
				}
			},
		)

		val newBlocksMap: MutableMap<BlockNode, MutableList<Any>> = LinkedHashMap(blocksMap.size)
		for (key in list) {
			newBlocksMap[key] = checkNotNull(blocksMap[key])
		}
		return newBlocksMap
	}
	private fun insertContinueInSwitch(switchBlock: BlockNode, switchOut: BlockNode, loopEnd: BlockNode): Boolean {
		var inserted = false
		for (caseBlock in checkNotNull(switchBlock.cleanSuccessors)) {
			if (checkNotNull(caseBlock.domFrontier).get(loopEnd.pos) && caseBlock !== switchOut) {
				// 在当前后继到 loop end 的路径上搜索前驱
				val list: MutableSet<BlockNode> = HashSet(BlockUtils.collectBlocksDominatedBy(mth, caseBlock, caseBlock))
				if (list.contains(switchOut) || switchOut.predecessors.any { list.contains(it) }) {
					// 不需要 continue
				} else {
					for (p in loopEnd.predecessors) {
						if (list.contains(p) || p === caseBlock) {
							if (p.isSynthetic) {
								p.instructions.add(InsnNode(InsnType.CONTINUE, 0))
								inserted = true
							}
							break
						}
					}
				}
			}
		}
		return inserted
	}

	companion object {
		/**
		 * 为 case 区域的每个退出边添加 break。
		 * break 的优化（提取公共、删除不可达）由 [kadx.core.dex.visitors.regions.SwitchBreakVisitor] 完成。
		 */
		private fun insertBreaksForCase(mth: MethodNode, switchRegion: SwitchRegion, caseContainer: IContainer) {
			val caseBlocks = BlockSet(mth)
			RegionUtils.visitBlockNodes(mth, caseContainer) { caseBlocks.add(it) }
			DepthRegionTraversal.traverse(
				mth,
				caseContainer,
				object : AbstractRegionVisitor() {
					override fun leaveRegion(mth: MethodNode, region: IRegion) {
						var insertBreak = false
						if (region === caseContainer) {
							// 顶层区域
							insertBreak = true
						} else {
							val lastContainer = ListUtils.last(region.subBlocks)
							if (lastContainer is BlockNode) {
								for (successor in lastContainer.successors) {
									if (!caseBlocks.contains(successor)) {
										insertBreak = true
										break
									}
								}
							}
						}
						if (insertBreak && canAppendBreak(region)) {
							appendBreakContainer(region, buildBreakContainer(switchRegion))
						}
					}
				},
			)
		}

		/**
		 * 为 switch 区域的所有 case 插入 break。
		 * 在 [kadx.core.dex.visitors.regions.PostProcessRegions] 中 try/catch 包裹之后执行，
		 * 以便处理所有块。
		 */
		fun insertBreaks(mth: MethodNode, sw: SwitchRegion) {
			for (caseInfo in sw.cases) {
				insertBreaksForCase(mth, sw, caseInfo.container)
			}
		}

		fun canAppendBreak(region: IRegion): Boolean = !region.contains(AFlag.FALL_THROUGH) && !RegionUtils.hasExitBlock(region)

		/**
		 * 把 break 容器追加到 [region] 的子块列表。
		 *
		 * 只有底层列表稳定可变的区域（[Region] 及委托其列表的 [SynchronizedRegion]）才能真正追加；
		 * 其余区域（IfRegion/LoopRegion/SwitchRegion/TryCatchRegion）的 `subBlocks` 是每次
		 * 重建的临时列表或不可变视图——上游在此处对 SwitchRegion 会直接抛
		 * UnsupportedOperationException 导致整个方法反编译失败，对其余区域则是静默丢失。
		 * 这里统一为：可持久化才追加，否则跳过（后续 SwitchBreakVisitor 会补全缺失的 break）。
		 */
		internal fun appendBreakContainer(region: IRegion, container: IContainer) {
			when (region) {
				is Region -> region.add(container)
				is SynchronizedRegion -> region.region.add(container)
				else -> {}
			}
		}

		fun buildBreakContainer(switchRegion: SwitchRegion): InsnContainer {
			val breakInsn = InsnNode(InsnType.BREAK, 0)
			breakInsn.add(AFlag.SYNTHETIC)
			breakInsn.addAttr(RegionRefAttr(switchRegion))
			return InsnContainer(breakInsn)
		}
	}
}
