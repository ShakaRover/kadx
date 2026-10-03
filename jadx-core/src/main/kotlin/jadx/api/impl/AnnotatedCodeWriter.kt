package jadx.api.impl

import jadx.api.ICodeInfo
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.core.utils.StringUtils
import java.util.TreeMap

/**
 * 支持元数据的代码写出器：在写代码的同时记录“字符位置 -> 注解”和“生成行号 -> 源码行号”。
 *
 * **做什么**：jadx 需要把反编译后的字符位置映射回类/方法/字段/变量/指令，
 * 以便 GUI 点击跳转、按源码行显示。它在 [SimpleCodeWriter] 基础上额外维护：
 * - [annotations]：字符索引 -> 代码注解；
 * - [lineMap]：生成文件的行号 -> DEX 调试信息里的源码行号。
 *
 * **为什么 `add`/`addLine` 等要维护 `offset`**：`getLineStartPos()` 需要知道当前行已写了多少字符，
 * 用“总长度 - 当前行偏移”即可算出本行起点。
 */
class AnnotatedCodeWriter(args: JadxArgs) : SimpleCodeWriter(args) {

	private var line: Int = 1
	private var offset: Int = 0

	// 字符位置 -> 注解；首次写入时才创建具体 Map（与原 Java 的懒初始化一致）
	private var annotations: MutableMap<Int, ICodeAnnotation> = mutableMapOf()

	// 生成行号 -> 源码行号；使用 TreeMap 保证按行号有序
	private var lineMap: MutableMap<Int, Int> = mutableMapOf()

	override fun isMetadataSupported(): Boolean = true

	override fun addMultiLine(str: String): AnnotatedCodeWriter {
		if (str.contains(newLineStr)) {
			checkNotNull(buf).append(str.replace(newLineStr, newLineStr + indentStr))
			line += StringUtils.countMatches(str, newLineStr)
			offset = 0
		} else {
			checkNotNull(buf).append(str)
		}
		return this
	}

	override fun add(str: String?): AnnotatedCodeWriter {
		checkNotNull(buf).append(str)
		offset += checkNotNull(str).length
		return this
	}

	override fun add(c: Char): AnnotatedCodeWriter {
		checkNotNull(buf).append(c)
		offset++
		return this
	}

	override fun add(cw: ICodeWriter): ICodeWriter {
		if (!cw.isMetadataSupported()) {
			checkNotNull(buf).append(cw.codeStr)
			return this
		}
		val code = cw as AnnotatedCodeWriter
		line--
		val startPos = getLength()
		// 把被合并 writer 的注解位置整体平移 startPos
		for ((pos, ann) in code.annotations) {
			attachAnnotation(ann, startPos + pos)
		}
		// 行号映射同样平移（合并点会占用当前行，故先 line-- 再叠加）
		for ((codeLine, sourceLine) in code.lineMap) {
			attachSourceLine(line + codeLine, sourceLine)
		}
		line += code.line
		offset = code.offset
		checkNotNull(buf).append(checkNotNull(code.buf))
		return this
	}

	override fun addLine() {
		checkNotNull(buf).append(newLineStr)
		line++
		offset = 0
	}

	override fun addLineIndent(): AnnotatedCodeWriter {
		checkNotNull(buf).append(indentStr)
		offset += indentStr.length
		return this
	}

	override fun getLine(): Int = line

	override fun getLineStartPos(): Int = getLength() - offset

	override fun attachDefinition(obj: ICodeNodeRef?) {
		if (obj == null) {
			return
		}
		attachAnnotation(NodeDeclareRef(obj))
	}

	override fun attachAnnotation(obj: ICodeAnnotation?) {
		if (obj == null) {
			return
		}
		attachAnnotation(obj, getLength())
	}

	override fun attachLineAnnotation(obj: ICodeAnnotation?) {
		if (obj == null) {
			return
		}
		attachAnnotation(obj, getLineStartPos())
	}

	/** 把注解挂到指定字符位置。 */
	private fun attachAnnotation(obj: ICodeAnnotation, pos: Int) {
		if (annotations.isEmpty()) {
			annotations = HashMap()
		}
		annotations[pos] = obj
	}

	override fun attachSourceLine(sourceLine: Int) {
		if (sourceLine == 0) {
			return
		}
		attachSourceLine(line, sourceLine)
	}

	/** 记录“生成行号 -> 源码行号”映射。 */
	private fun attachSourceLine(decompiledLine: Int, sourceLine: Int) {
		if (lineMap.isEmpty()) {
			lineMap = TreeMap()
		}
		lineMap[decompiledLine] = sourceLine
	}

	override fun finish(): ICodeInfo {
		val code = checkNotNull(buf).toString()
		buf = null
		return AnnotatedCodeInfo(code, lineMap, annotations)
	}

	override fun getRawAnnotations(): Map<Int, ICodeAnnotation> = annotations
}
