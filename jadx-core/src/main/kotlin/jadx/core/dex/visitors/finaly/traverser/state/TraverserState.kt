package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.GlobalTraverserSourceState
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockTraverserHandler

/**
 * finally 遍历状态机的抽象基类。
 *
 * **意图**：在两张子图（“finally”子图与“候选”子图）之间反向比较块与指令时，
 * 每一侧都有一个当前状态。状态决定了下一步该做什么：
 * 是否还需要收集块信息、是否已经可以比较指令、是否到达终止条件。
 * 具体状态由子类实现（NewBlock / NoBlock / AwaitingInsnCompare / Terminal ...）。
 *
 * **Kotlin 转换说明**：
 * - 保持 `abstract class`，可覆写方法全部保留（Java 子类/调用方依赖）；
 * - [ComparisonState] 作为嵌套枚举，Java 仍可写 `TraverserState.ComparisonState.READY_TO_COMPARE`；
 * - 私有构造参数 `comparatorState` + 显式 `getComparatorState()`，避免属性访问器与
 *   原 Java 方法签名冲突，同时 JVM 表面不变。
 */
abstract class TraverserState(val comparatorState: TraverserActivePathState) {

	/**
	 * 两侧状态的“就绪程度”：
	 * - [NOT_READY]：还没收集够信息，需要先执行 handler；
	 * - [AWAITING_OPTIONAL_PREDECESSOR_MERGE]：等待可选的前驱合并；
	 * - [READY_TO_COMPARE]：可以比较指令了。
	 */
	enum class ComparisonState {
		NOT_READY,
		AWAITING_OPTIONAL_PREDECESSOR_MERGE,
		READY_TO_COMPARE,
	}

	/** 返回该状态下一步应执行的处理器（可能为 null，例如终止状态）。 */
	abstract fun getNextHandler(): AbstractBlockTraverserHandler?

	abstract fun getCompareState(): ComparisonState

	abstract fun isTerminal(): Boolean

	/** 底层中心性状态；部分状态（如终止状态）不支持，返回 null。 */
	protected abstract fun getUnderlyingCentralityState(): CentralityState?

	/** 底层块游标信息；部分状态不支持，返回 null。 */
	protected abstract fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo?

	/** 深拷贝本状态，并绑定到新的活动路径状态。 */
	protected abstract fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState

	final override fun toString(): String = toString(0)

	fun duplicate(comparatorState: TraverserActivePathState): TraverserState = duplicateInternalState(comparatorState)

	/** 缩进友好的多行调试输出，用于日志排查。 */
	fun toString(indentAmount: Int): String {
		val baseIndent = " ".repeat(indentAmount)
		val secondIndent = " ".repeat(indentAmount + 2)

		val sb = StringBuilder(baseIndent)
		sb.append(javaClass.simpleName)
		sb.append(' ')
		if (isTerminal()) {
			sb.append("TERMINAL ")
		}
		sb.append(" {")
		sb.append(System.lineSeparator())

		sb.append(secondIndent)
		sb.append("centrality: ")
		val centralityState = getUnderlyingCentralityState()
		if (centralityState == null) {
			sb.append("none")
		} else {
			sb.append(centralityState)
		}
		sb.append(System.lineSeparator())

		sb.append(secondIndent)
		sb.append(getCompareState())
		sb.append(System.lineSeparator())

		sb.append(secondIndent)
		val blockInsnInfo = getBlockInsnInfo()
		if (blockInsnInfo != null) {
			sb.append(blockInsnInfo.toString(secondIndent))
		} else {
			sb.append("NO ACTIVE BLOCK")
		}
		sb.append(System.lineSeparator())

		sb.append(baseIndent)
		sb.append("}")
		return sb.toString()
	}

	val centralityState: CentralityState get() {
		val underlying = getUnderlyingCentralityState()
		if (underlying == null) {
			throw UnsupportedOperationException("Centrality state is not supported for " + javaClass.name)
		}
		return underlying
	}

	fun getBlockInsnInfo(): TraverserBlockInfo? = getUnderlyingBlockInsnInfo()

	val globalState: GlobalTraverserSourceState get() = comparatorState.getGlobalStateFor(this)
}
