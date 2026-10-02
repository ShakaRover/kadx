package jadx.api

/**
 * 反编译模式：控制 jadx 生成代码的风格与还原程度。
 *
 * 这是公共 API，枚举常量名与顺序必须保持不变（jadx-cli / jadx-gui 会做 `valueOf`/`ordinal` 相关处理）。
 */
enum class DecompilationMode {
	/**
	 * 自动选择最佳选项（默认）。
	 */
	AUTO,

	/**
	 * 尽量还原代码结构（正常的 Java 代码）。
	 */
	RESTRUCTURE,

	/**
	 * 简化指令：线性代码 + goto 跳转，不做结构化还原。
	 */
	SIMPLE,

	/**
	 * 不做任何修改，直接输出原始指令。
	 */
	FALLBACK,

	;

	/**
	 * 判断当前模式是否为“特殊/非结构化”模式。
	 *
	 * `AUTO` 与 `RESTRUCTURE` 会尝试恢复正常的控制流结构，因此不是特殊模式；
	 * `SIMPLE` 与 `FALLBACK` 会输出带 goto 的线性/原始代码，属于特殊模式。
	 *
	 * 说明：原 Java 代码的 `default` 分支在枚举常量穷尽时不可能到达，Kotlin 用穷尽 `when` 表达等价语义。
	 */
	fun isSpecial(): Boolean = when (this) {
		AUTO, RESTRUCTURE -> false
		SIMPLE, FALLBACK -> true
	}
}
