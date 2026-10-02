package jadx.core.dex.visitors.typeinference

/**
 * 类型更新回调：用于处理并修改类型更新的结果。
 *
 * **算法角色**：一次类型更新可能触发一串连锁更新，回调让调用方能在
 * 每个子更新完成后检查/改写结果，或延迟到所有更新结束再决定。
 *
 * **Kotlin 转换说明**：声明为 `fun interface`，保持 SAM 语义，
 * Java 侧仍可使用 lambda 注册回调。
 */
fun interface ITypeUpdateCallback {

	/**
	 * 当一次类型更新结果被计算出来时调用。
	 *
	 * @param result 类型更新结果
	 * @return 修改后的结果；返回 null 表示保留回调、等待下一个结果
	 */
	fun updateCallback(result: TypeUpdateResult): TypeUpdateResult?
}
