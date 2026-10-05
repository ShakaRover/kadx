package kadx.plugins.input.java.data

import kadx.api.plugins.input.data.ICallSite
import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.IMethodRef
import kadx.api.plugins.input.data.MethodHandleType
import kadx.api.plugins.input.data.annotations.EncodedType
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.impl.CallSite
import kadx.api.plugins.input.data.impl.FieldRefHandle
import kadx.api.plugins.input.data.impl.MethodRefHandle
import kadx.plugins.input.java.JavaClassReader
import kadx.plugins.input.java.data.attributes.JavaAttrType
import kadx.plugins.input.java.data.attributes.types.JavaBootstrapMethodsAttr
import kadx.plugins.input.java.data.attributes.types.data.RawBootstrapMethod
import kadx.plugins.input.java.utils.DescriptorParser
import kadx.plugins.input.java.utils.JavaClassParseException
import kadx.plugins.input.java.utils.ModifiedUTF8Decoder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.util.ArrayList

/**
 * class 文件常量池读取器。
 *
 **做什么**：按条目编号随机访问常量池——类名、字段/方法引用、字面量、字符串等；
 * 引用类条目惰性封装成 [JavaFieldData]/[JavaMethodRef] 对象（首次访问才解析）。
 *
 **为什么每次读取都 jumpToData**：常量池条目变长且无对齐保证，
 * 每次访问必须先用 [ClassOffsets] 定位到该条目的数据起点。
 */
