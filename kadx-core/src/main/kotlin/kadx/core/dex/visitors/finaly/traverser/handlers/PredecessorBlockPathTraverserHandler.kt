package kadx.core.dex.visitors.finaly.traverser.handlers

import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.visitors.finaly.traverser.state.ISourceBlockState
import kadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import kadx.core.dex.visitors.finaly.traverser.state.TraverserState
import kadx.core.dex.visitors.finaly.traverser.visitors.AbstractBlockTraverserVisitor
import kadx.core.dex.visitors.finaly.traverser.visitors.PredecessorBlockTraverserVisitor
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.concurrent.atomic.AtomicReference

/**
 * 前驱块处理器：从“源块”出发，沿控制流图向上查找作用域内的前驱块。
 *
 * **泛型约束**：`T extends TraverserState & ISourceBlockState` 表示状态既要是遍历状态，
 * 又要能提供源块（[ISourceBlockState.getSourceBlock]）。Kotlin 用 `where` 子句表达交叉类型上界。
 *
 * **Kotlin 转换说明**：
 * - 两个构造器分别接收“状态”和“状态的原子引用”，字段 [sourceBlockState] 在两者中都要初始化；
 * - 原 Java 为 final 类，Kotlin 默认 final。
 */
class PredecessorBlockPathTraverserHandler<T> : AbstractBlockPathTraverserHandler
	where T : TraverserState, T : ISourceBlockState {

	private val sourceBlockState: ISourceBlockState

	constructor(initialState: T) : super(initialState) {
		this.sourceBlockState = initialState
	}

	constructor(initialStateRef: AtomicReference<T>) : super(initialStateRef) {
		this.sourceBlockState = initialStateRef.get()
	}

	override fun handle() {
		val baseState: TraverserState = state
		val comparator: TraverserActivePathState = baseState.comparatorState
		val stateRef = comparator.getReferenceForState(baseState)
			?: throw KadxRuntimeException("Orphaned traverser state")
		val sourceBlock: BlockNode = sourceBlockState.getSourceBlock()
		val visitor: AbstractBlockTraverserVisitor = PredecessorBlockTraverserVisitor(baseState)
		val nextState: TraverserState = visitor.visit(sourceBlock)

		stateRef.set(nextState)
	}
}
