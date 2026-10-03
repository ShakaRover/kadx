package jadx.gui.ui.codearea.sync

import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.codearea.SmaliArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 使用 dex 调试行号信息，把 Java 代码区同步到 Java / Smali 代码区。
 *
 * **做什么**：调试行号（`.line`）把反编译输出行与源码行关联起来。
 * 从当前光标行向上回溯找到最近的“源码行号”，再在目标区域中反查该源码行。
 */
class DebugLineJavaSyncer(private val from: CodeArea) :
	IToSmaliSyncStrategy,
	IToJavaSyncStrategy {

	override fun syncTo(to: CodeArea): Boolean {
		// 目标可能是 java / simple / fallback 视图，不能只依赖当前行，
		// 需要用行号映射做关联。
		try {
			val toLineMapping = to.getFunctionUniqueLineMappings()
			if (toLineMapping.isEmpty()) {
				return false
			}
			val lineIndex = from.getCaretLineNumber()
			// lineIndex 从 0 开始，而行号映射基于 1
			val sourceLine = getClosestSourceLine(lineIndex + 1) ?: return false
			// 在目标区域做反向查找：目标行号 -> 源码行号
			for ((toLine, candidateSourceLine) in toLineMapping) {
				if (sourceLine == candidateSourceLine) {
					// 找到映射行，目标行号是 0 索引
					CodeSyncHighlighter.defaultHighlighter().highlightAndScrollToLine(to, toLine - 1)
					LOG.info("{} - successful sync of code to code", LOG.getName())
					return true
				}
			}
		} catch (e: Exception) {
			LOG.error("{} - Failed to sync from CodeArea to CodeArea: {}", LOG.getName(), e.getLocalizedMessage())
		}
		return false
	}

	override fun syncTo(to: SmaliArea): Boolean {
		try {
			val lineIndex = from.getCaretLineNumber()
			// lineIndex 从 0 开始，而行号映射基于 1
			val lineNum = lineIndex + 1
			val sourceLine = getClosestSourceLine(lineNum)
			if (sourceLine == null) {
				to.removeAllLineHighlights()
				LOG.debug("decompiled line {} not mapped to source line", lineNum)
				return false
			}
			// 在 smali 中查找 ".line <sourceLine>" 所在行
			LOG.debug("Finding \".line {}\" in smali", sourceLine)
			val smaliLine = findSmaliLineIndex(to, sourceLine)
			if (smaliLine < 0) {
				LOG.warn("{} - Source line {} not annotated in Smali", LOG.getName(), sourceLine)
				return false
			}
			CodeSyncHighlighter.defaultHighlighter().highlightAndScrollToLine(to, smaliLine)
			LOG.info("{} - successful sync of code to smali", LOG.getName())
			return true
		} catch (ex: Exception) {
			LOG.error("{} - Failed to sync CodeArea to SmaliArea: {}", LOG.getName(), ex.getLocalizedMessage())
		}
		return false
	}

	/**
	 * 从 [lineNum] 向上回溯，返回最近一个能映射到源码行号的输出行号。
	 * 有些中间行没有映射，需要一直回溯（例如 Simple 视图里多条指令行属于同一源码行）。
	 */
	private fun getClosestSourceLine(lineNum: Int): Int? {
		val lineMapping = from.getFunctionUniqueLineMappings()
		if (lineMapping.isEmpty()) {
			return null
		}
		var num = lineNum
		var sourceLine: Int? = null
		while (num >= 0) {
			sourceLine = lineMapping[num]
			if (sourceLine != null) {
				break
			}
			num--
		}
		return sourceLine
	}

	/** 在 smali 文本中查找 `".line <sourceLine>"` 所在的行索引。 */
	private fun findSmaliLineIndex(smaliArea: SmaliArea, sourceLine: Int): Int {
		val line = ".line " + sourceLine.toString()
		// 注意：Kotlin 的 split 默认按字面量切分，这里必须显式用 Regex 匹配 \R（任意换行）
		val smaliLines = smaliArea.getText().split(Regex("\\R"))
		for (i in smaliLines.indices) {
			if (smaliLines[i].trim() == line) {
				return i
			}
		}
		return -1
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DebugLineJavaSyncer::class.java)
	}
}
