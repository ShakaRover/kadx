package jadx.plugins.input.dex.sections.annotations

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.annotations.JadxAnnotation
import jadx.plugins.input.dex.DexException
import jadx.plugins.input.dex.sections.SectionReader

/**
 * DEX 注解 section 解析器：读取 class/field/method/参数四级注解引用与注解值。
 *
 * **背景**：
 * 1. [setOffset] 定位到 class_annotations_item，依次读出 fields_count / methods_count /
 *    parameters_count 三个计数（offset == 0 表示无注解）；
 * 2. 各层级注解以 "idx → offset" 映射表组织（[readFieldsAnnotationOffsetMap] 等），
 *    实际注解内容在 annotation_off_item / annotation_set_ref_list 中，按需惰性解析；
 * 3. [readAnnotation] 为静态工具：读取 visibility + type_idx + encoded_array 键值对。
 */
public class AnnotationsParser(
	private val inReader: SectionReader,
	private val ext: SectionReader,
) {

	private var offset: Int = 0
	private var fieldsCount: Int = 0
	private var methodsCount: Int = 0
	private var paramsRefCount: Int = 0

	public fun copy(): AnnotationsParser = AnnotationsParser(inReader.copy(), ext.copy())

	/**
	 * 定位到 class_annotations_item。
	 * offset == 0 时清空计数（无注解）；否则读取三个层级各自的条目数。
	 */
	public fun setOffset(offset: Int) {
		this.offset = offset
		if (offset == 0) {
			fieldsCount = 0
			methodsCount = 0
			paramsRefCount = 0
		} else {
			inReader.offset = offset
			inReader.pos(4) // 跳过 class_annotations_off（位于 item 起始）
			fieldsCount = inReader.readInt()
			methodsCount = inReader.readInt()
			paramsRefCount = inReader.readInt()
		}
	}

	public fun readClassAnnotations(): List<IAnnotation> {
		if (offset == 0) {
			return emptyList()
		}
		val classAnnotationsOffset = inReader.absPos(offset).readInt()
		return readAnnotationList(classAnnotationsOffset)
	}

	/** @return field_idx → annotation_off_item 偏移的映射 */
	public fun readFieldsAnnotationOffsetMap(): Map<Int, Int> {
		if (fieldsCount == 0) {
			return emptyMap()
		}
		inReader.pos(4 * 4) // class_annotations_off + fields_count
		val map = HashMap<Int, Int>(fieldsCount)
		for (i in 0 until fieldsCount) {
			val fieldIdx = inReader.readInt()
			val fieldAnnOffset = inReader.readInt()
			map[fieldIdx] = fieldAnnOffset
		}
		return map
	}

	/** @return method_idx → annotation_off_item 偏移的映射 */
	public fun readMethodsAnnotationOffsetMap(): Map<Int, Int> {
		if (methodsCount == 0) {
			return emptyMap()
		}
		inReader.pos(4 * 4 + fieldsCount * 2 * 4) // 跳过 class_annotations_off + fields_count + field 映射表
		val map = HashMap<Int, Int>(methodsCount)
		for (i in 0 until methodsCount) {
			val methodIdx = inReader.readInt()
			val methodAnnOffset = inReader.readInt()
			map[methodIdx] = methodAnnOffset
		}
		return map
	}

	/** @return method_idx → annotation_set_ref_list 偏移的映射（参数注解）*/
	public fun readMethodParamsAnnRefOffsetMap(): Map<Int, Int> {
		if (paramsRefCount == 0) {
			return emptyMap()
		}
		inReader.pos(4 * 4 + fieldsCount * 2 * 4 + methodsCount * 2 * 4) // 跳过前面所有字段与方法映射表
		val map = HashMap<Int, Int>(paramsRefCount)
		for (i in 0 until paramsRefCount) {
			val methodIdx = inReader.readInt()
			val methodAnnRefOffset = inReader.readInt()
			map[methodIdx] = methodAnnRefOffset
		}
		return map
	}

	/** 读取 annotation_off_item：size + size 个注解偏移，逐个解析。 */
	public fun readAnnotationList(offset: Int): List<IAnnotation> {
		if (offset == 0) {
			return emptyList()
		}
		inReader.absPos(offset)
		val size = inReader.readInt()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IAnnotation>(size)
		val pos = inReader.absPos
		for (i in 0 until size) {
			inReader.absPos(pos + i * 4)
			val annOffset = inReader.readInt()
			inReader.absPos(annOffset)
			list.add(readAnnotation(inReader, ext, true))
		}
		return list
	}

	/** 读取 annotation_set_ref_list：每个参数位置一个注解列表。 */
	public fun readAnnotationRefList(offset: Int): List<List<IAnnotation>> {
		if (offset == 0) {
			return emptyList()
		}
		inReader.absPos(offset)
		val size = inReader.readInt()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<List<IAnnotation>>(size)
		for (i in 0 until size) {
			val refOff = inReader.readInt()
			val pos = inReader.absPos
			list.add(readAnnotationList(refOff))
			inReader.absPos(pos)
		}
		return list
	}

	public companion object {
		/**
		 * 读取单个注解（annotation_item）。
		 * @param readVisibility true 时先读 visibility 字节（class/field/method 级注解需要），
		 *        false 时无 visibility 前缀（参数注解）
		 */
		@JvmStatic
		public fun readAnnotation(inReader: SectionReader, ext: SectionReader, readVisibility: Boolean): IAnnotation {
			var visibility: AnnotationVisibility? = null
			if (readVisibility) {
				val v = inReader.readUByte()
				visibility = getVisibilityValue(v)
			}
			val typeIndex = inReader.readUleb128()
			val size = inReader.readUleb128()
			val values = LinkedHashMap<String, EncodedValue>(size)
			for (i in 0 until size) {
				// 注解键名/类型索引必须有效（NO_INDEX 属于非法数据）
				val name = checkNotNull(ext.getString(inReader.readUleb128())) { "invalid string index in annotation" }
				values[name] = EncodedValueParser.parseValue(inReader, ext)
			}
			val type = checkNotNull(ext.getType(typeIndex)) { "invalid type index: $typeIndex" }
			return JadxAnnotation(visibility, type, values)
		}

		private fun getVisibilityValue(value: Int): AnnotationVisibility = when (value) {
			0 -> AnnotationVisibility.BUILD
			1 -> AnnotationVisibility.RUNTIME
			2 -> AnnotationVisibility.SYSTEM
			else -> throw DexException("Unknown annotation visibility value: $value")
		}
	}

	public fun parseEncodedValue(reader: SectionReader): EncodedValue = EncodedValueParser.parseValue(reader, ext)

	public fun parseEncodedArray(reader: SectionReader): List<EncodedValue> = EncodedValueParser.parseEncodedArray(reader, ext)
}
