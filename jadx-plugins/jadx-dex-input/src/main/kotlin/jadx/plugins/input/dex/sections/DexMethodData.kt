package jadx.plugins.input.dex.sections

import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.dex.sections.annotations.AnnotationsParser
import jadx.plugins.input.dex.smali.SmaliPrinter

/**
 * DEX 方法数据：一个完整方法的元信息（引用 + 访问标志 + 代码读取器 + 注解）。
 *
 * **背景**：[DexClassData.readMethods] 复用同一实例逐个填充方法，
 * [methodRef] 构造后必须通过 [setMethodRef] 设置（与原 Java 的 null 初始字段一致，
 * 未设置时调用 [getMethodRef] 抛 NPE）。
 */
public class DexMethodData(
	/** 注解解析器；可为 null（此时调用 [getAttributes] 会抛 NPE）*/
	private val annotationsParser: AnnotationsParser?,
) : IMethodData {

	private var methodRef: DexMethodRef? = null

	private var accessFlags: Int = 0
	private var annotationsOffset: Int = 0
	private var paramAnnotationsOffset: Int = 0

	/** 方法代码读取器；抽象/native 等无代码方法为 null */
	private var codeReader: DexCodeReader? = null

	override fun getMethodRef(): DexMethodRef = checkNotNull(methodRef) { "method ref not set" }

	public fun setMethodRef(methodRef: DexMethodRef) {
		this.methodRef = methodRef
	}

	override fun getAccessFlags(): Int = accessFlags

	public fun setAccessFlags(accessFlags: Int) {
		this.accessFlags = accessFlags
	}

	override fun getCodeReader(): ICodeReader? = codeReader

	public fun setCodeReader(codeReader: DexCodeReader?) {
		this.codeReader = codeReader
	}

	override fun disassembleMethod(): String = SmaliPrinter.printMethod(this)

	public fun setAnnotationsOffset(annotationsOffset: Int) {
		this.annotationsOffset = annotationsOffset
	}

	public fun setParamAnnotationsOffset(paramAnnotationsOffset: Int) {
		this.paramAnnotationsOffset = paramAnnotationsOffset
	}

	private fun getAnnotations(): List<IAnnotation> = getAnnotationsParser().readAnnotationList(annotationsOffset)

	private fun getParamsAnnotations(): List<List<IAnnotation>> = getAnnotationsParser().readAnnotationRefList(paramAnnotationsOffset)

	override fun getAttributes(): List<IJadxAttribute> {
		val list = ArrayList<IJadxAttribute>()
		DexAnnotationsConvert.forMethod(list, getAnnotations())
		Utils.addToList(list, AnnotationMethodParamsAttr.pack(getParamsAnnotations()))
		return list
	}

	private fun getAnnotationsParser(): AnnotationsParser = checkNotNull(annotationsParser) { "Annotation parser not initialized" }

	override fun toString(): String = getMethodRef().toString()
}
