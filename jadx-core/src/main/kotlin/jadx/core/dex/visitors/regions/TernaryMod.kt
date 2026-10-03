package jadx.core.dex.visitors.regions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.conditions.IfRegion
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.utils.InsnList
import jadx.core.utils.InsnRemover

/**
 * 把 `if (c) { r = a; } else { r = b; }` 还原成三目运算 `r = c ? a : b`。
 *
 * **算法意图**：Java 编译器常把三目表达式展开成 if/else 两个赋值分支，
 * 两个分支的结果最终通过一个 phi 合并。本访问器识别这种模式，把两个分支的
 * 赋值指令内联进一条合成的 [TernaryInsn]，从而让反编译输出更简洁。
 *
 * 同时支持“只有一个 then 分支”的写法：`if (c) { r = a; }` → `r = c ? a : r`。
 *
 * Kotlin 转换说明：
 * - 原 Java 静态方法 `process` 放入 companion + `@JvmStatic`；
 * - 原 Java 的 `==` 引用比较改为 `===`（phi 节点比较必须用身份语义）；
 * - 原 Java 空 else 分支改为 `forceInline` 布尔判断，避免空块。
 */
class TernaryMod private constructor() :
	AbstractRegionVisitor(),
	IRegionIterativeVisitor {

	override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
		if (processRegion(mth, region)) {
			mth.add(AFlag.REQUEST_CODE_SHRINK)
		}
		return true
	}

	override fun visitRegion(mth: MethodNode, region: IRegion): Boolean {
		if (processRegion(mth, region)) {
			CodeShrinkVisitor.shrinkMethod(mth)
			return true
		}
		return false
	}

	companion object {
		private val INSTANCE: TernaryMod = TernaryMod()

		fun process(mth: MethodNode) {
			var changed = false
			// 一轮遍历中尽可能多地转换三目节点
			DepthRegionTraversal.traverse(mth, INSTANCE)
			if (mth.contains(AFlag.REQUEST_CODE_SHRINK)) {
				CodeShrinkVisitor.shrinkMethod(mth)
				changed = true
			}
			if (changed && mth.isConstructor()) {
				// 构造器内（super 调用之前）需要激进模式：每次变更后都收缩并重跑
				DepthRegionTraversal.traverseIterative(mth, INSTANCE)
			}
		}

		private fun processRegion(mth: MethodNode, region: IRegion): Boolean {
			if (region is IfRegion) {
				return makeTernaryInsn(mth, region)
			}
			return false
		}

		private fun makeTernaryInsn(mth: MethodNode, ifRegion: IfRegion): Boolean {
			if (ifRegion.contains(AFlag.ELSE_IF_CHAIN)) {
				return false
			}
			val thenRegion = ifRegion.thenRegion ?: return false
			val elseRegion = ifRegion.elseRegion
			if (elseRegion == null) {
				return processOneBranchTernary(mth, ifRegion)
			}
			val tb = getTernaryInsnBlock(thenRegion) ?: return false
			val eb = getTernaryInsnBlock(elseRegion) ?: return false
			if (tb.contains(AFlag.DUPLICATED) || eb.contains(AFlag.DUPLICATED)) {
				return false
			}
			val conditionBlocks = ifRegion.conditionBlocks
			if (conditionBlocks.isEmpty()) {
				return false
			}

			val header = conditionBlocks[0]
			val thenInsn = tb.instructions[0]
			val elseInsn = eb.instructions[0]

			if (!verifyLineHints(mth, thenInsn, elseInsn)) {
				return false
			}

			val thenResArg = thenInsn.result
			val elseResArg = elseInsn.result
			if (thenResArg != null && elseResArg != null) {
				val thenPhi = checkNotNull(thenResArg.sVar).onlyOneUseInPhi
				val elsePhi = checkNotNull(elseResArg.sVar).onlyOneUseInPhi
				if (thenPhi == null || thenPhi !== elsePhi) {
					return false
				}
				if (!checkNotNull(ifRegion.parent).replaceSubBlock(ifRegion, header)) {
					return false
				}
				InsnList.remove(tb, thenInsn)
				InsnList.remove(eb, elseInsn)

				val resArg: RegisterArg
				if (thenPhi.argsCount == 2) {
					resArg = checkNotNull(thenPhi.result)
				} else {
					resArg = thenResArg
					thenPhi.removeArg(elseResArg)
				}
				val thenArg = InsnArg.wrapInsnIntoArg(thenInsn.copyWithoutResult())
				val elseArg = InsnArg.wrapInsnIntoArg(elseInsn.copyWithoutResult())
				val ternInsn = TernaryInsn(checkNotNull(ifRegion.condition), resArg.duplicate(), thenArg, elseArg)
				val branchLine = maxOf(thenInsn.getSourceLine(), elseInsn.getSourceLine())
				ternInsn.setSourceLine(maxOf(ifRegion.sourceLine, branchLine))

				InsnRemover.unbindInsn(mth, thenInsn)
				InsnRemover.unbindInsn(mth, elseInsn)
				ternInsn.rebindArgs()
				if (thenPhi.argsCount == 0) {
					InsnRemover.unbindResult(mth, thenPhi)
					InsnRemover.delistPhi(mth, thenPhi)
				}

				// 用三目指令替换原来的 if 指令
				header.instructions.clear()
				header.instructions.add(ternInsn)

				clearConditionBlocks(conditionBlocks, header)
				return true
			}

			if (!mth.isVoidReturn() &&
				thenInsn.type == InsnType.RETURN &&
				elseInsn.type == InsnType.RETURN
			) {
				val thenArg = thenInsn.getArg(0)
				val elseArg = elseInsn.getArg(0)
				if (thenArg.isLiteral != elseArg.isLiteral) {
					// 只有一个分支是字面量，无法合成三目
					return false
				}

				if (!checkNotNull(ifRegion.parent).replaceSubBlock(ifRegion, header)) {
					return false
				}
				InsnList.remove(tb, thenInsn)
				InsnList.remove(eb, elseInsn)
				tb.remove(AFlag.RETURN)
				eb.remove(AFlag.RETURN)

				val ternInsn = TernaryInsn(checkNotNull(ifRegion.condition), null, thenArg, elseArg)
				val retInsn = InsnNode(InsnType.RETURN, 1)
				val arg = InsnArg.wrapInsnIntoArg(ternInsn)
				arg.setType(thenArg.getType())
				retInsn.addArg(arg)

				header.instructions.clear()
				retInsn.rebindArgs()
				header.instructions.add(retInsn)
				header.add(AFlag.RETURN)

				clearConditionBlocks(conditionBlocks, header)
				return true
			}
			return false
		}
		private fun verifyLineHints(mth: MethodNode, thenInsn: InsnNode, elseInsn: InsnNode): Boolean {
			if (mth.contains(AFlag.USE_LINES_HINTS) &&
				thenInsn.getSourceLine() != elseInsn.getSourceLine()
			) {
				if (thenInsn.getSourceLine() != 0 && elseInsn.getSourceLine() != 0) {
					// 有时源码行号不准确
					return checkLineStats(thenInsn, elseInsn)
				}
				// 默认不生成嵌套三目
				return !containsTernary(thenInsn) && !containsTernary(elseInsn)
			}
			return true
		}

		/** 清空条件相关的块（保留 header），避免重复生成 */
		private fun clearConditionBlocks(conditionBlocks: List<BlockNode>, header: BlockNode) {
			for (block in conditionBlocks) {
				if (block !== header) {
					block.instructions.clear()
					block.add(AFlag.REMOVE)
				}
			}
		}

		/** 取出“只包含一条指令”的块，作为三目分支的候选 */
		private fun getTernaryInsnBlock(thenRegion: IContainer?): BlockNode? {
			if (thenRegion is Region) {
				val subBlocks = thenRegion.subBlocks
				if (subBlocks.size == 1) {
					val container = subBlocks[0]
					if (container is BlockNode) {
						if (container.instructions.size == 1) {
							return container
						}
					}
				}
			}
			return null
		}

		/** 递归判断指令（含内联参数）中是否已包含三目运算 */
		private fun containsTernary(insn: InsnNode): Boolean {
			if (insn.type == InsnType.TERNARY) {
				return true
			}
			for (i in 0 until insn.argsCount) {
				val arg = insn.getArg(i)
				if (arg.isInsnWrap) {
					val wrapInsn = (arg as InsnWrapArg).wrapInsn
					if (containsTernary(wrapInsn)) {
						return true
					}
				}
			}
			return false
		}

		/** 若多个参数来自同一源码行，则返回 true（用于行号提示校验） */
		private fun checkLineStats(t: InsnNode, e: InsnNode): Boolean {
			if (t.result == null || e.result == null) {
				return false
			}
			val tPhi = checkNotNull(checkNotNull(t.result).sVar).onlyOneUseInPhi
			val ePhi = checkNotNull(checkNotNull(e.result).sVar).onlyOneUseInPhi
			if (ePhi == null || tPhi !== ePhi) {
				return false
			}
			val map: MutableMap<Int, Int> = HashMap(checkNotNull(tPhi).argsCount)
			for (arg in checkNotNull(tPhi).getArguments()) {
				if (!arg.isRegister) {
					continue
				}
				val assignInsn = (arg as RegisterArg).assignInsn ?: continue
				val sourceLine = assignInsn.getSourceLine()
				if (sourceLine != 0) {
					map[sourceLine] = (map[sourceLine] ?: 0) + 1
				}
			}
			for (entry in map.entries) {
				if (entry.value >= 2) {
					return true
				}
			}
			return false
		}

		/**
		 * 处理只有 then 分支的写法：`if (c) { r = a; }` → `r = c ? a : r`。
		 * 要求 `r` 只被使用一次。
		 */
		private fun processOneBranchTernary(mth: MethodNode, ifRegion: IfRegion): Boolean {
			val thenRegion = ifRegion.thenRegion
			val block = getTernaryInsnBlock(thenRegion)
			if (block != null) {
				val insn = block.instructions[0]
				val result = insn.result
				if (result != null) {
					replaceWithTernary(mth, ifRegion, block, insn)
				}
			}
			return false
		}
		private fun replaceWithTernary(mth: MethodNode, ifRegion: IfRegion, block: BlockNode, insn: InsnNode) {
			val resArg = checkNotNull(insn.result)
			if (checkNotNull(resArg.sVar).useList.size != 1) {
				return
			}
			val phiInsn = checkNotNull(resArg.sVar).onlyOneUseInPhi ?: return
			if (phiInsn.argsCount != 2) {
				return
			}
			var otherArg: RegisterArg? = null
			for (arg in phiInsn.getArguments()) {
				if (!resArg.sameRegAndSVar(arg)) {
					otherArg = arg as RegisterArg
					break
				}
			}
			val other = otherArg ?: return
			val elseAssign = other.assignInsn
			val forceInline = mth.isConstructor() || (mth.parentClass.isEnum() && mth.methodInfo.isClassInit())
			if (!forceInline) {
				if (elseAssign != null && elseAssign.isConstInsn) {
					if (!verifyLineHints(mth, insn, elseAssign)) {
						return
					}
				} else {
					if (resArg.sameCodeVar(other)) {
						// 不要用同一变量作为 else 分支，避免 `l = (l == 0) ? 1 : l`
						return
					}
				}
			}

			// 所有检查通过
			val header = ifRegion.conditionBlocks[0]
			if (!checkNotNull(ifRegion.parent).replaceSubBlock(ifRegion, header)) {
				return
			}
			val elseArg: InsnArg
			if (elseAssign != null && elseAssign.isConstInsn) {
				// 内联常量
				elseArg = InsnArg.wrapInsnIntoArg(elseAssign.copyWithoutResult())
				val elseVar = checkNotNull(checkNotNull(elseAssign.result).sVar)
				if (elseVar.useCount == 1 && elseVar.onlyOneUseInPhi === phiInsn) {
					InsnRemover.remove(mth, elseAssign)
				}
			} else {
				elseArg = other.duplicate()
			}
			val thenArg = InsnArg.wrapInsnIntoArg(insn)
			val resultArg = checkNotNull(phiInsn.result).duplicate()
			val ternInsn = TernaryInsn(checkNotNull(ifRegion.condition), resultArg, thenArg, elseArg)
			ternInsn.simplifyCondition()

			InsnRemover.unbindAllArgs(mth, phiInsn)
			InsnRemover.delistPhi(mth, phiInsn)
			InsnRemover.unbindResult(mth, insn)
			InsnList.remove(block, insn)
			header.instructions.clear()
			ternInsn.rebindArgs()
			header.instructions.add(ternInsn)

			clearConditionBlocks(ifRegion.conditionBlocks, header)

			// 再次收缩方法
			CodeShrinkVisitor.shrinkMethod(mth)
		}
	}
}
