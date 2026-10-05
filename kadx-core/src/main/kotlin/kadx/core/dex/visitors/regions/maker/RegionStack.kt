package kadx.core.dex.visitors.regions.maker

import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.exceptions.KadxOverflowException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.Deque

private const val DEBUG = false
private const val REGIONS_STACK_LIMIT = 1000

/**
 * 区域构建过程中使用的“区域栈”。
 *
 * **算法意图**：[RegionMaker] 递归构建区域树时，需要记录当前所在的区域以及
 * “退出边界块”（exits）——即当前区域处理到哪些块就应停止。栈的每一帧保存一份
 * exits 的拷贝，因此进入/离开区域时状态能正确恢复。
 *
 * Kotlin 转换说明：本类只在 maker 包内使用，声明为 `internal`；
 * [State] 的 exits 需要拷贝（[State.copyWith]），保证各层互不影响。
 */
public class RegionStack(mth: MethodNode) {

	/** 栈帧：当前区域及其退出边界块集合 */
	private class State {
		var exits: MutableSet<BlockNode> = HashSet()
		var region: IRegion? = null

		constructor()

		private constructor(c: State, region: IRegion) {
			this.exits = HashSet(c.exits)
			this.region = region
		}

		fun copyWith(region: IRegion): State = State(this, region)

		override fun toString(): String = "Region: " + region + ", exits: " + exits
	}

	private val stack: Deque<State> = ArrayDeque()
	private var curState: State = State()

	init {
		if (DEBUG) {
			LOG.debug("New RegionStack: {}", mth)
		}
	}

	fun push(region: IRegion) {
		stack.push(curState)
		if (stack.size > REGIONS_STACK_LIMIT) {
			throw KadxOverflowException("Regions stack size limit reached")
		}
		curState = curState.copyWith(region)
		if (DEBUG) {
			LOG.debug("Stack push: {}: {}", size(), curState)
		}
	}

	fun pop() {
		curState = stack.pop()
		if (DEBUG) {
			LOG.debug("Stack  pop: {}: {}", size(), curState)
		}
	}

	/**
	 * 为当前栈帧添加一个退出边界块。
	 *
	 * @param exit 边界块，null 会被忽略
	 */
	fun addExit(exit: BlockNode?) {
		if (exit != null) {
			curState.exits.add(exit)
		}
	}

	fun addExits(exits: Collection<BlockNode>) {
		for (exit in exits) {
			addExit(exit)
		}
	}

	fun removeExit(exit: BlockNode?) {
		if (exit != null) {
			curState.exits.remove(exit)
		}
	}

	fun containsExit(exit: BlockNode): Boolean = curState.exits.contains(exit)

	fun getExits(): Iterable<BlockNode> = curState.exits

	fun peekRegion(): IRegion? = curState.region

	fun size(): Int = stack.size

	fun clear(): RegionStack {
		stack.clear()
		curState = State()
		return this
	}

	override fun toString(): String = "Region stack size: " + size() + ", last: " + curState

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(RegionStack::class.java)
	}
}
