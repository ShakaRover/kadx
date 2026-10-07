package kadx.plugins.input.dex.sections

import kadx.api.plugins.input.data.IClassData
import kadx.api.plugins.input.data.IFieldData
import kadx.api.plugins.input.data.IMethodData
import kadx.api.plugins.input.data.ISeqConsumer
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.api.plugins.input.data.attributes.types.SourceFileAttr
import kadx.plugins.input.dex.sections.annotations.AnnotationsParser
import kadx.plugins.input.dex.utils.SmaliUtils
import org.slf4j.LoggerFactory

/**
 * DEX 类数据：class_defs section 中一个 class_def_item 的完整视图。
 *
 * **背景**：
 * 1. [DexReader.visitClasses] 为所有 class 共享同一个实例——每次访问前 reader 游标推进 [SIZE] 字节，
 *    各 getter 按相对偏移（`sectionReader.pos(n * 4)`）惰性读取当前 class_def_item 的字段；
 * 2. [visitFieldsAndMethods] 解析 class_data：先读 4 个 ULEB128 计数，再依次遍历静态/实例字段与直接/虚方法，
 *    复用同一个 [DexFieldData]/[DexMethodData] 实例逐个填充后回调消费者；
 * 3. 注解偏移通过 [AnnotationsParser] 预读的 offset map 按 field/method idx 查询。
 */
