package jadx.core.dex.attributes.nodes

import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.Edge
import jadx.core.utils.BlockUtils

/**
 * 循环信息：描述 CFG 中的一条自然循环（natural loop）。
 *
 * **结构**：
 * - [start]：循环头（header）基本块，循环入口；
 * - [end]：循环出口块（回边指向的“汇合点”）；
 * - [loopBlocks]：属于该循环的所有基本块；
 * - [id]：循环编号（由 [jadx.core.dex.nodes.MethodNode.registerLoop] 按注册顺序赋值）；
 * - [parentLoop]：外层循环（可空，用于 `break`/`continue` 跨层判定）。
 *
 * **Kotlin 转换说明**：原 Java 字段名与 getter 名一一对应（如 `loopBlocks` ↔ `getLoopBlocks`），
 * 因此这里直接声明为 Kotlin 属性，既保留 JVM getter 名，又让上游 Kotlin 调用点的
 * `loop.start` / `loop.loopBlocks` / `loop.id` 等合成属性访问继续可用。
 * [parentLoop] 原 Java 允许为 null，如实标注为可空类型。
 */
class LoopInfo(
	val start: BlockNode,
	val end: BlockNode,
	val loopBlocks: Set<BlockNode>,
) {

	/** 循环编号，由方法注册循环时赋值 */
	var id: Int = 0

	/** 外层循环；最外层循环为 null */
	var parentLoop: LoopInfo? = null

	/**
	 * 返回所有“出口边”的源基本块。
	 *
	 * 出口边定义：源块在循环内、目标块在循环外。注意这里故意使用 [BlockNode.getSuccessors]
	 * 而非 clean successors，以便把异常处理器等特殊边也算进来（与原 Java 行为一致）。
	 */
	fun getExitNodes(): Set<BlockNode> {
		val nodes = HashSet<BlockNode>()
		val blocks = loopBlocks
		for (block in blocks) {
			// exit: successor node not from this loop, (don't change to getCleanSuccessors)
			for (s in block.getSuccessors()) {
				if (!blocks.contains(s) && !s.contains(AType.EXC_HANDLER)) {
					nodes.add(block)
				}
			}
		}
		return nodes
	}

	/**
	 * 返回所有出口边（[Edge] 列表）。
	 *
	 * 与 [getExitNodes] 的区别：这里返回“块到块”的边对象，并且用
	 * [BlockUtils.isExceptionHandlerPath] 排除异常处理路径。同样使用完整 successors
	 * 以包含回边。
	 */
	fun getExitEdges(): List<Edge> {
		val edges = ArrayList<Edge>()
		val blocks = loopBlocks
		for (block in blocks) {
			for (s in block.getSuccessors()) { // don't use clean successors to include loop back edges
				if (!blocks.contains(s) && !BlockUtils.isExceptionHandlerPath(s)) {
					edges.add(Edge(block, s))
				}
			}
		}
		return edges
	}

	/** 取循环的前置头块（pre-header）：循环头的前驱中不是循环自身的那个块 */
	fun getPreHeader(): BlockNode = BlockUtils.selectOther(end, start.getPredecessors())

	/**
	 * 判断 [searchLoop] 是否为本循环的某一层祖先循环。
	 *
	 * 从当前循环沿 [parentLoop] 链向上查找，找到即返回 true。
	 * 注意：本方法不把“自己”视为自己的祖先。
	 */
	fun hasParent(searchLoop: LoopInfo): Boolean {
		var parent = parentLoop
		while (true) {
			if (parent == null) {
				return false
			}
			if (parent === searchLoop) {
				return true
			}
			parent = parent.parentLoop
		}
	}

	override fun toString(): String = "LOOP:$id: $start->$end"
}
