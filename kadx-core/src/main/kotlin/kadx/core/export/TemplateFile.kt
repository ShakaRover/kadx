package kadx.core.export

import kadx.core.utils.exceptions.KadxRuntimeException
import org.jetbrains.annotations.Nullable
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.HashMap

/**
 * 极简模板引擎。
 *
 * **语法**：用 `{{变量名}}` 占位，构建时替换为 [add] 注册的值。
 * 若设置了值净化器（[setValueSanitizer]），值默认会被净化；在变量名前加 `!!`
 * （即 `{{!!变量名}}`）可跳过净化，原样输出。
 *
 * **用途**：导出 Gradle 工程时，用 `build.gradle` / `settings.gradle` 等资源模板
 * 生成最终文件。
 *
 * **Kotlin 转换说明**：
 * - 静态工厂 [fromResources] 放入 `companion object`，调用方式不变；
 * - 受检异常用 `@Throws` 标注，Java 调用方的 `throws`/`catch` 语义不变；
 * - 原 Java `String.getBytes()` 使用平台默认字符集，这里显式写
 *   [Charset.defaultCharset] 以保持字节输出完全一致。
 */
class TemplateFile private constructor(
	private val templateName: String,
	private val template: InputStream,
) {

	/** 解析状态机所处的阶段。 */
	private enum class State {
		NONE,
		START,
		VARIABLE,
		END,
	}

	/** 状态机的可变状态：当前阶段、正在收集的变量名、本字符是否已被吞掉。 */
	private class ParserState {
		var state: State = State.NONE
		var curVariable: StringBuilder? = null
		var skip: Boolean = false
	}

	/** 变量名 -> 替换值。 */
	private val values = HashMap<String, String>()

	/** 可选的净化器，对非 raw 的值做转义/净化。 */
	private var valueSanitizer: ((String) -> String)? = null

	/** 注册一个变量的替换值；null 会被转成字符串 "null"（与 Java `String.valueOf` 一致）。 */
	fun add(name: String, value: Any?) {
		values[name] = value.toString()
	}

	/** 构建模板内容并返回字符串。 */
	@Throws(IOException::class)
	fun build(): String {
		ByteArrayOutputStream().use { out ->
			process(out)
			return out.toString()
		}
	}

	/** 构建模板内容并写入指定文件。 */
	@Throws(IOException::class)
	fun save(outFile: File) {
		FileOutputStream(outFile).use { out ->
			process(out)
		}
	}

	/** 设置值净化器；传 null 表示不做净化。 */
	fun setValueSanitizer(valueSanitizer: ((String) -> String)?) {
		this.valueSanitizer = valueSanitizer
	}

	/** 逐字符读取模板并写出结果；模板只能被处理一次。 */
	@Throws(IOException::class)
	private fun process(out: OutputStream) {
		if (template.available() == 0) {
			throw IOException("Template already processed")
		}
		BufferedInputStream(template).use { input ->
			val state = ParserState()
			while (true) {
				val ch = input.read()
				if (ch == -1) {
					break
				}
				val str = process(state, ch.toChar())
				if (str != null) {
					out.write(str.toByteArray(Charset.defaultCharset()))
				} else if (!state.skip) {
					out.write(ch)
				}
			}
		}
	}

	/**
	 * 状态机的单字符处理。
	 *
	 * 返回 null 表示“该字符不直接输出”（由状态机决定是否原样写出），
	 * 返回字符串表示“用该字符串替换当前字符”。
	 */
	@Nullable
	private fun process(parser: ParserState, ch: Char): String? {
		val state = parser.state
		when (ch) {
			'{' -> {
				when (state) {
					State.START -> {
						parser.state = State.VARIABLE
						parser.curVariable = StringBuilder()
					}

					else -> {
						parser.state = State.START
					}
				}
				parser.skip = true
				return null
			}

			'}' -> {
				when (state) {
					State.VARIABLE -> {
						parser.state = State.END
						parser.skip = true
						return null
					}

					State.END -> {
						parser.state = State.NONE
						val rawName = checkNotNull(parser.curVariable).toString()
						parser.curVariable = StringBuilder()
						val rawValue = rawName.startsWith("!!")
						val varName = if (rawValue) rawName.substring(2) else rawName
						return processVar(varName, rawValue)
					}

					else -> {
						// 其余状态与 Java 一样落到函数末尾（skip=false, return null）
					}
				}
			}

			else -> {
				when (state) {
					State.VARIABLE -> {
						checkNotNull(parser.curVariable).append(ch)
						parser.skip = true
						return null
					}

					State.START -> {
						parser.state = State.NONE
						return "{" + ch
					}

					State.END -> {
						throw KadxRuntimeException(
							"Expected variable end: '${parser.curVariable}' (missing second '}')",
						)
					}

					State.NONE -> {
						// 与 Java 一样落到函数末尾（skip=false, return null）
					}
				}
			}
		}
		parser.skip = false
		return null
	}

	/** 查表替换变量；未注册的变量直接抛异常（与原 Java 行为一致）。 */
	private fun processVar(varName: String, rawValue: Boolean): String {
		val str = values[varName]
			?: throw KadxRuntimeException("Unknown variable: '$varName' in template: $templateName")
		if (!rawValue) {
			val sanitizer = valueSanitizer
			if (sanitizer != null) {
				return sanitizer(str)
			}
		}
		return str
	}

	companion object {
		/** 从 classpath 资源路径加载模板；资源不存在时抛 [FileNotFoundException]。 */
		@Throws(FileNotFoundException::class)
		fun fromResources(path: String): TemplateFile {
			val res = TemplateFile::class.java.getResourceAsStream(path)
				?: throw FileNotFoundException("Resource not found: $path")
			return TemplateFile(path, res)
		}
	}
}
