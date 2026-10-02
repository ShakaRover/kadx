package jadx.core.dex.visitors.regions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.regions.maker.ExcHandlersRegionMaker
import jadx.core.dex.visitors.regions.maker.RegionMaker
import jadx.core.dex.visitors.regions.maker.SynchronizedRegionMaker
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor

/**
 * 区域构建主入口：把基本块图“打包”成结构化区域树。
 *
 * **算法意图**：方法体在 CFG 层是一堆基本块，无法直接生成 Java 源码。
 * 本访问器调用 [RegionMaker] 从入口块出发，按深度优先顺序识别出
 * if / loop / switch / synchronized 等结构并构建区域；随后：
 * 1. [ExcHandlersRegionMaker] 收集异常处理器区域；
 * 2. 处理需要强制内联的赋值（[AFlag.FORCE_ASSIGN_INLINE]）；
 * 3. [ProcessTryCatchRegions] 把 try 体提取成独立区域；
 * 4. [PostProcessRegions] 合并循环条件、插入 switch break、补边指令；
 * 5. [CleanRegions] 清掉空区域；
 * 6. 若是 `synchronized` 方法，移除最外层的 monitor 块。
 *
 * Kotlin 转换说明：原 Java 的 `stream().flatMap().anyMatch()` 改为普通双重循环，
 * 避免在热路径上创建额外的迭代器/流对象（SOP 规则 5）。
 */
@JadxVisitor(
	name = "RegionMakerVisitor",
	desc = "Pack blocks into regions for code generation",
)
class RegionMakerVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() || checkNotNull(mth.getBasicBlocks()).isEmpty()) {
			return
		}
		val rm = RegionMaker(mth)
		mth.region = rm.makeMthRegion()
		if (!mth.isNoExceptionHandlers()) {
			ExcHandlersRegionMaker(mth, rm).process()
		}
		processForceInlineInsns(mth)
		ProcessTryCatchRegions.process(mth)
		PostProcessRegions.process(mth)
		CleanRegions.process(mth)
		if (mth.accessFlags.isSynchronized()) {
			SynchronizedRegionMaker.removeSynchronized(mth)
		}
	}

	/** 若存在“强制赋值内联”标记，则运行一次代码收缩（shrink） */
	private fun processForceInlineInsns(mth: MethodNode) {
		var needShrink = false
		for (block in checkNotNull(mth.getBasicBlocks())) {
			for (insn in block.getInstructions()) {
				if (insn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
					needShrink = true
					break
				}
			}
			if (needShrink) {
				break
			}
		}
		if (needShrink) {
			CodeShrinkVisitor.shrinkMethod(mth)
		}
	}

	override fun getName(): String = "RegionMakerVisitor"
}
