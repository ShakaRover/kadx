package jadx.gui.ui.codearea.sync

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.core.dex.nodes.MethodNode
import jadx.gui.device.debugger.DbgUtils
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.codearea.SmaliArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.NavigableMap

/**
 * 使用指令字节码偏移，把 Smali 代码区同步到 Java 代码区。
 *
 * **做什么**：只有 Smali 区域显示 Dalvik 字节码（由调试模型生成）时才可用，
 * 因为此时才带有按行记录的代码偏移。流程：
 * 1. 由 smali 光标行取得代码偏移；
 * 2. 在 Java 代码元数据中找到对应方法的声明注解；
 * 3. 在方法内找出包含该偏移的指令区间；
 * 4. 高亮这些区间并滚动到第一个。
 */
class InsnOffsetSmaliSyncer(private val from: SmaliArea) : IToJavaSyncStrategy {

	override fun syncTo(to: CodeArea): Boolean {
		if (!from.isShowingDalvikBytecode) {
			// 该策略仅在调试模型生成 smali 时可用（此时才有按行的代码偏移）。
			return false
		}
		// 1. 由 smali 光标行取得代码偏移
		val jclass = from.getJClass()
		val lineInfo = DbgUtils.getCodeOffsetInfoByLine(jclass, from.getCaretLineNumber()) ?: return false
		val lineInfoPos = lineInfo.value
		LOG.debug(
			"lineInfo key {}, lineInfo value {}, caretLineNumber {}",
			lineInfo.key,
			lineInfo.value,
			from.getCaretLineNumber(),
		)
		val toMetadata = to.codeMetadata ?: return false

		@Suppress("UNCHECKED_CAST")
		val codeAreaAnnotationMap = toMetadata.getAsMap() as NavigableMap<Int, ICodeAnnotation>
		val methodDecl = findMethodDeclAnnotation(codeAreaAnnotationMap, lineInfo.key)
		if (methodDecl == null) {
			LOG.warn("{} - No NodeDeclareRef exists for {}", LOG.getName(), lineInfo.key)
			return false
		}
		// 从方法声明向后遍历注解，比较相邻两个指令偏移：
		// 若 smali 偏移落在两者之间，则把该区间加入高亮列表。
		var prev: Map.Entry<Int, ICodeAnnotation>? = null
		val offsetBoundariesToHighlight = ArrayList<CodeMetadataRange>()
		val it = methodDecl
		while (it.hasNext()) {
			val entry = it.next()
			if (entry.value.annType == ICodeAnnotation.AnnType.END) {
				break
			}
			if (entry.value.annType != ICodeAnnotation.AnnType.OFFSET) {
				continue
			}
			if (prev != null) {
				val currentInsnOffset = entry.value as InsnCodeOffset
				val prevInsnOffset = prev.value as InsnCodeOffset
				if (prevInsnOffset.getOffset() <= lineInfoPos && lineInfoPos <= currentInsnOffset.getOffset()) {
					offsetBoundariesToHighlight.add(CodeMetadataRange(prev, entry))
				}
			}
			prev = entry
		}

		if (offsetBoundariesToHighlight.isEmpty()) {
			return false
		}

		to.scrollToPos(offsetBoundariesToHighlight[0].start.key)

		try {
			for (cmr in offsetBoundariesToHighlight) {
				LOG.debug("Highlighting {}", cmr)
				CodeSyncHighlighter.defaultHighlighter().highlightRange(to, cmr.start.key, cmr.end.key)
			}
			LOG.info("{} - successful sync of smali to code", LOG.getName())
			return true
		} catch (ex: Exception) {
			LOG.error("{} - Unable to highlight smali -> code insn offset range: {}", LOG.getName(), ex.getLocalizedMessage())
		}
		return false
	}

	/**
	 * 查找 [smaliLineMthFullID] 对应方法的 `NodeDeclareRef` 注解。
	 *
	 * @param map                Java 代码区的注解映射
	 * @param smaliLineMthFullID 要查找的方法原始全名
	 * @return 指向注解映射中该条目的迭代器（从该条目继续向后遍历）
	 */
	private fun findMethodDeclAnnotation(
		map: NavigableMap<Int, ICodeAnnotation>,
		smaliLineMthFullID: String,
	): Iterator<Map.Entry<Int, ICodeAnnotation>>? {
		// 使用 NavigableMap 以保证迭代顺序
		val it = map.descendingMap().entries.iterator()
		while (it.hasNext()) {
			val entry = it.next()
			val value = entry.value
			if (value is NodeDeclareRef) {
				val node = value.getNode()
				if (node is MethodNode) {
					if (node.methodInfo.rawFullId == smaliLineMthFullID) {
						return it
					}
				}
			}
		}
		return null
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(InsnOffsetSmaliSyncer::class.java)
	}
}
