package jadx.plugins.input.dex.sections

import jadx.api.plugins.input.data.IFieldData
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.dex.sections.annotations.AnnotationsParser

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

	private var parentClassType: String? = null
	private var type: String? = null
	private var name: String? = null
	private var accessFlags: Int = 0
	private var annotationsOffset: Int = 0
	private var constValue: EncodedValue? = null

	override fun getParentClassType(): String? = parentClassType

	public fun setParentClassType(parentClassType: String?) {
		this.parentClassType = parentClassType
	}

	override fun getType(): String? = type

	public fun setType(type: String?) {
		this.type = type
	}

	override fun getName(): String? = name

	public fun setName(name: String?) {
		this.name = name
	}

	override fun getAccessFlags(): Int = accessFlags

	public fun setAccessFlags(accessFlags: Int) {
		this.accessFlags = accessFlags
	}

	public fun setAnnotationsOffset(annotationsOffset: Int) {
		this.annotationsOffset = annotationsOffset
	}

	public fun setConstValue(constValue: EncodedValue?) {
		this.constValue = constValue
	}

	private fun getAnnotations(): List<IAnnotation> {
		val parser = checkNotNull(annotationsParser) { "Annotation parser not initialized" }
		return parser.readAnnotationList(annotationsOffset)
	}

	override fun getAttributes(): List<IJadxAttribute> {
		val list = ArrayList<IJadxAttribute>(2)
		Utils.addToList(list, constValue)
		DexAnnotationsConvert.forField(list, getAnnotations())
		return list
	}

	override fun toString(): String = "$parentClassType->$name:$type"
}
