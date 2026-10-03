package jadx.plugins.input.java.data

import jadx.api.plugins.input.data.AccessFlags
import jadx.api.plugins.input.data.IClassData
import jadx.api.plugins.input.data.IFieldData
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.ISeqConsumer
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.api.plugins.input.data.attributes.types.InnerClassesAttr
import jadx.api.plugins.input.data.attributes.types.SignatureAttr
import jadx.api.plugins.input.data.attributes.types.SourceFileAttr
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.java.JavaClassReader
import jadx.plugins.input.java.data.attributes.AttributesReader
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr
import jadx.plugins.input.java.utils.DisasmUtils
import org.jetbrains.annotations.Nullable
import java.util.ArrayList

/**
 * 单个 .class 文件的顶层数据容器。
 *
 **做什么**：构造时建立 [ClassOffsets] 偏移索引与 [ConstPoolReader]；
 * [visitFieldsAndMethods] 顺序遍历字段表/方法表并回调消费者（复用同一实例，逐条填充）；
 * 类型、接口列表、类级属性都从这里读取。
 */
class JavaClassData(private val clsReaderValue: JavaClassReader) : IClassData {

	private val dataValue: DataReader = DataReader(clsReaderValue.data)
	private val offsetsValue: ClassOffsets = ClassOffsets(dataValue)
	private val constPoolReaderValue: ConstPoolReader = ConstPoolReader(clsReaderValue, this, dataValue.copy(), offsetsValue)
	private val attributesReaderValue: AttributesReader = AttributesReader(this, constPoolReaderValue)

	override val inputFileOffset: Int get() = offsetsValue.accessFlagsOffset

	override fun copy(): IClassData = this

	override val accessFlags: Int get() = dataValue.absPos(offsetsValue.accessFlagsOffset).readU2()

	// 接口声明非空；损坏 class 时 getClass 返回 null，调用方解引用与原 Java 一样 NPE
	override val type: String get() {
		val idx = dataValue.absPos(offsetsValue.clsTypeOffset).readU2()
		return constPoolReaderValue.getClass(idx) ?: throw NullPointerException("class type is null")
	}

	@get:Nullable
	override val superType: String? get() {
		val idx = dataValue.absPos(offsetsValue.superTypeOffset).readU2()
		if (idx == 0) {
			return null
		}
		return constPoolReaderValue.getClass(idx)
	}

	override val interfacesTypes: List<String> get() {
		dataValue.absPos(offsetsValue.interfacesOffset)
		// readClassesList 元素理论上可空（损坏 class），透传给声明非空的接口类型（擦除后等价）
		@Suppress("UNCHECKED_CAST")
		return dataValue.readClassesList(constPoolReaderValue) as List<String>
	}

	override val inputFileName: String get() = clsReaderValue.fileName

	override fun visitFieldsAndMethods(fieldsConsumer: ISeqConsumer<IFieldData>, mthConsumer: ISeqConsumer<IMethodData>) {
		val clsIdx = dataValue.absPos(offsetsValue.clsTypeOffset).readU2()
		val classType = checkNotNull(constPoolReaderValue.getClass(clsIdx))
		val reader = dataValue.absPos(offsetsValue.fieldsOffset).copy()
		val fieldsCount = reader.readU2()
		fieldsConsumer.init(fieldsCount)
		if (fieldsCount != 0) {
			val field = JavaFieldData()
			field.setParentClassType(classType)
			for (i in 0 until fieldsCount) {
				parseField(reader, field)
				fieldsConsumer.accept(field)
			}
		}

		val methodsCount = reader.readU2()
		mthConsumer.init(methodsCount)
		if (methodsCount != 0) {
			val methodRef = JavaMethodRef()
			methodRef.setParentClassType(classType)
			val method = JavaMethodData(this, methodRef)
			for (i in 0 until methodsCount) {
				parseMethod(reader, method, i)
				mthConsumer.accept(method)
			}
		}
	}

	private fun parseField(reader: DataReader, field: JavaFieldData) {
		val accessFlags = reader.readU2()
		val nameIdx = reader.readU2()
		val typeIdx = reader.readU2()
		val attributesValue = attributesReaderValue.loadAll(reader)

		field.setAccessFlags(accessFlags)
		field.setName(constPoolReaderValue.getUtf8(nameIdx))
		field.setType(constPoolReaderValue.getUtf8(typeIdx))
		field.setAttributes(attributesValue)
	}

	private fun parseMethod(reader: DataReader, method: JavaMethodData, id: Int) {
		var accessFlags = reader.readU2()
		val nameIdx = reader.readU2()
		val descriptorIdx = reader.readU2()
		val attributesValue = attributesReaderValue.loadAll(reader)

		val methodRef = method.methodRef
		methodRef.reset()
		methodRef.initUniqId(clsReaderValue, id, false)
		methodRef.setName(constPoolReaderValue.getUtf8(nameIdx))
		methodRef.setDescr(constPoolReaderValue.getUtf8(descriptorIdx))

		if (methodRef.name == "<init>") {
			accessFlags = accessFlags or AccessFlags.CONSTRUCTOR // java bytecode don't use that flag
		}

		method.setData(accessFlags, attributesValue)
	}

	val data: DataReader get() = dataValue

	override val attributes: List<IJadxAttribute> get() {
		dataValue.absPos(offsetsValue.attributesOffset)
		val attributesValue = attributesReaderValue.loadAll(dataValue)
		val size = attributesValue.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IJadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributesValue))
		val innerClasses: InnerClassesAttr? = attributesValue.get(JavaAttrType.INNER_CLASSES)
		Utils.addToList(list, innerClasses)
		val sourceFile: SourceFileAttr? = attributesValue.get(JavaAttrType.SOURCE_FILE)
		Utils.addToList(list, sourceFile)
		val signature: SignatureAttr? = attributesValue.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		return list
	}

	fun <T : IJavaAttribute> loadClassAttribute(reader: DataReader, type: JavaAttrType<T>): T? {
		reader.absPos(offsetsValue.attributesOffset)
		return attributesReaderValue.loadOne(reader, type)
	}

	override val disassembledCode: String get() = DisasmUtils.get(dataValue.bytes)

	val clsReader: JavaClassReader get() = clsReaderValue

	val offsets: ClassOffsets get() = offsetsValue

	val constPoolReader: ConstPoolReader get() = constPoolReaderValue

	val attributesReader: AttributesReader get() = attributesReaderValue

	override fun toString(): String = inputFileName
}
