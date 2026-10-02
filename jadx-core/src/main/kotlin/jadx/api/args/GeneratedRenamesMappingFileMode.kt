package jadx.api.args

/**
 * 生成的重命名映射文件（mapping 文件）的读写模式。
 *
 * 公共 API：jadx-cli / jadx-gui 通过命令行/设置界面选择该模式，枚举常量名与顺序保持不变。
 */
enum class GeneratedRenamesMappingFileMode {

	/**
	 * 找到就加载，不保存（默认）。
	 */
	READ,

	/**
	 * 找到就加载；只有文件不存在时才保存（不覆盖已有文件）。
	 */
	READ_OR_SAVE,

	/**
	 * 不加载，总是覆盖保存。
	 */
	OVERWRITE,

	/**
	 * 既不加载也不保存。
	 */
	IGNORE,

	;

	/**
	 * 是否需要读取映射文件。
	 * `READ` / `READ_OR_SAVE` 会读取，其余模式忽略。
	 */
	fun shouldRead(): Boolean = this == READ || this == READ_OR_SAVE

	/**
	 * 是否需要写出映射文件。
	 * `READ_OR_SAVE` / `OVERWRITE` 会写出，其余模式忽略。
	 */
	fun shouldWrite(): Boolean = this == READ_OR_SAVE || this == OVERWRITE

	companion object {
		/**
		 * 返回默认模式。
		 *
		 * 用 `@JvmStatic` 保持 Java 调用方写法 `GeneratedRenamesMappingFileMode.getDefault()` 不变。
		 */
		@JvmStatic
		fun getDefault(): GeneratedRenamesMappingFileMode = READ
	}
}
