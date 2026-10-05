package kadx.plugins.input.dex.sections

import kadx.api.plugins.input.data.ICallSite
import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.MethodHandleType
import kadx.api.plugins.input.data.impl.CallSite
import kadx.api.plugins.input.data.impl.FieldRefHandle
import kadx.api.plugins.input.data.impl.MethodRefHandle
import kadx.plugins.input.dex.DexReader
import kadx.plugins.input.dex.sections.annotations.EncodedValueParser
import kadx.plugins.input.dex.utils.Leb128
import kadx.plugins.input.dex.utils.MUtf8
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * DEX section 读取器：基于 [ByteBuffer] 游标的小端序二进制读取工具。
 *
 * **背景**：
 * 1. 构造时从 [DexReader.buf] 复制（duplicate）出一个独立游标的缓冲区，并定位到 section 偏移 [off]；
 *    [copy] / [copy(off)] 生成共享同一底层 buffer 的独立游标副本，供并行解析不同区域使用；
 * 2. [pos] 为相对本 section 起始（[offset]）的定位，[absPos] 为文件绝对定位；两者均返回 this 支持链式调用；
 * 3. 除基础字节读取外，还提供 DEX 引用表查询：类型/字符串池（MUTF-8）、字段引用、方法引用与原型、
 *    method handle / call site（invoke-custom）等。
 */
