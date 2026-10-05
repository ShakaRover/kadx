package kadx.plugins.input.dex.sections

import kadx.api.plugins.input.data.IFieldData
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.api.plugins.utils.Utils
import kadx.plugins.input.dex.sections.annotations.AnnotationsParser

/**
 * DEX 字段数据：一个完整字段的元信息（父类/名字/类型 + 访问标志 + 常量值 + 注解）。
 *
 * **背景**：
 * 1. [SectionReader.getFieldRef] 用 `DexFieldData(null)` 构造"仅引用"视图（无注解解析器）；
 *    [DexClassData.visitFieldsAndMethods] 则传入真实 [AnnotationsParser]，复用同一实例逐个填充字段；
 * 2. 各字段通过 setter 延迟填充（DEX 中字段信息分散在 field_ids / class_data / encoded_array 多个 section）。
 */
public class DexFieldData(
	/** 注解解析器；"仅引用"视图下为 null，此时调用 [getAttributes] 会抛 NPE */
	private val annotationsParser: AnnotationsParser?,
) : IFieldData {

	private var parentClassTypeValue: String? = null
	private var typeValue: String? = null
	private var nameValue: String? = null
	private var accessFlagsValue: Int = 0
	private var annotationsOffset: Int = 0
	private var constValue: EncodedValue? = null

	override val parentClassType: String? get() = parentClassTypeValue

	public fun setParentClassType(parentClassTypeValue: String?) {
		this.parentClassTypeValue = parentClassTypeValue
	}

	override val type: String? get() = typeValue

	public fun setType(typeValue: String?) {
		this.typeValue = typeValue
	}

	override val name: String? get() = nameValue

	public fun setName(nameValue: String?) {
		this.nameValue = nameValue
	}

	override val accessFlags: Int get() = accessFlagsValue

	public fun setAccessFlags(accessFlagsValue: Int) {
		this.accessFlagsValue = accessFlagsValue
	}

	public fun setAnnotationsOffset(annotationsOffset: Int) {
		this.annotationsOffset = annotationsOffset
	}

	public fun setConstValue(constValue: EncodedValue?) {
		this.constValue = constValue
	}

	private val annotations: List<IAnnotation> get() {
		val parser = checkNotNull(annotationsParser) { "Annotation parser not initialized" }
		return parser.readAnnotationList(annotationsOffset)
	}

	override val attributes: List<IKadxAttribute> get() {
		val list = ArrayList<IKadxAttribute>(2)
		Utils.addToList(list, constValue)
		DexAnnotationsConvert.forField(list, annotations)
		return list
	}

	override fun toString(): String = "$parentClassTypeValue->$nameValue:$typeValue"
}
