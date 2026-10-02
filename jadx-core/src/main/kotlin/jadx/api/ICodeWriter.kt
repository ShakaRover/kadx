package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import org.jetbrains.annotations.ApiStatus

/**
 * 代码写出器接口：jadx 通过它生成 Java/XML/JSON 等文本，并同时记录代码元数据（位置映射）。
 *
 * 这是公共 API，会被 jadx-cli / jadx-gui / 插件实现与调用，方法名与 JVM 签名必须保持不变。
 * 所有方法都返回 `this` 以便链式调用。
 */
interface ICodeWriter {

	/** 当前实现是否支持记录元数据。 */
	fun isMetadataSupported(): Boolean

	fun startLine(): ICodeWriter

	fun startLine(c: Char): ICodeWriter

	fun startLine(str: String): ICodeWriter

	fun startLineWithNum(sourceLine: Int): ICodeWriter

	fun addMultiLine(str: String): ICodeWriter

	/**
	 * 追加字符串。
	 *
	 * 参数保留可空：原 Java 接口无空值注解，实现直接 `buf.append(str)`，
	 * 传入 null 会追加字面量 "null"，这里保持相同行为。
	 */
	fun add(str: String?): ICodeWriter

	fun add(c: Char): ICodeWriter

	fun add(code: ICodeWriter): ICodeWriter

	fun newLine(): ICodeWriter

	fun addIndent(): ICodeWriter

	fun incIndent()

	fun decIndent()

	fun getIndent(): Int

	fun setIndent(indent: Int)

	/**
	 * 返回当前行号（仅在支持元数据时有意义）。
	 */
	fun getLine(): Int

	/**
	 * 返回当前行起始位置（仅在支持元数据时有意义）。
	 */
	fun getLineStartPos(): Int

	fun attachDefinition(obj: ICodeNodeRef?)

	fun attachAnnotation(obj: ICodeAnnotation?)

	fun attachLineAnnotation(obj: ICodeAnnotation?)

	fun attachSourceLine(sourceLine: Int)

	/** 结束写入并返回结果代码信息。 */
	fun finish(): ICodeInfo

	/** 返回已写入的代码字符串。 */
	fun getCodeStr(): String

	/** 返回已写入内容的长度。 */
	fun getLength(): Int

	/** 返回原始字符缓冲区（实现细节，供调试/内部使用）。 */
	fun getRawBuf(): StringBuilder

	/** 返回原始位置 -> 注解映射（内部 API，非稳定）。 */
	@ApiStatus.Internal
	fun getRawAnnotations(): Map<Int, ICodeAnnotation>
}
