package kadx.api

/**
 * 反编译代码中注释的输出级别。
 *
 * 级别按“信息量从小到大”排列：`NONE` 表示完全不输出注释，越靠后的级别包含越多的调试/提示信息。
 * 调用方通过 [filter] 判断某条注释在当前限制下是否应当输出。
 *
 * 这是对外公共 API（kadx-cli / kadx-gui / 插件都会使用），枚举常量名与顺序必须保持不变。
 */
enum class CommentsLevel {
	NONE,
	USER_ONLY,
	ERROR,
	WARN,
	INFO,
	DEBUG,
	;

	/**
	 * 判断当前级别是否应当被保留（即不超过限制级别 [limit]）。
	 *
	 * 因为枚举声明顺序就是“详细程度递增”，所以直接比较 [ordinal] 即可：
	 * 序号越小越“轻”，序号越大越“啰嗦”。例如 `DEBUG.filter(INFO)` 为 false（DEBUG 比 INFO 更啰嗦）。
	 *
	 * @param limit 允许输出的最高详细程度
	 * @return 当前级别在限制范围内返回 true
	 */
	fun filter(limit: CommentsLevel): Boolean = this.ordinal <= limit.ordinal
}
