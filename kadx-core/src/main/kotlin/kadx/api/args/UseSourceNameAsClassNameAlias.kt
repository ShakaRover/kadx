package kadx.api.args

import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * 是否使用源文件名（SourceFile）作为类名别名。
 *
 * 公共 API：kadx-cli / kadx-gui 通过该枚举配置重命名策略，常量名与顺序保持不变。
 */
enum class UseSourceNameAsClassNameAlias {
	/** 总是使用源文件名作为别名。 */
	ALWAYS,

	/** 仅当源文件名“更好”时才使用。 */
	IF_BETTER,

	/** 从不使用（默认）。 */
	NEVER,

	;

	/**
	 * 把枚举转换为布尔值（已废弃，请直接使用枚举本身）。
	 *
	 * `IF_BETTER` 对应 true，`NEVER` 对应 false；`ALWAYS` 无法用布尔值表达，因此抛异常。
	 */
	@Deprecated("Use UseSourceNameAsClassNameAlias directly.")
	fun toBoolean(): Boolean = when (this) {
		IF_BETTER -> true
		NEVER -> false
		ALWAYS -> throw KadxRuntimeException("No match between $this and boolean")
	}

	companion object {
		/**
		 * 返回默认策略。
		 *
		 * 用 `@JvmStatic` 保持 Java 调用方写法 `UseSourceNameAsClassNameAlias.getDefault()` 不变。
		 */
		@JvmStatic
		fun getDefault(): UseSourceNameAsClassNameAlias = NEVER

		/**
		 * 由布尔值创建枚举（已废弃，请直接使用枚举本身）。
		 *
		 * `true` -> [IF_BETTER]，`false` -> [NEVER]。
		 */
		@Deprecated("Use UseSourceNameAsClassNameAlias directly.")
		@JvmStatic
		fun create(useSourceNameAsAlias: Boolean): UseSourceNameAsClassNameAlias = if (useSourceNameAsAlias) IF_BETTER else NEVER
	}
}
