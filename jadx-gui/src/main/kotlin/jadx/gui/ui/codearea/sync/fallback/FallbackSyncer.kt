package jadx.gui.ui.codearea.sync.fallback

import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.codearea.SmaliArea
import jadx.gui.ui.codearea.sync.CodeSyncHighlighter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 基于正则 / 字符串的回退同步策略：当按调试行号或指令偏移都失败时使用。
 *
 * **同步思路**：
 * 1. 找到光标下的标识性词元（类成员名 / 字符串字面量）；
 * 2. 找到其所属的方法或类声明；
 * 3. 若光标在声明行上，则在另一侧找到等价的声明行；
 * 4. 否则在另一侧的外层方法 / 类中，找到该词元的第 n 次出现。
 *
 * **暂不支持**：泛型类 / 方法、匿名类、lambda、构造器。
 */
object FallbackSyncer {
	private val LOG: Logger = LoggerFactory.getLogger(FallbackSyncer::class.java)

	/** 执行回退同步；成功返回 true。 */
	@JvmStatic
	fun sync(fromArea: AbstractCodeArea, toArea: AbstractCodeArea): Boolean {
		LOG.debug("FALLBACK SYNC START")
		try {
			val caretPos = fromArea.getCaretPosition()
			val lineIndex = fromArea.getLineOfOffset(caretPos)
			val fromLines = fromArea.getText().split(Regex("\\R"))
			if (lineIndex >= fromLines.size) {
				return false
			}

			val caretLine = fromLines[lineIndex]
			LOG.debug("Caret line [{}]: {}", caretPos, caretLine)

			// 提取光标下的词元（字符串字面量或标识符）
			val areaToken = getToken(fromArea, caretPos)
			val token = areaToken.getStr()
			LOG.debug("Token at caret: '{}'", token)
			if (token.isEmpty()) {
				return false
			}

			if (!allowSync(areaToken)) {
				LOG.debug("Fallback matching only applicable for variable, classname, field or method tokens")
				return false
			}

			return syncToIdentifyingNthOccurence(areaToken, toArea)
		} finally {
			LOG.debug("FALLBACK SYNC END")
		}
	}

	/** 按区域类型创建对应的词元对象。 */
	private fun getToken(from: AbstractCodeArea, caretPos: Int): AbstractCodeAreaToken = when (from) {
		is SmaliArea -> SmaliAreaToken(from, caretPos)
		is CodeArea -> JavaCodeAreaToken(from, caretPos)
		else -> throw FallbackSyncException("Unknown AbstractCodeArea type for $from")
	}

	/** 在 [to] 的外层方法 / 类作用域中，查找词元的第 n 次出现并高亮。 */
	private fun syncToIdentifyingNthOccurence(sourceToken: AbstractCodeAreaToken, to: AbstractCodeArea): Boolean {
		val tokenLine = sourceToken.getLine()

		// 定位方法/类声明行作为上下文
		val fromDeclaration = tokenLine.getEnclosingScopeDeclaration()
		val fromDeclaringLine = fromDeclaration.getLine()

		val from = fromDeclaringLine.getArea()
		val declarationLineStr = fromDeclaringLine.getStr()
		LOG.debug("Found declaration line: {}", declarationLineStr)
		val nameToFind = fromDeclaration.getIdentifyingName()
		if (nameToFind.isNullOrEmpty()) {
			return false
		}

		// 判断匹配的是类还是方法
		val isClass = fromDeclaringLine.isClassDeclaration()
		val regex = if (isClass) generateClassRegex(nameToFind) else generateMethodRegex(nameToFind)

		// 在目标文本中查找声明
		val matcher = Pattern.compile(regex).matcher(to.getText())
		LOG.debug("Searching for {} in targetText, isClass {}", nameToFind, isClass)
		val targetDeclLine = findTargetDeclaringLine(to, matcher, fromDeclaration)
		if (targetDeclLine == null) {
			LOG.debug("Cannot find target declaration line")
			return false
		}
		val targetDeclarationLineIndex = targetDeclLine.getLineIndex()
		LOG.debug("Target declaration line {}", targetDeclLine.getStr())
		if (tokenLine.isScopeDeclarationLine()) {
			CodeSyncHighlighter.defaultHighlighter().highlightAndScrollToLine(to, targetDeclarationLineIndex)
			LOG.info("{} - Highlighted target declaration line", LOG.getName(), targetDeclLine.getStr())
			return true
		}

		// 从目标区域截取方法/类体
		val methodBody = extractMethodBody(to, matcher.start())

		// 在源方法体中统计光标对应的是第几次出现
		val fromMatcher = Pattern.compile(regex).matcher(from.getText())
		if (!fromMatcher.find()) {
			LOG.debug("No method/class match found in source for regex: {}", regex)
			return false
		}
		val sourceMethodBody = extractMethodBody(from, fromMatcher.start())

		val tokenStr = sourceToken.getStr()
		val caretPos = sourceToken.getAtPos()
		val caretOffsetInMethod = caretPos - fromMatcher.start()
		var nthOccurrence = 0
		val tokenPattern = Pattern.compile("\"" + Pattern.quote(tokenStr) + "\"|\\b" + Pattern.quote(tokenStr) + "\\b")
		var tokenMatcher = tokenPattern.matcher(sourceMethodBody)

		while (tokenMatcher.find()) {
			if (tokenMatcher.start() > caretOffsetInMethod) {
				break
			}
			nthOccurrence++
		}

		LOG.debug("Caret is at occurrence number: {}", nthOccurrence)

		// 在目标方法体中查找第 n 次出现
		tokenMatcher = tokenPattern.matcher(methodBody)
		var occurrenceCount = 0
		while (tokenMatcher.find()) {
			occurrenceCount++
			if (occurrenceCount == nthOccurrence) {
				val tokenPosInMethod = tokenMatcher.start()
				val absoluteOffset = matcher.start() + tokenPosInMethod
				val tokenLineIndex = to.getLineOfOffset(absoluteOffset)
				CodeSyncHighlighter.defaultHighlighter().highlightAndScrollToLine(to, tokenLineIndex)
				LOG.info("{} - Highlighted token '{}' at nth occurrence: {}", LOG.getName(), tokenStr, nthOccurrence)
				return true
			}
		}

		LOG.debug("No matching token or instruction found in method: {}", nameToFind)
		return false
	}