class ConstPoolReader(
	private val clsReader: JavaClassReader,
	private val clsData: JavaClassData,
	private val data: DataReader,
	private val offsets: ClassOffsets,
) {

	@Nullable
	fun getClass(idx: Int): String? {
		jumpToData(idx)
		val nameIdx = data.readU2()
		return fixType(checkNotNull(getUtf8(nameIdx)))
	}

	fun getFieldRef(idx: Int): IFieldRef {
		jumpToData(idx)
		val clsIdx = data.readU2()
		val nameTypeIdx = data.readU2()
		jumpToData(nameTypeIdx)
		val nameIdx = data.readU2()
		val typeIdx = data.readU2()

		val fieldData = JavaFieldData()
		fieldData.setParentClassType(getClass(clsIdx))
		fieldData.setName(getUtf8(nameIdx))
		fieldData.setType(getUtf8(typeIdx))
		return fieldData
	}

	fun getFieldType(idx: Int): String {
		jumpToData(idx)
		data.skip(2) // class_idx
		val nameTypeIdx = data.readU2()
		jumpToData(nameTypeIdx)
		data.skip(2) // name_idx
		val typeIdx = data.readU2()
		return checkNotNull(getUtf8(typeIdx))
	}

	fun getMethodRef(idx: Int): IMethodRef {
		jumpToData(idx)
		val clsIdx = data.readU2()
		val nameTypeIdx = data.readU2()
		jumpToData(nameTypeIdx)
		val nameIdx = data.readU2()
		val descIdx = data.readU2()

		val mthRef = JavaMethodRef()
		mthRef.initUniqId(clsReader, idx, true)
		mthRef.setParentClassType(getClass(clsIdx))
		mthRef.setName(getUtf8(nameIdx))
		mthRef.setDescr(getUtf8(descIdx))
		return mthRef
	}

	fun getCallSite(idx: Int): ICallSite = when (val constType = jumpToConst(idx)) {
		ConstantType.INVOKE_DYNAMIC -> {
			val bootstrapMthIdx = data.readU2()
			val nameAndTypeIdx = data.readU2()
			jumpToData(nameAndTypeIdx)
			val nameIdx = data.readU2()
			val descIdx = data.readU2()
			resolveMethodCallSite(bootstrapMthIdx, nameIdx, descIdx)
		}

		ConstantType.DYNAMIC -> throw JavaClassParseException("Field call site not yet implemented")

		else -> throw JavaClassParseException("Unexpected tag type for call site: " + constType)
	}

	private fun resolveMethodCallSite(bootstrapMthIdx: Int, nameIdx: Int, descIdx: Int): CallSite {
		val bootstrapMethodsAttr = clsData.loadClassAttribute(data, JavaAttrType.BOOTSTRAP_METHODS)
			?: throw JavaClassParseException("Unexpected missing BootstrapMethods attribute")
		val rawBootstrapMethod = bootstrapMethodsAttr.list[bootstrapMthIdx]

		val values = ArrayList<EncodedValue>(6)
		values.add(EncodedValue(EncodedType.ENCODED_METHOD_HANDLE, getMethodHandle(rawBootstrapMethod.methodHandleIdx)))
		values.add(EncodedValue(EncodedType.ENCODED_STRING, getUtf8(nameIdx)))
		values.add(EncodedValue(EncodedType.ENCODED_METHOD_TYPE, DescriptorParser.parseToMethodProto(checkNotNull(getUtf8(descIdx)))))
		for (argConstIdx in rawBootstrapMethod.args) {
			values.add(readAsEncodedValue(argConstIdx))
		}
		return CallSite(values)
	}

	private fun getMethodHandle(idx: Int): IMethodHandle {
		jumpToData(idx)
		val kind = data.readU1()
		val refIdx = data.readU2()
		val handleType = convertMethodHandleKind(kind)
		if (handleType.isField) {
			return FieldRefHandle(handleType, getFieldRef(refIdx))
		}
		return MethodRefHandle(handleType, getMethodRef(refIdx))
	}

	private fun convertMethodHandleKind(kind: Int): MethodHandleType = when (kind) {
		1 -> MethodHandleType.STATIC_PUT
		2 -> MethodHandleType.STATIC_GET
		3 -> MethodHandleType.INSTANCE_PUT
		4 -> MethodHandleType.INSTANCE_GET
		5 -> MethodHandleType.INVOKE_INSTANCE
		6 -> MethodHandleType.INVOKE_STATIC
		7 -> MethodHandleType.INVOKE_DIRECT
		8 -> MethodHandleType.INVOKE_CONSTRUCTOR
		9 -> MethodHandleType.INVOKE_INTERFACE
		else -> throw IllegalArgumentException("Unknown method handle type: " + kind)
	}

	fun getUtf8(idx: Int): String? {
		if (idx == 0) {
			return null
		}
		jumpToData(idx)
		return readString()
	}

	fun jumpToConst(idx: Int): ConstantType {
		jumpToTag(idx)
		return ConstantType.getTypeByTag(data.readU1())
	}

	fun readString(): String {
		val len = data.readU2()
		val bytes = data.readBytes(len)
		return parseString(bytes)
	}

	fun readU2(): Int = data.readU2()

	fun readU4(): Int = data.readU4()

	fun readU8(): Long = data.readU8()

	fun getInt(idx: Int): Int {
		jumpToData(idx)
		return data.readS4()
	}

	fun getLong(idx: Int): Long {
		jumpToData(idx)
		return data.readS8()
	}

	fun getDouble(idx: Int): Double {
		jumpToData(idx)
		return java.lang.Double.longBitsToDouble(data.readU8())
	}

	fun getFloat(idx: Int): Float {
		jumpToData(idx)
		return java.lang.Float.intBitsToFloat(data.readU4())
	}

	fun readAsEncodedValue(idx: Int): EncodedValue {
		val constantType = jumpToConst(idx)
		return when (constantType) {
			ConstantType.UTF8 -> EncodedValue(EncodedType.ENCODED_STRING, readString())
			ConstantType.STRING -> EncodedValue(EncodedType.ENCODED_STRING, getUtf8(readU2()))
			ConstantType.INTEGER -> EncodedValue(EncodedType.ENCODED_INT, data.readS4())
			ConstantType.FLOAT -> EncodedValue(EncodedType.ENCODED_FLOAT, java.lang.Float.intBitsToFloat(data.readU4()))
			ConstantType.LONG -> EncodedValue(EncodedType.ENCODED_LONG, data.readS8())
			ConstantType.DOUBLE -> EncodedValue(EncodedType.ENCODED_DOUBLE, java.lang.Double.longBitsToDouble(data.readU8()))
			ConstantType.CLASS -> EncodedValue(EncodedType.ENCODED_TYPE, getClass(idx))
			ConstantType.METHOD_TYPE -> EncodedValue(EncodedType.ENCODED_METHOD_TYPE, DescriptorParser.parseToMethodProto(checkNotNull(getUtf8(readU2()))))
			ConstantType.METHOD_HANDLE -> EncodedValue(EncodedType.ENCODED_METHOD_HANDLE, getMethodHandle(idx))
			else -> throw JavaClassParseException("Can't encode constant " + constantType + " as encoded value")
		}
	}

	@NotNull
	private fun parseString(bytes: ByteArray): String = ModifiedUTF8Decoder.decodeString(bytes)

	private fun fixType(clsName: String): String {
		when (clsName[0]) {
			'[' -> return clsName

			'L', 'T' ->
				if (clsName.endsWith(";")) {
					return clsName
				}
		}
		return 'L' + clsName + ';'
	}

	private fun jumpToData(idx: Int) {
		data.absPos(offsets.getOffsetOfConstEntry(idx))
	}

	private fun jumpToTag(idx: Int) {
		data.absPos(offsets.getOffsetOfConstEntry(idx) - 1)
	}
}
