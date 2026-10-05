package kadx.plugins.input.dex.sections

import kadx.api.plugins.input.data.ICodeReader
import kadx.api.plugins.input.data.IMethodData
import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import kadx.api.plugins.utils.Utils
import kadx.plugins.input.dex.sections.annotations.AnnotationsParser
import kadx.plugins.input.dex.smali.SmaliPrinter

/**
 * DEX 方法数据：一个完整方法的元信息（引用 + 访问标志 + 代码读取器 + 注解）。
 *
 * **背景**：[DexClassData.readMethods] 复用同一实例逐个填充方法，
 * [methodRef] 构造后必须通过 [setMethodRef] 设置（与原 Java 的 null 初始字段一致，
 * 未设置时调用 [getMethodRef] 抛 NPE）。
 */
public class DexMethodData(
	/** 注解解析器；可为 null（此时调用 [getAttributes] 会抛 NPE）*/
	private val annotationsParserValue: AnnotationsParser?,
) : IMethodData {

	private var methodRefValue: DexMethodRef? = null

	private var accessFlagsValue: Int = 0
	private var annotationsOffset: Int = 0
	private var paramAnnotationsOffset: Int = 0

	/** 方法代码读取器；抽象/native 等无代码方法为 null */
	private var codeReaderValue: DexCodeReader? = null

	override val methodRef: DexMethodRef get() = checkNotNull(methodRefValue) { "method ref not set" }

	public fun setMethodRef(methodRefValue: DexMethodRef) {
		this.methodRefValue = methodRefValue
	}

	override val accessFlags: Int get() = accessFlagsValue

	public fun setAccessFlags(accessFlagsValue: Int) {
		this.accessFlagsValue = accessFlagsValue
	}

	override val codeReader: ICodeReader? get() = codeReaderValue

	public fun setCodeReader(codeReaderValue: DexCodeReader?) {
		this.codeReaderValue = codeReaderValue
	}

	override fun disassembleMethod(): String = SmaliPrinter.printMethod(this)

	public fun setAnnotationsOffset(annotationsOffset: Int) {
		this.annotationsOffset = annotationsOffset
	}

	public fun setParamAnnotationsOffset(paramAnnotationsOffset: Int) {
		this.paramAnnotationsOffset = paramAnnotationsOffset
	}

	private val annotations: List<IAnnotation> get() = annotationsParser.readAnnotationList(annotationsOffset)

	private val paramsAnnotations: List<List<IAnnotation>> get() = annotationsParser.readAnnotationRefList(paramAnnotationsOffset)

	override val attributes: List<IKadxAttribute> get() {
		val list = ArrayList<IKadxAttribute>()
		DexAnnotationsConvert.forMethod(list, annotations)
		Utils.addToList(list, AnnotationMethodParamsAttr.pack(paramsAnnotations))
		return list
	}

	private val annotationsParser: AnnotationsParser get() = checkNotNull(annotationsParserValue) { "Annotation parser not initialized" }

	override fun toString(): String = methodRef.toString()
}
