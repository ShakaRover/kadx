package jadx.core.dex.visitors.regions

import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnContainer
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.ListUtils
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 区域树深度优先遍历工具。
 *
 * **算法意图**：区域树是一个“容器树”（[IContainer] 可以是基本块或区域）。
 * 本类用显式栈（而不是递归）做深度优先遍历，避免深层嵌套导致栈溢出；
 * 并通过一个特殊的“离开标记”对象 [LEAVE_REGION_MARK] 在出栈时回调
 * [IRegionVisitor.leaveRegion]，保证 enter/leave 严格配对。
 *
 * 提供四种遍历方式：
 * - [traverse]：标准 enter/process/leave 遍历；
 * - [traversePartial]：找到第一个满足条件的容器就停止；
 * - [traverseIterative]：反复遍历直到访问器不再请求重跑；
 * - [traverseIncludingExcHandlers]：在迭代遍历基础上把异常处理器区域也纳入。
 *
 * Kotlin 转换说明：纯静态工具类，用 `object` + `@JvmStatic`，Java 调用方写法不变。
 */
object DepthRegionTraversal {

	/** 迭代遍历的次数上限倍数：上限 = 该倍数 × 基本块数量，防止死循环 */
	private const val ITERATIVE_LIMIT_MULTIPLIER = 5

	fun traverse(mth: MethodNode, visitor: IRegionVisitor) {
		val region = mth.region
		if (region != null) {
			traverseInternal(mth, visitor, region)
		}
	}

	fun traverse(mth: MethodNode, container: IContainer, visitor: IRegionVisitor) {
		traverseInternal(mth, visitor, container)
	}

	fun <R> traversePartial(mth: MethodNode, visitor: IRegionPartialVisitor<R>): R? {
		val region = mth.region
		if (region == null) {
			return null
		}
		return traversePartialInternal(mth, visitor, region)
	}

	fun <R> traversePartial(mth: MethodNode, container: IContainer, visitor: IRegionPartialVisitor<R>): R? = traversePartialInternal(mth, visitor, container)

	fun traverseIterative(mth: MethodNode, visitor: IRegionIterativeVisitor) {
		var repeat: Boolean
		var k = 0
		val blocksCount = checkNotNull(mth.basicBlocks).size
		val limit = ITERATIVE_LIMIT_MULTIPLIER * blocksCount
		val region = mth.region
		if (region == null) {
			return
		}
		do {
			repeat = traverseIterativeStepInternal(mth, visitor, region)
			if (k++ > limit) {
				throw JadxRuntimeException(
					"Iterative traversal limit reached: " +
						"limit: " + limit + ", visitor: " + visitor.javaClass.name +
						", blocks count: " + blocksCount,
				)
			}
		} while (repeat)
	}

	fun traverseIncludingExcHandlers(mth: MethodNode, visitor: IRegionIterativeVisitor) {
		var repeat: Boolean
		var k = 0
		val blocksCount = checkNotNull(mth.basicBlocks).size
		val limit = ITERATIVE_LIMIT_MULTIPLIER * blocksCount
		val region = mth.region
		if (region == null) {
			return
		}
		do {
			repeat = traverseIterativeStepInternal(mth, visitor, region)
			if (!repeat) {
				for (h in mth.getExceptionHandlers()) {
					val handlerRegion = h.getHandlerRegion()
					if (handlerRegion != null) {
						repeat = traverseIterativeStepInternal(mth, visitor, handlerRegion)
						if (repeat) {
							break
						}
					}
				}
			}
			if (k++ > limit) {
				throw JadxRuntimeException(
					"Iterative traversal limit reached: " +
						"limit: " + limit + ", visitor: " + visitor.javaClass.name +
						", blocks count: " + blocksCount,
				)
			}
		} while (repeat)
	}

	/**
	 * “离开区域”的哨兵标记：入栈时遇到它，说明应调用 leaveRegion。
	 * 用引用比较（`===`）判断，绝不能改成值比较。
	 */
	private val LEAVE_REGION_MARK: IContainer = InsnContainer(emptyList())

	private fun traverseInternal(mth: MethodNode, visitor: IRegionVisitor, startContainer: IContainer) {
		val stack: MutableList<IContainer> = ArrayList()
		val regionLeaveStack: MutableList<IRegion> = ArrayList()
		stack.add(startContainer)
		while (true) {
			val current = ListUtils.removeLast(stack)
			if (current == null) {
				return
			}
			if (current === LEAVE_REGION_MARK) {
				val region = ListUtils.removeLast(regionLeaveStack)
				visitor.leaveRegion(mth, checkNotNull(region))
			} else if (current is IBlock) {
				visitor.processBlock(mth, current)
			} else if (current is IRegion) {
				val visitRegion = visitor.enterRegion(mth, current)
				stack.add(LEAVE_REGION_MARK)
				regionLeaveStack.add(current)
				if (visitRegion) {
					addSubBlocksToStack(stack, current)
				}
			}
		}
	}

	private fun <R> traversePartialInternal(mth: MethodNode, visitor: IRegionPartialVisitor<R>, startContainer: IContainer): R? {
		val stack: MutableList<IContainer> = ArrayList()
		stack.add(startContainer)
		while (true) {
			val current = ListUtils.removeLast(stack)
			if (current == null) {
				return null
			}
			val result = visitor.visit(mth, current)
			if (result != null) {
				return result
			}
			if (current is IRegion) {
				addSubBlocksToStack(stack, current)
			}
		}
	}

	/** 逆序压栈，保证出栈顺序与区域原始顺序一致 */
	private fun addSubBlocksToStack(stack: MutableList<IContainer>, region: IRegion) {
		val subBlocks = region.subBlocks
		for (i in subBlocks.size - 1 downTo 0) {
			stack.add(subBlocks[i])
		}
	}

	private fun traverseIterativeStepInternal(mth: MethodNode, visitor: IRegionIterativeVisitor, startRegion: IRegion): Boolean {
		val stack: MutableList<IRegion> = ArrayList()
		stack.add(startRegion)
		while (true) {
			val region = ListUtils.removeLast(stack)
			if (region == null) {
				return false
			}
			if (visitor.visitRegion(mth, region)) {
				return true
			}
			val subBlocks = region.subBlocks
			// 逆序压栈，保持访问顺序
			for (i in subBlocks.size - 1 downTo 0) {
				val subBlock = subBlocks[i]
				if (subBlock is IRegion) {
					stack.add(subBlock)
				}
			}
		}
	}
}
