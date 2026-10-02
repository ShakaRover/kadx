package jadx.api.data

/**
 * 注释风格：定义一条用户注释在生成代码时用哪种前后缀包裹。
 *
 * **做什么**：每种风格保存三个片段——起始串 [start]、换行后的前缀 [onNewLine]、结束串 [end]。
 * 生成多行注释时，除第一行外每行都以 [onNewLine] 开头。
 *
 * **为什么保留显式 `getXxx()` 函数**：这是公共 API，jadx-gui / 插件以 Java 或 Kotlin 调用，
 * 显式函数保证 Java 调用方 `style.getStart()` 零改动（Kotlin 属性也会生成同名 getter，
 * 但本仓库公共 API 统一用显式函数，见迁移规范 9.7）。
 */
enum class CommentStyle(
	// 起始片段（写在注释最前面）
	private val start: String,
	// 多行注释中，换行之后每一行的前缀
	private val onNewLine: String,
	// 结束片段（写在注释最后）
	private val end: String,
) {

	/**
	 * 行注释，形如：
	 * <pre>
	 * // comment
	 * </pre>
	 */
	LINE("// ", "// ", ""),

	/**
	 * 块注释（每个星号独占一行的常见形式），形如：
	 * <pre>
	 * &#47;*
	 *  * comment
	 *  *&#47;
	 * </pre>
	 */
	BLOCK("/*\n * ", " * ", "\n */"),

	/**
	 * 单行块注释，形如：
	 * <pre>
	 * &#47;* comment *&#47;
	 * </pre>
	 */
	BLOCK_CONDENSED("/* ", " * ", " */"),

	/**
	 * Javadoc 风格块注释，形如：
	 * <pre>
	 * &#47;**
	 *  * comment
	 *  *&#47;
	 * </pre>
	 */
	JAVADOC("/**\n * ", " * ", "\n */"),

	/**
	 * 单行 Javadoc，形如：
	 * <pre>
	 * &#47;** comment *&#47;
	 * </pre>
	 */
	JAVADOC_CONDENSED("/** ", " * ", " */"),
	;

	/** 注释起始片段。 */
	fun getStart(): String = start

	/** 多行注释换行后每行的前缀。 */
	fun getOnNewLine(): String = onNewLine

	/** 注释结束片段。 */
	fun getEnd(): String = end
}
