package jadx.core.dex.visitors.regions.variables

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.loops.ForLoop
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.visitors.regions.TracedRegionVisitor

/**
 * 收集每个 SSA 变量的所有“使用位置”（赋值 + 读取）。
 *
 * **算法意图**：[ProcessVariables] 需要知道每个变量在区域树中的哪些位置被赋值/读取，
 * 才能决定变量声明放在哪个区域。本访问器遍历区域树，为每个基本块构造 [UsePlace]，
 * 再把块内指令的结果寄存器（赋值）和参数寄存器（读取）登记到 [VarUsage]。
 *
 * 特殊处理：`for` 循环的初始化/自增指令不在块内，需要从 [ForLoop] 类型里单独取出。
 *
 * Kotlin 转换说明：继承 [TracedRegionVisitor]；`getUsageMap` 返回 `Map<SSAVar, VarUsage>`。
 */
internal class CollectUsageRegionVisitor : TracedRegionVisitor() {

	private val args: MutableList<RegisterArg> = ArrayList()
	val usageMap: MutableMap<SSAVar?, VarUsage> = LinkedHashMap()

	override fun processBlockTraced(mth: MethodNode, block: IBlock, curRegion: IRegion) {
		val usePlace = UsePlace(curRegion, block)
		regionProcess(usePlace)
		val len = block.instructions.size
		for (i in 0 until len) {
			val insn = block.instructions[i]
			processInsn(insn, usePlace)
		}
	}

	/** `for` 循环的初始化/自增指令也算作使用位置 */
	private fun regionProcess(usePlace: UsePlace) {
		val region = usePlace.region
		if (region is LoopRegion) {
			val loopType = region.type
			if (loopType is ForLoop) {
				processInsn(loopType.initInsn, usePlace)
				processInsn(loopType.incrInsn, usePlace)
			}
		}
	}

	fun processInsn(insn: InsnNode?, usePlace: UsePlace) {
		if (insn == null) {
			return
		}
		// 结果寄存器（赋值）
		val result = insn.result
		if (result != null && result.isRegister) {
			if (!result.contains(AFlag.DONT_GENERATE)) {
				val usage = getUsage(result.sVar)
				usage.assigns.add(usePlace)
			}
		}
		// 参数寄存器（读取）
		args.clear()
		insn.getRegisterArgs(args)
		for (arg in args) {
			if (!arg.contains(AFlag.DONT_GENERATE)) {
				val usage = getUsage(arg.sVar)
				usage.uses.add(usePlace)
			}
		}
	}

	private fun getUsage(ssaVar: SSAVar?): VarUsage = usageMap.computeIfAbsent(ssaVar) { VarUsage(it) }
}
