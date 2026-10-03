package jadx.gui.ui.codearea.sync.fallback

import jadx.gui.ui.codearea.SmaliArea

/**
 * Smali 输出中的一行。
 *
 * **做什么**：判断是否为类 / 方法 / 字段声明，并从 smali 类型描述符中提取类名。
 */
class SmaliAreaLine(area: SmaliArea, lineIndex: Int) : AbstractCodeAreaLine(area, lineIndex) {

	override fun getLineAt(lineIndex: Int): AbstractCodeAreaLine = SmaliAreaLine(getArea() as SmaliArea, lineIndex)

	override fun isClassDeclaration(): Boolean = getTrimmedStr().startsWith("Class: ") || getTrimmedStr().startsWith(".class ")

	override fun isMethodOrConstructorDeclaration(): Boolean = getTrimmedStr().startsWith(".method")

	override fun isFieldDeclaration(): Boolean = getTrimmedStr().startsWith(".field")

	override fun extractDeclaredClassName(): String? {
		if (!isClassDeclaration()) {
			return null
		}
		val parts = getTrimmedStr().split(Regex("\\s+"))
		for (part in parts) {
			if (part.startsWith("L") && part.endsWith(";")) {
				val fileClassName: String
				if (part.contains("/")) {
					fileClassName = part.substring(part.lastIndexOf('/') + 1, part.length - 1)
				} else {
					fileClassName = part.substring(1, part.length - 1) // 去掉开头的 'L' 和结尾的 ';'
				}
				if (fileClassName.contains('$')) { // 内部类
					return fileClassName.substring(fileClassName.lastIndexOf('$') + 1)
				}
				return fileClassName
			}
		}
		return null
	}

	override fun extractDeclaredMethodName(): String? {
		if (!isMethodOrConstructorDeclaration()) {
			return null
		}
		val parenIndex = getTrimmedStr().indexOf('(')
		if (parenIndex > 0) {
			val beforeParen = getTrimmedStr().substring(0, parenIndex).trim()
			val tokens = beforeParen.split(Regex("\\s+"))
			return tokens[tokens.size - 1]
		}
		return null
	}

	override fun createMethodDeclaration(): MethodDeclaration = MethodDeclaration.create(this)
}
