package jadx.gui.ui.codearea.sync

import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.codearea.SmaliArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * 使用 smali 中的 dex 调试行信息，把 Smali 代码区同步到 Java 代码区。
 *
 * **做什么**：从光标所在 smali 行向上寻找最近的“锚点”（`.line` / `.method` /
 * `.field` / `.class` 等），若锚点是源码行，则在 Java 区域反查对应的输出行并高亮。
 */
class DebugLineSmaliSyncer(private val from: SmaliArea) : IToJavaSyncStrategy {

	override fun syncTo(to: CodeArea): Boolean {
		try {
			// 取源文本行与当前行索引
			val lineIndex = from.getCaretLineNumber()
			val fromLines = from.getText().split(Regex("\\R"))
			if (lineIndex >= fromLines.size) {
				return false
			}

			// 找到用于定位的锚点
			val anchor = findNearestAnchor(lineIndex, fromLines)
			if (anchor == null) {
				LOG.error("{} - No Smali Anchor found", LOG.getName())
				return false
			}

			if (anchor.type == Anchor.Type.SOURCE_LINE) {
				LOG.debug(anchor.toString())
				val toDecompToSourceMapping = to.getFunctionUniqueLineMappings()
				for ((decompLine, sourceLine) in toDecompToSourceMapping) {
					if (anchor.codeMappedLineNumber == sourceLine) {
						val decompLineIndex = decompLine - 1
						LOG.debug("Highlighting {} on {}", decompLine, to)
						CodeSyncHighlighter.defaultHighlighter().highlightAndScrollToLine(to, decompLineIndex)
						LOG.info("{} - successful sync of smali to code", LOG.getName())
						return true
					}
				}
			}
			to.removeAllLineHighlights()
		} catch (ex: Exception) {
			LOG.error("{} - Failed to sync from Smali to Code", LOG.getName(), ex)
		}
		return false
	}

	/** 从 [smaliLineNumber] 向上查找最近的锚点行。 */
	private fun findNearestAnchor(smaliLineNumber: Int, lines: List<String>): Anchor? {
		for (i in smaliLineNumber downTo 0) {
			val trimmedLine = lines[i].trim()
			if (trimmedLine.startsWith(".line")) {
				return Anchor(Anchor.Type.SOURCE_LINE, trimmedLine, i)
			}
			if (trimmedLine.startsWith(".method")) {
				return Anchor(Anchor.Type.METHOD_START, trimmedLine, i)
			}
			if (trimmedLine.startsWith(".end")) {
				return Anchor(Anchor.Type.METHOD_END, trimmedLine, i)
			}
			if (trimmedLine.startsWith(".field")) {
				return Anchor(Anchor.Type.FIELD, trimmedLine, i)
			}
			if (trimmedLine.startsWith(".class")) {
				return Anchor(Anchor.Type.CLASS, trimmedLine, smaliLineNumber)
			}
		}
		return null
	}

	/** 可作为代码区定位依据的 smali 行。 */
	private class Anchor(val type: Type, line: String, private val smaliLineNumber: Int) {
		enum class Type {
			SOURCE_LINE,
			METHOD_START,
			METHOD_END,
			FIELD,
			CLASS,
		}

		var codeMappedLineNumber: Int = -1

		init {
			map(line)
		}

		private fun map(line: String) {
			when (type) {
				Type.SOURCE_LINE -> {
					val p = Pattern.compile("(\\.line\\s)(\\d+)")
					val m = p.matcher(line)
					if (m.find()) {
						codeMappedLineNumber = m.group(2).toInt()
					}
				}

				else -> {
					codeMappedLineNumber = -1
				}
			}
		}

		override fun toString(): String = String.format("Anchor %s, %d, %d", type.name, smaliLineNumber, codeMappedLineNumber)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DebugLineSmaliSyncer::class.java)
	}
}
