package jadx.gui.ui.codearea.sync.fallback

import jadx.gui.ui.codearea.AbstractCodeArea
import java.util.regex.Pattern

/**
 * 光标位置下“词元（token）”的抽象基类。
 *
 * **做什么**：根据光标位置提取标识符（或字符串字面量），并提供判断该词元
 * 是否属于字段、字段引用、方法声明 / 调用等的能力，供回退同步使用。
 */
abstract class AbstractCodeAreaToken protected constructor(
	protected val area: AbstractCodeArea,
	at: Int,
) {
	private val atPos: Int = at

	protected var startPos: Int = 0
	protected var length: Int = 0

	init {
		extractTokenAt()
	}

	fun getAtPos(): Int = atPos

	/** 取词元文本。 */
	fun getStr(): String = area.getText(startPos, length)

	/** 词元后面紧跟 `(` 时，视为方法声明或调用。 */
	fun isMethodConstructorDeclarationOrCall(): Boolean = area.getText(startPos + length, 1) == "("

	/** 是否为方法体内的类字段引用。 */
	abstract fun isFieldReference(): Boolean

	/** 是否为类字段声明中的字段词元。 */
	abstract fun isClassField(): Boolean

	abstract fun getLine(): AbstractCodeAreaLine

	/** 从 [atPos] 处向两侧扩展，提取标识符；若没有标识符则尝试取所在行的字符串字面量。 */
	private fun extractTokenAt() {
		val text = area.getText()
		if (text.isEmpty()) {
			throw FallbackSyncException("text area is null or empty")
		}
		// 找出光标周围的单词边界
		var start = atPos
		var end = atPos

		while (start > 0 && Character.isJavaIdentifierPart(text[start - 1])) {
			start--
		}
		while (end < text.length && Character.isJavaIdentifierPart(text[end])) {
			end++
		}
		if (start == end) {
			// 没有标识符，尝试取光标所在行的字符串字面量
			val line = area.getLineOfOffset(atPos)
			val lineText = area.getText(
				area.getLineStartOffset(line),
				area.getLineEndOffset(line) - area.getLineStartOffset(line),
			)
			val p = Pattern.compile("\"([^\"]*)\"")
			val m = p.matcher(lineText)
			while (m.find()) {
				val litStart = area.getLineStartOffset(line) + m.start(1)
				val litEnd = area.getLineStartOffset(line) + m.end(1)
				if (atPos in litStart..litEnd) {
					this.startPos = m.start(1)
					this.length = m.end(1) - m.start(1)
					return
				}
			}
			throw FallbackSyncException("Unable to extract token at position $atPos")
		}
		this.startPos = start
		this.length = end - start
	}
}