	/** 在目标区域中查找与 [sourceDecl] 等价的声明行。 */
	private fun findTargetDeclaringLine(
		to: AbstractCodeArea,
		matcher: Matcher,
		sourceDecl: IDeclaration,
	): AbstractCodeAreaLine? {
		// 在目标文本中查找声明
		while (matcher.find()) {
			LOG.debug("Match found at offset: {}", matcher.start())
			val targetDeclarationLineIndex = to.getLineOfOffset(matcher.start())
			val toDeclCandidate = getLine(to, targetDeclarationLineIndex)
			if (!toDeclCandidate.isScopeDeclarationLine()) {
				continue
			}
			val targetDecl = toDeclCandidate.getDeclaration()
			if (sourceDecl == targetDecl) {
				return toDeclCandidate
			}
		}
		return null
	}

	/** 按区域类型创建对应的行对象。 */
	private fun getLine(area: AbstractCodeArea, lineIndex: Int): AbstractCodeAreaLine = when (area) {
		is SmaliArea -> SmaliAreaLine(area, lineIndex)
		is CodeArea -> JavaCodeAreaLine(area, lineIndex)
		else -> throw FallbackSyncException("Unknown AbstractCodeArea type for $area")
	}

	/** 判断该词元是否允许参与回退匹配。 */
	private fun allowSync(areaToken: AbstractCodeAreaToken): Boolean {
		val isOnDeclarationLine = areaToken.getLine().isDeclarationLine()
		return isOnDeclarationLine ||
			areaToken.isClassField() ||
			areaToken.isFieldReference() ||
			areaToken.isMethodConstructorDeclarationOrCall()
	}

	private fun generateClassRegex(name: String): String = "\\b(class|interface|enum)\\s+" + Pattern.quote(name) + "\\b" + // java
		"|" +
		"\\.class.*L.*" + Pattern.quote(name) + ";" + // smali text
		"|" +
		"Class:\\sL.*" + Pattern.quote(name) + ";" // smali + dalvik

	private fun generateMethodRegex(name: String): String = "\\b" + Pattern.quote(name) + "\\s*\\(" + // java like
		"|" +
		"\\.method.*" + Pattern.quote(name) + "\\s*\\(" // smali

	/** 从 [startIndex] 开始截取方法体（smali 到 `.end method`，Java 按花括号配对）。 */
	private fun extractMethodBody(area: AbstractCodeArea, startIndex: Int): String {
		val text = area.getText()
		if (area is SmaliArea) {
			val end = text.indexOf(".end method", startIndex)
			return if (end != -1) text.substring(startIndex, end + ".end method".length) else text.substring(startIndex)
		}
		var brace = 0
		var inMethod = false
		for (i in startIndex until text.length) {
			val c = text[i]
			if (c == '{') {
				brace++
				inMethod = true
			} else if (c == '}') {
				brace--
				if (brace == 0 && inMethod) {
					return text.substring(startIndex, i + 1)
				}
			}
		}
		return text.substring(startIndex)
	}
}
