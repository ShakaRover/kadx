package jadx.core.dex.visitors.finaly.traverser.handlers

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.dex.visitors.finaly.traverser.visitors.ImplicitInsnBlockTraverserVisitor
import jadx.core.dex.visitors.finaly.traverser.visitors.PathEndBlockTraverserVisitor
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.concurrent.atomic.AtomicReference

/**
 * 基础块处理器：进入一个新块后，先收集该块的“游标信息”，再决定下一步状态。
 *
 * **处理流程**（与原 Java 完全一致）：
 * 1. 取出当前块的 [TraverserBlockInfo]（没有则说明状态非法）；
 * 2. 找到该状态在活动路径中的原子引用（没有则说明状态已游离）；
 * 3. 用 [ImplicitInsnBlockTraverserVisitor] 跳过块尾的“隐式指令”（如 GOTO）；
 * 4. 用 [PathEndBlockTraverserVisitor] 处理块尾的“路径结束指令”（RETURN/THROW）；
 * 5. 把推进后的新状态写回原子引用。
 *
 * **Kotlin 转换说明**：原 Java 为非 final 类，这里保留 `open`，其 [handle] 覆写方法
 * 因此仍可被进一步覆写（保持 JVM 表面不变）。
 */
open class BaseBlockTraverserHandler : AbstractBlockPathTraverserHandler {

	constructor(initialState: TraverserState) : super(initialState)

	constructor(initialStateRef: AtomicReference<TraverserState>) : super(initialStateRef)

	override fun handle() {
		val blockInsnInfo: TraverserBlockInfo = state.getBlockInsnInfo()
			?: throw JadxRuntimeException("Expected to find block info within " + javaClass.simpleName)
		val comparator: TraverserActivePathState = state.comparatorState
		val stateRef = comparator.getReferenceForState(state)
			?: throw JadxRuntimeException("Orphaned traverser state")
		val block: BlockNode = blockInsnInfo.block
		val implicitVisitor = ImplicitInsnBlockTraverserVisitor(state)
		val stateAfterImplicit: TraverserState = implicitVisitor.visit(block)
		val pathEndVisitor = PathEndBlockTraverserVisitor(stateAfterImplicit)
		val nextState: TraverserState = pathEndVisitor.visit(block)

		stateRef.set(nextState)
	}
}
