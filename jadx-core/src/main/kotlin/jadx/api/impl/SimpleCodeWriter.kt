package jadx.api.impl

import jadx.api.ICodeInfo
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.core.utils.Utils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 不带元数据支持的代码写出器。
 *
 * **做什么**：把反编译结果拼接成文本（`buf` 字符缓冲），维护缩进（`indent`/`indentStr`）。
 * 生成位置映射/注解的方法在这里都是空实现，需要元数据时请用 `AnnotatedCodeWriter`。
 *
 * **为什么是 `open class`**：`AnnotatedCodeWriter`（本包）与 GUI 的 `SmaliWriter`（Java）继承它。
 *
 * **为什么 `buf` 是可空 + `@JvmField`**：原 Java 在 `finish()` 后把 `buf` 置为 null 释放内存，
 * 且 Java 子类 `SmaliWriter` 直接访问该字段（`buf.toString()`），所以必须保持“字段 + 可空”。
 */
open class SimpleCodeWriter : ICodeWriter {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SimpleCodeWriter::class.java)
	}

	// 代码字符缓冲；finish() 后置为 null。用 @JvmField 让 Java 子类能直接访问字段。
	@JvmField
	protected var buf: StringBuilder? = StringBuilder()

	// 当前缩进对应的空白字符串（由 indent 与 singleIndentStr 计算）
	protected var indentStr: String = ""

	// 当前缩进层级（private 以避免生成 getIndent()/setIndent() 与显式接口实现冲突）
	private var indent: Int = 0

	// 是否在每行前输出源码行号（由 JadxArgs 配置）
	protected val insertLineNumbers: Boolean

	// 单级缩进字符串
	protected val singleIndentStr: String

	// 换行符
	protected val newLineStr: String

	constructor(args: JadxArgs) {
		this.insertLineNumbers = args.isInsertDebugLines
		this.singleIndentStr = args.codeIndentStr
		this.newLineStr = args.codeNewLineStr
		if (insertLineNumbers) {
			incIndent(3)
			add(indentStr)
		}
	}

	/**
	 * 无参构造器：仅供测试/调试使用，使用默认缩进与换行符。
	 *
	 * 保留 `@Deprecated` 以提示优先使用带 [JadxArgs] 的构造器（与原 Java 一致）。
	 */
	@Deprecated("Use constructor with JadxArgs")
	constructor() {
		this.insertLineNumbers = false
		this.singleIndentStr = JadxArgs.DEFAULT_INDENT_STR
		this.newLineStr = JadxArgs.DEFAULT_NEW_LINE_STR
	}

	override fun isMetadataSupported(): Boolean = false

	override fun startLine(): SimpleCodeWriter {
		addLine()
		addLineIndent()
		return this
	}

	override fun startLine(c: Char): SimpleCodeWriter {
		startLine()
		add(c)
		return this
	}

	override fun startLine(str: String): SimpleCodeWriter {
		startLine()
		add(str)
		return this
	}

	override fun startLineWithNum(sourceLine: Int): SimpleCodeWriter {
		if (sourceLine == 0) {
			startLine()
			return this
		}
		if (this.insertLineNumbers) {
			newLine()
			attachSourceLine(sourceLine)
			val start = getLength()
			add("/* ").add(sourceLine.toString()).add(" */ ")
			val len = getLength() - start
			if (indentStr.length > len) {
				add(indentStr.substring(len))
			}
		} else {
			startLine()
			attachSourceLine(sourceLine)
		}
		return this
	}

	override fun addMultiLine(str: String): SimpleCodeWriter {
		if (str.contains(newLineStr)) {
			checkNotNull(buf).append(str.replace(newLineStr, newLineStr + indentStr))
		} else {
			checkNotNull(buf).append(str)
		}
		return this
	}

	override fun add(str: String?): SimpleCodeWriter {
		checkNotNull(buf).append(str)
		return this
	}

	override fun add(c: Char): SimpleCodeWriter {
		checkNotNull(buf).append(c)
		return this
	}

	override fun add(cw: ICodeWriter): ICodeWriter {
		checkNotNull(buf).append(cw.getCodeStr())
		return this
	}

	override fun newLine(): SimpleCodeWriter {
		addLine()
		return this
	}

	override fun addIndent(): SimpleCodeWriter {
		add(singleIndentStr)
		return this
	}

	/** 追加一个换行符（供子类覆写，例如记录行号）。 */
	protected open fun addLine() {
		checkNotNull(buf).append(newLineStr)
	}

	/** 在当前行首追加缩进空白。 */
	protected open fun addLineIndent(): SimpleCodeWriter {
		checkNotNull(buf).append(indentStr)
		return this
	}

	private fun updateIndent() {
		this.indentStr = Utils.strRepeat(singleIndentStr, indent)
	}

	override fun incIndent() {
		incIndent(1)
	}

	override fun decIndent() {
		decIndent(1)
	}

	private fun incIndent(c: Int) {
		this.indent += c
		updateIndent()
	}

	private fun decIndent(c: Int) {
		this.indent -= c
		if (this.indent < 0) {
			LOG.warn("Indent < 0")
			this.indent = 0
		}
		updateIndent()
	}

	override fun getIndent(): Int = indent

	override fun setIndent(indent: Int) {
		this.indent = indent
		updateIndent()
	}

	override fun getLine(): Int = 0

	override fun getLineStartPos(): Int = 0

	override fun attachDefinition(obj: ICodeNodeRef?) {
		// 无操作：本实现不支持元数据
	}

	override fun attachAnnotation(obj: ICodeAnnotation?) {
		// 无操作：本实现不支持元数据
	}

	override fun attachLineAnnotation(obj: ICodeAnnotation?) {
		// 无操作：本实现不支持元数据
	}

	override fun attachSourceLine(sourceLine: Int) {
		// 无操作：本实现不支持元数据
	}

	override fun finish(): ICodeInfo {
		val code = getStringWithoutFirstEmptyLine()
		buf = null
		return SimpleCodeInfo(code)
	}

	/** 去掉开头第一个空行（若存在）。 */
	private fun getStringWithoutFirstEmptyLine(): String {
		val b = checkNotNull(buf)
		val len = newLineStr.length
		if (b.length > len && b.substring(0, len) == newLineStr) {
			return b.substring(len)
		}
		return b.toString()
	}

	override fun getLength(): Int = checkNotNull(buf).length

	override fun getRawBuf(): StringBuilder = checkNotNull(buf)

	override fun getRawAnnotations(): Map<Int, ICodeAnnotation> = emptyMap()

	override fun getCodeStr(): String = checkNotNull(buf).toString()

	override fun toString(): String = getCodeStr()
}
