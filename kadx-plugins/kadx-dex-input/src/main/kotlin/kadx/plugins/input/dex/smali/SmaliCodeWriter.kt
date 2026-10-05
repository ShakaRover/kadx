package kadx.plugins.input.dex.smali

/**
 * Smali 代码增量写入器：以流式 API 累积生成 smali 文本。
 *
 * **背景**：[SmaliPrinter] / [InsnFormatter] 逐行拼装 `.method` ... `.end method`
 * 文本，本类负责换行、缩进与片段追加；所有 `add*`/`startLine` 方法返回 this 支持链式调用。
 *
 * **Kotlin 转换说明**：原 Java `public static final String` → companion object 内
 * `val`，调用方 `SmaliCodeWriter.NL` 零改动。
 */
public class SmaliCodeWriter {

	public companion object {
		/** 平台相关换行符 */
		public val NL: String = System.getProperty("line.separator") ?: "\n"

		/** 单级缩进字符串（4 个空格）*/
		public val INDENT_STR: String = "    "
	}

	private val codeValue = StringBuilder()

	private var indent = 0
	private var indentStr = ""

	/** 开始新行并追加内容 */
	public fun startLine(line: String): SmaliCodeWriter {
		startLine()
		codeValue.append(line)
		return this
	}

	/** 开始新行（非空时先补换行与当前缩进）*/
	public fun startLine(): SmaliCodeWriter {
		if (codeValue.isNotEmpty()) {
			codeValue.append(NL)
			codeValue.append(indentStr)
		}
		return this
	}

	public fun add(obj: Any?): SmaliCodeWriter {
		codeValue.append(obj)
		return this
	}

	public fun add(i: Int): SmaliCodeWriter {
		codeValue.append(i)
		return this
	}

	public fun add(c: Char): SmaliCodeWriter {
		codeValue.append(c)
		return this
	}

	public fun add(str: String): SmaliCodeWriter {
		codeValue.append(str)
		return this
	}

	/** 依次追加多个参数类型（方法签名拼装用）*/
	public fun addArgs(argTypes: List<String>): SmaliCodeWriter {
		for (type in argTypes) {
			codeValue.append(type)
		}
		return this
	}

	/** 增加一级缩进 */
	public fun incIndent() {
		indent++
		buildIndent()
	}

	/** 减少一级缩进 */
	public fun decIndent() {
		indent--
		buildIndent()
	}

	private fun buildIndent() {
		val s = StringBuilder(indent * INDENT_STR.length)
		repeat(indent) {
			s.append(INDENT_STR)
		}
		indentStr = s.toString()
	}

	/** @return 已累积的完整 smali 文本 */
	public val code: String get() = codeValue.toString()
}