public class DexClassData(
	private val sectionReader: SectionReader,
	private val annotationsParser: AnnotationsParser,
) : IClassData {

	public companion object {
		private val LOG = LoggerFactory.getLogger(DexClassData::class.java)

		/** class_def_item 大小：8 个 u4 字段 */
		public const val SIZE: Int = 8 * 4

		private fun getOffsetFromMap(idx: Int, annOffsetMap: Map<Int, Int>): Int {
			val offset = annOffsetMap[idx]
			return if (offset != null) offset else 0
		}
	}

	private val inputFileOffsetValue: Int

	init {
		inputFileOffsetValue = sectionReader.offset
	}

	override val inputFileOffset: Int get() = inputFileOffsetValue

	override fun copy(): IClassData = DexClassData(sectionReader.copy(), annotationsParser.copy())

	override val type: String get() {
		val typeIdx = sectionReader.pos(0).readInt()
		return checkNotNull(sectionReader.getType(typeIdx)) { "Unknown class type" }
	}

	override val accessFlags: Int get() = sectionReader.pos(4).readInt()

	override val superType: String? get() {
		val typeIdx = sectionReader.pos(2 * 4).readInt()
		return sectionReader.getType(typeIdx)
	}

	override val interfacesTypes: List<String> get() {
		val offset = sectionReader.pos(3 * 4).readInt()
		if (offset == 0) {
			return emptyList()
		}
		return sectionReader.absPos(offset).readTypeList()
	}

	private val sourceFile: String? get() {
		val strIdx = sectionReader.pos(4 * 4).readInt()
		return sectionReader.getString(strIdx)
	}

	override val inputFileName: String get() = sectionReader.dexReader.inputFileName

	public val annotationsOff: Int get() = sectionReader.pos(5 * 4).readInt()

	public val classDataOff: Int get() = sectionReader.pos(6 * 4).readInt()

	public val staticValuesOff: Int get() = sectionReader.pos(7 * 4).readInt()

	override fun visitFieldsAndMethods(fieldConsumer: ISeqConsumer<IFieldData>, mthConsumer: ISeqConsumer<IMethodData>) {
		val classDataOff = classDataOff
		if (classDataOff == 0) {
			return
		}
		val data = sectionReader.copy(classDataOff)
		val staticFieldsCount = data.readUleb128()
		val instanceFieldsCount = data.readUleb128()
		val directMthCount = data.readUleb128()
		val virtualMthCount = data.readUleb128()

		fieldConsumer.init(staticFieldsCount + instanceFieldsCount)
		mthConsumer.init(directMthCount + virtualMthCount)

		annotationsParser.setOffset(annotationsOff)
		visitFields(fieldConsumer, data, staticFieldsCount, instanceFieldsCount)
		visitMethods(mthConsumer, data, directMthCount, virtualMthCount)
	}

	private fun visitFields(fieldConsumer: ISeqConsumer<IFieldData>, data: SectionReader, staticFieldsCount: Int, instanceFieldsCount: Int) {
		val annotationOffsetMap = annotationsParser.readFieldsAnnotationOffsetMap()
		val fieldData = DexFieldData(annotationsParser)
		fieldData.setParentClassType(type)
		readFields(fieldConsumer, data, fieldData, staticFieldsCount, annotationOffsetMap, true)
		readFields(fieldConsumer, data, fieldData, instanceFieldsCount, annotationOffsetMap, false)
	}

	private fun readFields(
		fieldConsumer: ISeqConsumer<IFieldData>,
		data: SectionReader,
		fieldData: DexFieldData,
		count: Int,
		annOffsetMap: Map<Int, Int>,
		staticFields: Boolean,
	) {
		val constValues = if (staticFields) getStaticFieldInitValues(data.copy()) else null
		var fieldId = 0
		for (i in 0 until count) {
			fieldId += data.readUleb128()
			val accFlags = data.readUleb128()
			sectionReader.fillFieldData(fieldData, fieldId)
			fieldData.setAccessFlags(accFlags)
			fieldData.setAnnotationsOffset(getOffsetFromMap(fieldId, annOffsetMap))
			// 静态字段才有初始常量值（encoded_array），且数量可能少于字段数
			val cv = constValues
			fieldData.setConstValue(if (cv != null && staticFields && i < cv.size) cv[i] else null)
			fieldConsumer.accept(fieldData)
		}
	}

	private fun visitMethods(mthConsumer: ISeqConsumer<IMethodData>, data: SectionReader, directMthCount: Int, virtualMthCount: Int) {
		val methodData = DexMethodData(annotationsParser)
		methodData.setMethodRef(DexMethodRef())
		val annotationOffsetMap = annotationsParser.readMethodsAnnotationOffsetMap()
		val paramsAnnOffsetMap = annotationsParser.readMethodParamsAnnRefOffsetMap()

		readMethods(mthConsumer, data, methodData, directMthCount, annotationOffsetMap, paramsAnnOffsetMap)
		readMethods(mthConsumer, data, methodData, virtualMthCount, annotationOffsetMap, paramsAnnOffsetMap)
	}

	private fun readMethods(
		mthConsumer: ISeqConsumer<IMethodData>,
		data: SectionReader,
		methodData: DexMethodData,
		count: Int,
		annotationOffsetMap: Map<Int, Int>,
		paramsAnnOffsetMap: Map<Int, Int>,
	) {
		val dexCodeReader = DexCodeReader(sectionReader.copy())
		var mthIdx = 0
		for (i in 0 until count) {
			mthIdx += data.readUleb128()
			val accFlags = data.readUleb128()
			val codeOff = data.readUleb128()

			val methodRef = methodData.methodRef
			methodRef.reset()
			sectionReader.initMethodRef(mthIdx, methodRef)
			methodData.setAccessFlags(accFlags)
			if (codeOff == 0) {
				// 无代码（abstract/native/interface default 等）
				methodData.setCodeReader(null)
			} else {
				dexCodeReader.mthId = mthIdx
				dexCodeReader.setOffset(codeOff)
				methodData.setCodeReader(dexCodeReader)
			}
			methodData.setAnnotationsOffset(getOffsetFromMap(mthIdx, annotationOffsetMap))
			methodData.setParamAnnotationsOffset(getOffsetFromMap(mthIdx, paramsAnnOffsetMap))
			mthConsumer.accept(methodData)
		}
	}

	private fun getStaticFieldInitValues(reader: SectionReader): List<EncodedValue> {
		val staticValuesOff = staticValuesOff
		if (staticValuesOff == 0) {
			return emptyList()
		}
		reader.absPos(staticValuesOff)
		return annotationsParser.parseEncodedArray(reader)
	}

	private val annotations: List<IAnnotation> get() {
		annotationsParser.setOffset(annotationsOff)
		return annotationsParser.readClassAnnotations()
	}

	override val attributes: List<IKadxAttribute> get() {
		val list = ArrayList<IKadxAttribute>()
		val srcFile = sourceFile
		if (srcFile != null && !srcFile.isEmpty()) {
			list.add(SourceFileAttr(srcFile))
		}
		DexAnnotationsConvert.forClass(type, list, annotations)
		return list
	}

	public val classDefOffset: Int get() = sectionReader.pos(0).absPos

	override val disassembledCode: String get() {
		val buf = sectionReader.dexReader.buf
		// 堆内 buffer 直接取底层数组（零拷贝）；mmap buffer 没有底层数组
		// （.array() 会抛 UnsupportedOperationException），按需拷一份 ——
		// 只有 smali 输出才会走到这里，不影响默认的 Java 输出路径。
		val dexBuf = if (buf.hasArray()) {
			buf.array()
		} else {
			ByteArray(buf.capacity()).also { buf.duplicate().clear().get(it) }
		}
		return SmaliUtils.getSmaliCode(dexBuf, classDefOffset)
	}

	override fun toString(): String = type
}
