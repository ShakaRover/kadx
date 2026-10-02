package jadx.core.dex.visitors.finaly.traverser.handlers

import jadx.core.dex.visitors.finaly.traverser.TraverserException
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import java.util.concurrent.atomic.AtomicReference

/**
 * “块路径”处理器的抽象基类。
 *
 * **作用**：遍历器在搜索重复的 `finally` 指令时，需要决定“块”这一层该如何继续搜索。
 * 本类处理器不直接产生新路径，而是**原地**更新某一个遍历状态（通过 [stateRef] 原子引用），
 * 因此 [process] 不返回结果，只负责把状态推进。
 *
 * **Kotlin 转换说明**：
 * - 字段是 `AtomicReference<? extends TraverserState>`，Kotlin 用 `AtomicReference<out TraverserState>`
 *   表达同一个通配符（JVM 擦除后完全一致）；
 * - [handle] 声明了受检异常 [TraverserException]，保留 `@Throws` 以便 Java 调用方仍需 catch；
 * - 原 Java 的 `process()`/`getState()`/`getStateReference()` 都是 `final`，Kotlin 方法默认 final，语义不变。
 */
abstract class AbstractBlockPathTraverserHandler : AbstractBlockTraverserHandler {

	private val stateRef: AtomicReference<out TraverserState>

	constructor(initialState: TraverserState) {
		this.stateRef = AtomicReference(initialState)
	}

	constructor(initialStateRef: AtomicReference<out TraverserState>) {
		this.stateRef = initialStateRef
	}

	/** 子类实现真正的处理逻辑：读取当前状态并写回推进后的状态。 */
	@Throws(TraverserException::class)
	protected abstract fun handle()

	/** 对外统一入口：执行一次处理。 */
	@Throws(TraverserException::class)
	fun process() {
		handle()
	}

	/** 读取当前状态（原子引用里的值）。 */
	fun getState(): TraverserState = stateRef.get()

	/** 暴露原子引用本身，供调用方原地替换状态。 */
	fun getStateReference(): AtomicReference<out TraverserState> = stateRef
}
