package jadx.gui.ui.codearea.sync.fallback

import jadx.gui.ui.codearea.CodeArea

/**
 * Java 反编译输出中的词元：用于判断字段 / 字段引用。
 */
class JavaCodeAreaToken(area: CodeArea, at: Int) : AbstractCodeAreaToken(area, at) {

	override fun isClassField(): Boolean {
		val line = getLine()
		if (!line.isFieldDeclaration()) {
			return false
		}
		// 赋值紧跟词元
		if (line.str.contains("=")) {
			return area.getText(startPos + length, 2) == " ="
		}
		// 以 ';' 结尾
		return area.getText(startPos + length, 1) == ";"
	}

	override fun isFieldReference(): Boolean = area.getText(startPos - 5, 5) == "this."

	override fun getLine(): AbstractCodeAreaLine = JavaCodeAreaLine(area as CodeArea, area.getLineOfOffset(getAtPos()))
}
