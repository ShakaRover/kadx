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
class JavaClassData(private val clsReader: JavaClassReader) : IClassData {

	private val data: DataReader = DataReader(clsReader.getData())
	private val offsets: ClassOffsets = ClassOffsets(data)
	private val constPoolReader: ConstPoolReader = ConstPoolReader(clsReader, this, data.copy(), offsets)
	private val attributesReader: AttributesReader = AttributesReader(this, constPoolReader)

	override fun getInputFileOffset(): Int = offsets.accessFlagsOffset

	override fun copy(): IClassData = this

	override fun getAccessFlags(): Int = data.absPos(offsets.accessFlagsOffset).readU2()

	// 接口声明非空；损坏 class 时 getClass 返回 null，调用方解引用与原 Java 一样 NPE
	override fun getType(): String {
		val idx = data.absPos(offsets.clsTypeOffset).readU2()
		return constPoolReader.getClass(idx)!!
	}

	@Nullable
	override fun getSuperType(): String? {
		val idx = data.absPos(offsets.superTypeOffset).readU2()
		if (idx == 0) {
			return null
		}
		return constPoolReader.getClass(idx)
	}

	override fun getInterfacesTypes(): List<String> {
		data.absPos(offsets.interfacesOffset)
		// readClassesList 元素理论上可空（损坏 class），透传给声明非空的接口类型（擦除后等价）
		@Suppress("UNCHECKED_CAST")
		return data.readClassesList(constPoolReader) as List<String>
	}

	override fun getInputFileName(): String = clsReader.getFileName()

	override fun visitFieldsAndMethods(fieldsConsumer: ISeqConsumer<IFieldData>, mthConsumer: ISeqConsumer<IMethodData>) {
		val clsIdx = data.absPos(offsets.clsTypeOffset).readU2()
		val classType = constPoolReader.getClass(clsIdx)!!
		val reader = data.absPos(offsets.fieldsOffset).copy()
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
		val attributes = attributesReader.loadAll(reader)

		field.setAccessFlags(accessFlags)
		field.setName(constPoolReader.getUtf8(nameIdx))
		field.setType(constPoolReader.getUtf8(typeIdx))
		field.setAttributes(attributes)
	}

	private fun parseMethod(reader: DataReader, method: JavaMethodData, id: Int) {
		var accessFlags = reader.readU2()
		val nameIdx = reader.readU2()
		val descriptorIdx = reader.readU2()
		val attributes = attributesReader.loadAll(reader)

		val methodRef = method.getMethodRef()
		methodRef.reset()
		methodRef.initUniqId(clsReader, id, false)
		methodRef.setName(constPoolReader.getUtf8(nameIdx))
		methodRef.setDescr(constPoolReader.getUtf8(descriptorIdx))

		if (methodRef.getName() == "<init>") {
			accessFlags = accessFlags or AccessFlags.CONSTRUCTOR // java bytecode don't use that flag
		}

		method.setData(accessFlags, attributes)
	}

	fun getData(): DataReader = data

	override fun getAttributes(): List<IJadxAttribute> {
		data.absPos(offsets.attributesOffset)
		val attributes = attributesReader.loadAll(data)
		val size = attributes.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IJadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributes))
		val innerClasses: InnerClassesAttr? = attributes.get(JavaAttrType.INNER_CLASSES)
		Utils.addToList(list, innerClasses)
		val sourceFile: SourceFileAttr? = attributes.get(JavaAttrType.SOURCE_FILE)
		Utils.addToList(list, sourceFile)
		val signature: SignatureAttr? = attributes.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		return list
	}

	fun <T : IJavaAttribute> loadClassAttribute(reader: DataReader, type: JavaAttrType<T>): T? {
		reader.absPos(offsets.attributesOffset)
		return attributesReader.loadOne(reader, type)
	}

	override fun getDisassembledCode(): String = DisasmUtils.get(data.getBytes())

	fun getClsReader(): JavaClassReader = clsReader

	fun getOffsets(): ClassOffsets = offsets

	fun getConstPoolReader(): ConstPoolReader = constPoolReader

	fun getAttributesReader(): AttributesReader = attributesReader

	override fun toString(): String = getInputFileName()
}
