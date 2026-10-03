package jadx.gui.ui.codearea.sync.fallback

import jadx.gui.ui.codearea.SmaliArea

/**
 * Smali 输出中的词元：用于判断字段 / 字段引用。
 */
class SmaliAreaToken(area: SmaliArea, at: Int) : AbstractCodeAreaToken(area, at) {

	override fun isFieldReference(): Boolean = area.getText(startPos - 2, 2) == "->"

	override fun isClassField(): Boolean {
		val line = getLine()
		val startsWithField = line.isFieldDeclaration()
		if (startsWithField) {
			val tokenStr = str
			val trimmedLine = line.trimmedStr
			val lineTokenStartPos = trimmedLine.indexOf(tokenStr)
			val lineTokenAfterPos = lineTokenStartPos + length
			for (i in lineTokenAfterPos until trimmedLine.length) {
				when (trimmedLine[i]) {
					' ' -> {}
					':' -> return true
					else -> return false
				}
			}
		}
		return false
	}

	override fun getLine(): AbstractCodeAreaLine = SmaliAreaLine(area as SmaliArea, area.getLineOfOffset(getAtPos()))
}
