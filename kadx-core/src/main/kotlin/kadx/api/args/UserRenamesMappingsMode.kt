package kadx.api.args

/**
 * 用户重命名映射（user renames mappings）的读写模式。
 *
 * 公共 API：kadx-cli / kadx-gui 通过该枚举配置用户重命名的保存策略，常量名与顺序保持不变。
 */
enum class UserRenamesMappingsMode {

	/**
	 * 只读取，用户手动保存（默认）。
	 */
	READ,

	/**
	 * 读取，并在每次修改后自动保存。
	 */
	READ_AND_AUTOSAVE_EVERY_CHANGE,

	/**
	 * 读取，并在退出程序或关闭项目之前自动保存。
	 */
	READ_AND_AUTOSAVE_BEFORE_CLOSING,

	/**
	 * 既不加载也不保存。
	 */
	IGNORE,

	;

	/** 是否需要读取用户映射文件（除 [IGNORE] 外都需要）。 */
	fun shouldRead(): Boolean = this != IGNORE

	/** 是否需要在修改后自动保存（仅两种 AUTOSAVE 模式）。 */
	fun shouldWrite(): Boolean = this == READ_AND_AUTOSAVE_EVERY_CHANGE || this == READ_AND_AUTOSAVE_BEFORE_CLOSING

	companion object {
		/**
		 * 返回默认模式。
		 *
		 * 用 `@JvmStatic` 保持 Java 调用方写法 `UserRenamesMappingsMode.getDefault()` 不变。
		 */
		@JvmStatic
		fun getDefault(): UserRenamesMappingsMode = READ
	}
}
