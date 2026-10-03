package jadx.core.dex.visitors.regions

import jadx.api.ICodeWriter
import jadx.api.impl.SimpleCodeWriter
import jadx.core.Consts
import jadx.core.codegen.InsnGen
import jadx.core.codegen.MethodGen
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.exceptions.CodegenException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 区域树一致性自检。
 *
 * **算法意图**：反编译最怕“丢代码”。本访问器做两类检查：
 * 1. 统计所有出现在区域树里的基本块，与方法的全部基本块对比；若有不属于任何区域
 *    且非空、非移除标记的块，就给出“代码丢失”告警，并附上该块的反编译片段；
 * 2. 检查循环头块的条件是否合法（单条指令，或显式允许多条）。
 *
 * Kotlin 转换说明：原 Java 的匿名内部类改为 Kotlin `object : AbstractRegionVisitor()`；
 * 静态辅助方法放入 companion。
 */
class CheckRegions : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() ||
			mth.region == null ||
			checkNotNull(mth.basicBlocks).isEmpty() ||
			mth.contains(AType.JADX_ERROR)
		) {
			return
		}

		// 检查所有基本块是否都被包含进区域树
		val blocksInRegions: MutableSet<BlockNode> = HashSet()
		DepthRegionTraversal.traverse(
			mth,
			object : AbstractRegionVisitor() {
				override fun processBlock(mth: MethodNode, container: IBlock) {
					if (container !is BlockNode) {
						return
					}
					if (blocksInRegions.add(container)) {
						return
					}
					if (Consts.DEBUG_RESTRUCTURE &&
						LOG.isDebugEnabled &&
						!container.contains(AFlag.RETURN) &&
						!container.contains(AFlag.REMOVE) &&
						!container.contains(AFlag.SYNTHETIC) &&
						!container.instructions.isEmpty()
					) {
						LOG.debug("Duplicated block: {} - {}", mth, container)
					}
				}
			},
		)
		if (checkNotNull(mth.basicBlocks).size != blocksInRegions.size) {
			for (block in checkNotNull(mth.basicBlocks)) {
				if (!blocksInRegions.contains(block) &&
					!block.instructions.isEmpty() &&
					!block.contains(AFlag.ADDED_TO_REGION) &&
					!block.contains(AFlag.DONT_GENERATE) &&
					!block.contains(AFlag.REMOVE)
				) {
					val blockCode = getBlockInsnStr(mth, block).replace("*/", "*\\/")
					mth.addWarn("Code restructure failed: missing block: " + block + ", code lost:" + blockCode)
				}
			}
		}

		// 检查循环条件是否合法
		DepthRegionTraversal.traverse(
			mth,
			object : AbstractRegionVisitor() {
				override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
					if (region is LoopRegion) {
						val loopHeader = region.header
						if (loopHeader != null &&
							!loopHeader.contains(AFlag.ALLOW_MULTIPLE_INSNS_LOOP_COND) &&
							loopHeader.instructions.size != 1
						) {
							mth.addWarn("Incorrect condition in loop: " + loopHeader)
						}
					}
					return true
				}
			},
		)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CheckRegions::class.java)

		/** 把一个基本块的指令临时反编译成文本，用于告警信息 */
		private fun getBlockInsnStr(mth: MethodNode, block: IBlock): String {
			val code: ICodeWriter = SimpleCodeWriter()
			code.incIndent()
			code.newLine()
			val mg = MethodGen.getFallbackMethodGen(mth)
			val ig = InsnGen(mg, true)
			for (insn in block.instructions) {
				try {
					ig.makeInsn(insn, code)
				} catch (e: CodegenException) {
					// 忽略无法反编译的指令，仅用于调试
				}
			}
			code.newLine()
			return code.codeStr
		}
	}
}