public class SectionReader(
	private val dexReaderValue: DexReader,
	off: Int,
) {

	public constructor(sectionReader: SectionReader, off: Int) : this(sectionReader.dexReader, off)

	/** 生成共享同一底层 buffer、游标相同的独立副本。 */
	public fun copy(): SectionReader = SectionReader(this, offset)

	/** 生成共享同一底层 buffer、定位到 [off] 的独立副本。 */
	public fun copy(off: Int): SectionReader = SectionReader(this, off)

	private val buf: ByteBuffer
	var offset: Int = off

	init {
		buf = duplicate(dexReaderValue.buf, off)
	}

	/**
	 * 从 [start]（相对 section）读取长度为 [len] 的字节数组，不改变游标位置。
	 */
	public fun getByteCode(start: Int, len: Int): ByteArray {
		val pos = buf.position()
		buf.position(start)
		val bytes = readByteArray(len)
		buf.position(pos)
		return bytes
	}

	private companion object {
		/** 从 [baseBuffer] 复制出独立游标缓冲区，设为小端序并定位到 [off]。 */
		private fun duplicate(baseBuffer: ByteBuffer, off: Int): ByteBuffer {
			val dupBuf = baseBuffer.duplicate()
			dupBuf.order(ByteOrder.LITTLE_ENDIAN)
			dupBuf.position(off)
			return dupBuf
		}
	}

	/** 更新 section 起始偏移（不移动游标）。属性 [offset] 自动生成 setOffset/getOffset。 */
	public fun shiftOffset(shift: Int) {
		offset += shift
	}

	/** 定位到 section 起始 + [pos]（相对偏移），返回 this 支持链式调用。 */
	public fun pos(pos: Int): SectionReader {
		buf.position(offset + pos)
		return this
	}

	/** 定位到文件绝对位置 [pos]，返回 this 支持链式调用。 */
	public fun absPos(pos: Int): SectionReader {
		buf.position(pos)
		return this
	}

	/** @return 当前游标的文件绝对位置 */
	public val absPos: Int get() = buf.position()

	/** 跳过 [skip] 字节（等价于 pos += skip）*/
	public fun skip(skip: Int) {
		val pos = buf.position()
		buf.position(pos + skip)
	}

	public fun readInt(): Int = buf.getInt()

	public fun readLong(): Long = buf.getLong()

	public fun readByte(): Byte = buf.get()

	/** 读取一个无符号字节（0..255）*/
	public fun readUByte(): Int = buf.get().toInt() and 0xFF

	/** 读取一个无符号短整型（0..65535，DEX u2）*/
	public fun readUShort(): Int = buf.getShort().toInt() and 0xFFFF

	/** 读取一个有符号短整型（DEX s2）*/
	public fun readShort(): Int = buf.getShort().toInt()

	public fun readByteArray(len: Int): ByteArray {
		val arr = ByteArray(len)
		buf.get(arr)
		return arr
	}

	/** 连续读取 [size] 个 u2，返回 int 数组（DEX type_ids / encoded_array 等场景）*/
	public fun readUShortArray(size: Int): IntArray {
		val arr = IntArray(size)
		for (i in 0 until size) {
			arr[i] = readUShort()
		}
		return arr
	}

	/** 读取 [len] 字节并按 US-ASCII 解码为字符串（DEX header magic / shorty 等）*/
	public fun readString(len: Int): String = String(readByteArray(len), StandardCharsets.US_ASCII)

	private fun readTypeListAt(paramsOff: Int): List<String> {
		if (paramsOff == 0) {
			return emptyList()
		}
		return absPos(paramsOff).readTypeList()
	}

	/**
	 * 读取一个 type_list：先读 u4 size，再依次读取 size 个 u2 type_idx。
	 */
	public fun readTypeList(): List<String> {
		val size = readInt()
		if (size == 0) {
			return emptyList()
		}
		val typeIds = readUShortArray(size)
		val types = ArrayList<String>(size)
		for (typeId in typeIds) {
			// type_list 中的索引必须有效（NO_INDEX 属于非法数据）
			types.add(checkNotNull(getType(typeId)) { "invalid type index: $typeId" })
		}
		return types
	}

	/**
	 * 按 type_idx 查询类型描述符（如 `Lcom/example/Foo;`）。
	 * @return idx 为 [DexConsts.NO_INDEX] 时返回 null
	 */
	public fun getType(idx: Int): String? {
		if (idx == DexConsts.NO_INDEX) {
			return null
		}
		val typeIdsOff = dexReaderValue.header.typeIdsOff
		absPos(typeIdsOff + idx * 4)
		val strIdx = readInt()
		return getString(strIdx)
	}

	/**
	 * 按 string_idx 查询字符串池内容（MUTF-8 解码）。
	 * @return idx 为 [DexConsts.NO_INDEX] 时返回 null
	 */
	public fun getString(idx: Int): String? {
		if (idx == DexConsts.NO_INDEX) {
			return null
		}
		// TODO: make string pool cache?
		val stringIdsOff = dexReaderValue.header.stringIdsOff
		absPos(stringIdsOff + idx * 4)
		val strOff = readInt()
		absPos(strOff)
		return MUtf8.decode(this)
	}

	public fun getFieldRef(idx: Int): IFieldRef {
		val fieldData = DexFieldData(null)
		val clsTypeIdx = fillFieldData(fieldData, idx)
		fieldData.setParentClassType(getType(clsTypeIdx))
		return fieldData
	}

	/**
	 * 从 field_ids section 填充 [fieldData]（类型 + 名称），返回所属类的 type_idx。
	 */
	public fun fillFieldData(fieldData: DexFieldData, idx: Int): Int {
		val fieldIdsOff = dexReaderValue.header.fieldIdsOff
		absPos(fieldIdsOff + idx * 8)
		val classTypeIdx = readUShort()
		val typeIdx = readUShort()
		val nameIdx = readInt()
		fieldData.setType(getType(typeIdx))
		fieldData.setName(getString(nameIdx))
		return classTypeIdx
	}

	public fun getMethodRef(idx: Int): DexMethodRef {
		val methodRef = DexMethodRef()
		initMethodRef(idx, methodRef)
		return methodRef
	}

	/** 读取 invoke-custom 的 call site（encoded_array 编码）。 */
	public fun getCallSite(idx: Int, ext: SectionReader): ICallSite {
		val callSiteOff = dexReaderValue.header.callSiteOff
		absPos(callSiteOff + idx * 4)
		absPos(readInt())
		return CallSite(EncodedValueParser.parseEncodedArray(this, ext))
	}

	/** 读取 method handle（接口方法引用 / 字段访问等，用于 invoke-polymorphic）。 */
	public fun getMethodHandle(idx: Int): IMethodHandle {
		val methodHandleOff = dexReaderValue.header.methodHandleOff
		absPos(methodHandleOff + idx * 8)
		val handleType = getMethodHandleType(readUShort())
		skip(2) // reserved0
		val refId = readUShort()
		if (handleType.isField) {
			return FieldRefHandle(handleType, getFieldRef(refId))
		}
		return MethodRefHandle(handleType, getMethodRef(refId))
	}

	private fun getMethodHandleType(type: Int): MethodHandleType = when (type) {
		0x00 -> MethodHandleType.STATIC_PUT
		0x01 -> MethodHandleType.STATIC_GET
		0x02 -> MethodHandleType.INSTANCE_PUT
		0x03 -> MethodHandleType.INSTANCE_GET
		0x04 -> MethodHandleType.INVOKE_STATIC
		0x05 -> MethodHandleType.INVOKE_INSTANCE
		0x06 -> MethodHandleType.INVOKE_CONSTRUCTOR
		0x07 -> MethodHandleType.INVOKE_DIRECT
		0x08 -> MethodHandleType.INVOKE_INTERFACE
		else -> throw IllegalArgumentException("Unknown method handle type: 0x" + Integer.toHexString(type))
	}

	public fun initMethodRef(idx: Int, methodRef: DexMethodRef) {
		methodRef.initUniqId(dexReaderValue, idx)
		methodRef.setDexIdx(idx)
		methodRef.setSectionReader(this)
	}

	/**
	 * 从 method_ids + proto_ids section 加载方法引用完整信息：
	 * 父类类型、方法名、返回类型与参数类型列表。
	 */
	public fun loadMethodRef(methodRef: DexMethodRef, idx: Int) {
		val header = dexReaderValue.header
		val methodIdsOff = header.methodIdsOff
		absPos(methodIdsOff + idx * 8)
		val classTypeIdx = readUShort()
		val protoIdx = readUShort()
		val nameIdx = readInt()

		val protoIdsOff = header.protoIdsOff
		absPos(protoIdsOff + protoIdx * 12)
		skip(4) // shortyIdx
		val returnTypeIdx = readInt()
		val paramsOff = readInt()

		val argTypes = readTypeListAt(paramsOff)
		methodRef.setParentClassType(getType(classTypeIdx))
		methodRef.setName(getString(nameIdx))
		methodRef.setReturnType(getType(returnTypeIdx))
		methodRef.setArgTypes(argTypes)
	}

	public fun getMethodProto(idx: Int): DexMethodProto {
		val protoIdsOff = dexReaderValue.header.protoIdsOff
		absPos(protoIdsOff + idx * 12)
		skip(4) // shortyIdx
		val returnTypeIdx = readInt()
		val paramsOff = readInt()
		return DexMethodProto(readTypeListAt(paramsOff), getType(returnTypeIdx))
	}

	/** @return 方法 [idx] 的参数类型列表（经 method_ids → proto_ids 两级索引）*/
	public fun getMethodParamTypes(idx: Int): List<String> {
		val header = dexReaderValue.header
		val methodIdsOff = header.methodIdsOff
		absPos(methodIdsOff + idx * 8 + 2)
		val protoIdx = readUShort()

		val protoIdsOff = header.protoIdsOff
		absPos(protoIdsOff + protoIdx * 12 + 8)
		val paramsOff = readInt()

		if (paramsOff == 0) {
			return emptyList()
		}
		return absPos(paramsOff).readTypeList()
	}

	public val dexReader: DexReader get() = dexReaderValue

	/** 读取 ULEB128（无符号 LEB128，DEX 长度/索引编码）*/
	public fun readUleb128(): Int = Leb128.readUnsignedLeb128(this)

	/** 读取 ULEB128p1：ULEB128 值减 1（DBG_START_LOCAL_EXTENDED / 参数名等 "0=无" 编码）*/
	public fun readUleb128p1(): Int = Leb128.readUnsignedLeb128(this) - 1

	/** 读取 SLEB128（有符号 LEB128，DEX 行号增量等）*/
	public fun readSleb128(): Int = Leb128.readSignedLeb128(this)

	/** @return 底层 buffer 容量（即本 DEX 文件大小）*/
	public fun size(): Int = buf.capacity()

	override fun toString(): String = "SectionReader{buf=$buf, offset=$offset}"
}
