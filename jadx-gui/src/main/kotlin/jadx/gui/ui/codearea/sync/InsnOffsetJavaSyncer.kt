package jadx.gui.ui.codearea.sync

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.core.dex.nodes.MethodNode
import jadx.gui.device.debugger.DbgUtils
import jadx.gui.device.debugger.smali.SmaliMethodNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.codearea.SmaliArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.AbstractMap.SimpleEntry

/**
 * 使用指令字节码偏移，把 Java 代码区同步到代码区 / Smali 区域。
 *
 * **做什么**：代码元数据里每个指令都挂着 [InsnCodeOffset]。先在源区域找到
 * 光标所在的“方法范围”与“指令偏移范围”，再到目标区域用相同偏移定位并高亮。
 * 只有 Smali 区域显示 Dalvik 字节码时才生效。
 */
class InsnOffsetJavaSyncer(private val from: CodeArea) :
	IToJavaSyncStrategy,
	IToSmaliSyncStrategy {

	override fun syncTo(to: SmaliArea): Boolean {
		if (!to.isShowingDalvikBytecode) {
			return false
		}

		// 1. 找到光标所在的方法范围
		// 2. 在方法范围内找到光标最近的 InsnCodeOffset 区间
		// 3. 找出落在该偏移区间内的所有 smali 行
		// 4. 高亮这些行并滚动到第一行
		val caretPos = from.getCaretPosition()
		val mthRange = findEnclosingMethodRange(caretPos) ?: return false
		val mthDefPos = mthRange.start.key
		val mthEndPos = mthRange.end.key

		LOG.debug("InsnOffsetJavaSyncer caretPos = {}", caretPos)
		LOG.debug("InsnOffsetJavaSyncer mthDefPos = {}", mthDefPos)
		LOG.debug("InsnOffsetJavaSyncer mthEndPos = {}", mthEndPos)

		val insnOffsetRange = findOffsetRange(caretPos, mthDefPos, mthEndPos) ?: return false
		val mthID = getMthRawFullID(mthDefPos)
		val smaliMthNode = DbgUtils.getSmaliMethodNode(to.getJClass(), mthID)
		if (smaliMthNode == null) {
			LOG.error("{} - mth ID {} not mapped to a SmaliMethodNode", LOG.getName(), mthID)
			return false
		}

		val smaliLines = getMappedSmaliLines(smaliMthNode, insnOffsetRange)
		if (smaliLines.size < 2) {
			return false
		}

		try {
			CodeSyncHighlighter.defaultHighlighter().highlightAndScrollToLine(to, smaliLines[0])
			for (i in 1 until smaliLines.size) {
				CodeSyncHighlighter.defaultHighlighter().highlightLine(to, smaliLines[i])
			}
			LOG.info("{} - successful sync of code to smali", LOG.getName())
			return true
		} catch (ex: Exception) {
			LOG.error("{} - Failed to sync code to smali with instruction offsets ", LOG.getName(), ex)
		}
		return false
	}

	override fun syncTo(to: CodeArea): Boolean {
		val caretPos = from.getCaretPosition()
		val fromMthRange = findEnclosingMethodRange(caretPos) ?: return false
		val mthDefPos = fromMthRange.start.key
		val mthEndPos = fromMthRange.end.key
		LOG.debug("InsnOffsetJavaSyncer caretPos = {}, mthDefPos = {}, mthEndPos = {}", caretPos, mthDefPos, mthEndPos)

		val fromInsnOffsetRange = findOffsetRange(caretPos, mthDefPos, mthEndPos) ?: return false
		val mthID = getMthRawFullID(mthDefPos)
		// 在目标区域搜索同名方法
		val toMthRange = findMethodRange(mthID, to) ?: return false
		val toMetadata = to.codeMetadata ?: return false

		// 搜索第一个指令偏移
		val firstInsnOffset = (fromInsnOffsetRange.start.value as InsnCodeOffset).getOffset()
		val highlightPosStart: Int? = toMetadata.searchDown(toMthRange.start.key) { offset, ann ->
			if (ann.getAnnType() != ICodeAnnotation.AnnType.OFFSET) {
				return@searchDown null
			}
			val pos = (ann as InsnCodeOffset).getOffset()
			if (pos != firstInsnOffset) {
				return@searchDown null
			}
			offset
		}
		if (highlightPosStart == null) {
			return false
		}

		// 搜索第二个指令偏移
		val secondInsnOffset = (fromInsnOffsetRange.end.value as InsnCodeOffset).getOffset()
		val highlightPosEnd: Int? = toMetadata.searchDown(highlightPosStart) { offset, ann ->
			if (ann.getAnnType() != ICodeAnnotation.AnnType.OFFSET) {
				return@searchDown null
			}
			val pos = (ann as InsnCodeOffset).getOffset()
			if (pos != secondInsnOffset) {
				return@searchDown null
			}
			offset
		}
		if (highlightPosEnd == null) {
			return false
		}
		to.scrollToPos(highlightPosStart)
		try {
			CodeSyncHighlighter.defaultHighlighter().highlightRange(to, highlightPosStart, highlightPosEnd)
			LOG.info("{} - successful sync of code to code", LOG.getName())
			return true
		} catch (ex: Exception) {
			LOG.error(
				"{} - Unable to highlight code area from insn offset mappings {} -> {}",
				LOG.getName(),
				highlightPosStart,
				highlightPosEnd,
			)
		}
		return false
	}

	/** 在 [area] 中查找与 [mthFullRawID] 匹配的方法声明及其结束位置。 */
	private fun findMethodRange(mthFullRawID: String, area: CodeArea): CodeMetadataRange? {
		val codeMetadata = area.codeMetadata ?: return null
		val toMthDecl: Map.Entry<Int, ICodeAnnotation>? =
			codeMetadata.searchDown<Map.Entry<Int, ICodeAnnotation>?>(0) { offset, ann ->
				if (ann.getAnnType() != ICodeAnnotation.AnnType.DECLARATION) {
					return@searchDown null
				}
				val node = (ann as NodeDeclareRef).getNode()
				if (node.getAnnType() != ICodeAnnotation.AnnType.METHOD) {
					return@searchDown null
				}
				val mth = node as MethodNode
				if (mth.methodInfo.rawFullId != mthFullRawID) {
					return@searchDown null
				}
				SimpleEntry(offset, ann)
			}
		if (toMthDecl == null) {
			return null
		}
		val toMthEnd: Map.Entry<Int, ICodeAnnotation>? =
			codeMetadata.searchDown<Map.Entry<Int, ICodeAnnotation>?>(toMthDecl.key) { offset, ann ->
				if (ann.getAnnType() != ICodeAnnotation.AnnType.END) {
					return@searchDown null
				}
				SimpleEntry(offset, ann)
			}
		if (toMthEnd == null) {
			return null
		}
		return CodeMetadataRange(toMthDecl, toMthEnd)
	}

	/** 查找包含 [startPos] 的方法声明范围（向上找方法声明，向下找 END）。 */
	private fun findEnclosingMethodRange(startPos: Int): CodeMetadataRange? {
		val codeMetadata = from.codeMetadata ?: return null
		val mthDef: Map.Entry<Int, ICodeAnnotation>? =
			codeMetadata.searchUp<Map.Entry<Int, ICodeAnnotation>?>(startPos) { offset, ann ->
				if (ann.getAnnType() != ICodeAnnotation.AnnType.DECLARATION) {
					return@searchUp null
				}
				val node = (ann as NodeDeclareRef).getNode()
				if (node.getAnnType() != ICodeAnnotation.AnnType.METHOD) {
					return@searchUp null
				}
				SimpleEntry(offset, ann)
			}
		if (mthDef == null) {
			return null
		}
		val mthEnd: Map.Entry<Int, ICodeAnnotation>? =
			codeMetadata.searchDown<Map.Entry<Int, ICodeAnnotation>?>(startPos) { offset, ann ->
				if (ann.getAnnType() != ICodeAnnotation.AnnType.END) {
					return@searchDown null
				}
				SimpleEntry(offset, ann)
			}
		if (mthEnd == null) {
			return null
		}
		return CodeMetadataRange(mthDef, mthEnd)
	}

	/**
	 * 构造光标附近的指令偏移区间：起点是 [startPos] 之前最近的 OFFSET，
	 * 终点是 [startPos] 之后最近的 OFFSET（必须单调递增）。
	 *
	 * @param startPos  搜索起点
	 * @param mthDefPos 所在方法声明位置
	 * @param mthEndPos 所在方法结束位置
	 */
	private fun findOffsetRange(startPos: Int, mthDefPos: Int, mthEndPos: Int): CodeMetadataRange? {
		val first = findInsnOffsetBeforePos(startPos, mthDefPos)
		val second = findInsnOffsetAfterPos(startPos, mthEndPos)
		if (first == null || second == null) {
			LOG.warn("{} - Unable to find InsnCodeOffsets between {} -> {}", LOG.getName(), mthDefPos, mthEndPos)
			return null
		}
		val startOffset = (first.value as InsnCodeOffset).getOffset()
		val endOffset = (second.value as InsnCodeOffset).getOffset()
		if (startOffset > endOffset) {
			LOG.warn(
				"{} - insn startOffset={} is greater than insn endOffset={} - cannot construct range",
				LOG.getName(),
				startOffset,
				endOffset,
			)
			return null
		}
		return CodeMetadataRange(first, second)
	}

	/** 向上查找偏移小于等于 [limit] 之前最近的一个指令偏移注解。 */
	private fun findInsnOffsetBeforePos(startPos: Int, limit: Int): Map.Entry<Int, ICodeAnnotation>? {
		val codeMetadata = from.codeMetadata ?: return null
		return codeMetadata.searchUp<Map.Entry<Int, ICodeAnnotation>?>(startPos) { offset, ann ->
			if (offset <= limit) {
				return@searchUp null
			}
			if (ann.getAnnType() != ICodeAnnotation.AnnType.OFFSET) {
				return@searchUp null
			}
			SimpleEntry(offset, ann)
		}
	}

	/** 向下查找偏移大于等于 [limit] 之前最近的一个指令偏移注解。 */
	private fun findInsnOffsetAfterPos(startPos: Int, limit: Int): Map.Entry<Int, ICodeAnnotation>? {
		val codeMetadata = from.codeMetadata ?: return null
		return codeMetadata.searchDown<Map.Entry<Int, ICodeAnnotation>?>(startPos) { offset, ann ->
			if (offset >= limit) {
				return@searchDown null
			}
			if (ann.getAnnType() != ICodeAnnotation.AnnType.OFFSET) {
				return@searchDown null
			}
			SimpleEntry(offset, ann)
		}
	}

	/**
	 * 假设 [mthDefPos] 处有一个 `NodeDeclareRef{MethodNode}` 注解，返回方法的原始全名。
	 */
	private fun getMthRawFullID(mthDefPos: Int): String {
		val ann = from.codeMetadata?.getAt(mthDefPos)
		val ref = ann as NodeDeclareRef
		val mth = ref.getNode() as MethodNode
		return mth.methodInfo.rawFullId
	}

	/**
	 * 取得与代码偏移区间对应的 smali 行索引。
	 *
	 * @param smaliMethodNode     目标方法
	 * @param insnCodeOffsetRange 光标所在的代码偏移区间
	 */
	private fun getMappedSmaliLines(
		smaliMethodNode: SmaliMethodNode,
		insnCodeOffsetRange: CodeMetadataRange,
	): List<Int> {
		val lines = ArrayList<Int>()
		val startInsnCodeOffset = (insnCodeOffsetRange.start.value as InsnCodeOffset).getOffset()
		val endInsnCodeOffset = (insnCodeOffsetRange.end.value as InsnCodeOffset).getOffset()
		// 行映射：smali 行索引 -> 代码偏移
		val smaliLineMapping = smaliMethodNode.getLineMapping()
		LOG.debug("startInsnPos={}, endInsnPos={}", startInsnCodeOffset, endInsnCodeOffset)
		for ((line, codeOffset) in smaliLineMapping) {
			LOG.debug("line={} -> codeOffset={}", line, codeOffset)
			// 假设 smali 调试工具给出的代码偏移与元数据中的一致
			if (codeOffset == startInsnCodeOffset || codeOffset == endInsnCodeOffset) {
				lines.add(line)
			}
		}
		lines.sort() // 只有两个元素
		return lines
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(InsnOffsetJavaSyncer::class.java)
	}
}
