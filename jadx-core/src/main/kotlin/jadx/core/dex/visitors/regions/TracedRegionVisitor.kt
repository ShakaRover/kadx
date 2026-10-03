package jadx.core.dex.visitors.regions

import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import java.util.ArrayDeque
import java.util.Deque

/**
 * 带“区域栈”的访问器。
 *
 * **用途**：处理基本块时，除了块本身，往往还需要知道它当前位于哪个区域里
 * （例如判断块是否在循环中、是否还有后续代码）。本类在 [enterRegion]/[leaveRegion]
 * 时维护一个区域栈，把栈顶区域作为“父区域”传给 [processBlockTraced]。
 *
 * Kotlin 转换说明：
 * - 使用 `java.util.ArrayDeque` 保持与原 Java 完全一致的栈实现（LIFO，push/pop）；
 * - [getRegionStack] 返回 `java.util.Deque`，Java 调用方（如 ReturnVisitor）可直接遍历。
 */
abstract class TracedRegionVisitor : IRegionVisitor {

	/** 当前遍历路径上的区域栈，栈顶是最近进入、尚未离开的区域 */
	val regionStack: Deque<IRegion> = ArrayDeque()

	/** 进入区域时压栈 */
	override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
		regionStack.push(region)
		return true
	}

	/**
	 * 处理基本块时，把当前栈顶区域作为父区域回调 [processBlockTraced]。
	 *
	 * 栈顶理论上不可能为空（进入区域时必压栈），这里用 [checkNotNull] 替代原 Java 的
	 * `Objects.requireNonNull`，保持“异常时立即失败”的语义。
	 */
	override fun processBlock(mth: MethodNode, block: IBlock) {
		val curRegion = checkNotNull(regionStack.peek())
		processBlockTraced(mth, block, curRegion)
	}

	/** 子类实现：处理基本块，并附带其所属父区域 */
	abstract fun processBlockTraced(mth: MethodNode, block: IBlock, parentRegion: IRegion)

	/** 离开区域时弹栈 */
	override fun leaveRegion(mth: MethodNode, region: IRegion) {
		regionStack.pop()
	}
}
