package kadx.gui.ui.codearea.sync.fallback

import kadx.gui.ui.codearea.CodeArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Java 反编译输出中的一行。
 *
 * **做什么**：用正则判断是否为类 / 方法 / 字段声明，并提取类名或方法名。
 */
class JavaCodeAreaLine(area: CodeArea, lineIndex: Int) : AbstractCodeAreaLine(area, lineIndex) {

	override fun getLineAt(lineIndex: Int): AbstractCodeAreaLine = JavaCodeAreaLine(getArea() as CodeArea, lineIndex)

	override fun isClassDeclaration(): Boolean = Regex(".*\\b(class|interface|enum)\\b.*\\{").matches(trimmedStr)

	override fun isMethodOrConstructorDeclaration(): Boolean {
		val l = trimmedStr
		// 跳过控制流语句（避免把 if / for 等误判为方法）
		// 注意：这里依赖 kadx 代码生成格式，且假设 kadx 不会把两条语句输出在同一行
		if (l.startsWith("if ") ||
			l.startsWith("for ") ||
			l.startsWith("while ") ||
			l.startsWith("switch ") ||
			l.startsWith("case ") ||
			l.startsWith("break ") ||
			l.startsWith("default ") ||
			l.startsWith("} else if ") ||
			l.startsWith("} else ") ||
			l.startsWith("try ") ||
			l.startsWith("} catch ") ||
			l.startsWith("} finally ") ||
			l.startsWith("throw ") ||
			l.startsWith("do ") ||
			l.startsWith("synchronized ")
		) {
			return false
		}
		val hasParens = l.contains("(") && l.contains(")")
		val isDefined = l.endsWith("{")
		val isAbstract = l.contains("abstract") && l.endsWith(";")
		return hasParens && (isDefined || isAbstract)
	}

	override fun isFieldDeclaration(): Boolean {
		try {
			val enclosingDeclaration = enclosingScopeDeclaration
			if (enclosingDeclaration !is ClassDeclaration) {
				return false
			}
			val line = trimmedStr
			// 也可能包含匿名类或 lambda 的字段
			return line.endsWith(";") || line.contains(" = ")
		} catch (ex: Exception) {
			LOG.error("{} - Unable to determine if line is a field declaration", LOG.getName(), ex)
		}
		return false
	}

	override fun extractDeclaredClassName(): String? {
		if (!isClassDeclaration()) {
			return null
		}
		val tokens = trimmedStr.split(Regex("\\s+"))
		for (i in tokens.indices) {
			if (tokens[i] == "class" || tokens[i] == "interface" || tokens[i] == "enum") {
				if (i + 1 < tokens.size) {
					return tokens[i + 1]
				}
			}
		}
		return null
	}

	override fun extractDeclaredMethodName(): String? {
		if (!isMethodOrConstructorDeclaration()) {
			return null
		}
		val paren = trimmedStr.indexOf('(')
		val before = trimmedStr.substring(0, paren).trim()
		val parts = before.split(Regex("\\s+"))
		return parts[parts.size - 1] // 最后一个词元就是方法名
	}

	override fun createMethodDeclaration(): MethodDeclaration = MethodDeclaration.create(this)

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JavaCodeAreaLine::class.java)
	}
}
