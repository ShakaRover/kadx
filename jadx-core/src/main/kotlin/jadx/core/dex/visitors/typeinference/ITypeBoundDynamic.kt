package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType

/**
 * “动态”类型边界：允许使用 [TypeUpdateInfo] 中已请求但尚未真正应用的类型，
 * 从而给出更精确的限制。
 *
 * **为什么需要它**：泛型方法/字段的返回类型依赖实例的具体泛型实参，
 * 而实例类型本身可能还在推导队列里，因此边界需要在每次查询时重新计算。
 */
interface ITypeBoundDynamic : ITypeBound {

	/**
	 * 当 [TypeUpdateInfo] 可用时，用本方法替代 [ITypeBound.getType]。
	 *
	 * @param updateInfo 当前这一轮类型更新的中间状态
	 */
	fun getType(updateInfo: TypeUpdateInfo): ArgType
}
