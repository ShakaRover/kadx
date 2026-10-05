package kadx.gui.ui.codearea.sync.fallback

import java.util.Objects

/**
 * 类声明：记录类名与声明所在行。
 *
 * **为什么不能用 `data class`**：原实现有自定义 `equals/hashCode`（只按标识名判等），
 * `data class` 会破坏该语义。
 */
class ClassDeclaration(private val line: AbstractCodeAreaLine) : IDeclaration {
	private val name: String = line.extractDeclaredClassName()
		?: throw FallbackSyncException("line does not declare a class: $line")

	override fun getIdentifyingName(): String = name

	override fun getLine(): AbstractCodeAreaLine = line

	override fun equals(other: Any?): Boolean {
		if (other is ClassDeclaration) {
			return this.getIdentifyingName() == other.getIdentifyingName()
		}
		return false
	}

	override fun hashCode(): Int = Objects.hash(line, name)
}
