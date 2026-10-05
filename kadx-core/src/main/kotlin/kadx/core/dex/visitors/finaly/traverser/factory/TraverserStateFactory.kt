package kadx.core.dex.visitors.finaly.traverser.factory

import kadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import kadx.core.dex.visitors.finaly.traverser.state.TraverserState

/**
 * 遍历状态工厂基类。
 *
 * **作用**：把“如何构造某个 [TraverserState]”从状态本身解耦出来。
 * 具体工厂（如 NewBlock / NoBlock / Terminal / IdentifiedScope 等状态的内部工厂）
 * 负责在需要时基于一条活动路径状态生成对应的新状态。
 *
 * **Kotlin 转换说明**：原 Java 是 `public abstract class`（并非接口），
 * 因此这里保持抽象类，Kotlin/Java 工厂都以 `extends` / `:` 方式继承；
 * [generateInternalState] 为 `protected abstract`，跨包子类（state 包内的工厂）仍可覆写。
 */
abstract class TraverserStateFactory<T : TraverserState> {

	/** 由子类实现：根据活动路径状态构造一个具体遍历状态。 */
	protected abstract fun generateInternalState(state: TraverserActivePathState): T

	/** 对外统一入口：生成状态（原 Java 为 final，Kotlin 默认 final）。 */
	fun generateState(state: TraverserActivePathState): T = generateInternalState(state)
}
