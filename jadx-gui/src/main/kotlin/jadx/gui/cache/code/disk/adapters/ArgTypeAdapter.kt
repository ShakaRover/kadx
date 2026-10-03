package jadx.gui.cache.code.disk.adapters

import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.exceptions.JadxRuntimeException
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [ArgType] 的二进制适配器。
 *
 * **做什么**：把类型系统里的各种形态（未知类型、基本类型、数组、对象、通配符、
 * 泛型对象、类型变量、外部类泛型）编码成带标签的二进制。每个值先写 1 字节类型标签
 * [Types]，再写该形态的数据。
 *
 * **格式契约（持久化格式，禁止变更标签顺序）**：标签值就是 [Types] 枚举的 ordinal，
 * 顺序 `NULL, UNKNOWN, PRIMITIVE, ARRAY, OBJECT, WILDCARD, GENERIC, TYPE_VARIABLE, OUTER_GENERIC`
 * 必须保持不变；泛型列表长度用 1 字节表示。
 *
 * **可空语义**：`null` 表示 NULL 类型（序列化时写 NULL 标签，读取时还原为 `null`），
 * 因此本类实现 `DataAdapter<ArgType?>`。
 */
class ArgTypeAdapter : DataAdapter<ArgType?> {

	/** 类型标签，ordinal 直接写入磁盘，顺序不可调整。 */
	private enum class Types {
		NULL,
		UNKNOWN,
		PRIMITIVE,
		ARRAY,
		OBJECT,
		WILDCARD,
		GENERIC,
		TYPE_VARIABLE,
		OUTER_GENERIC,
	}

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: ArgType?) {
		if (value == null) {
			writeType(out, Types.NULL)
			return
		}
		if (!value.isTypeKnown()) {
			writeType(out, Types.UNKNOWN)
			return
		}
		if (value.isPrimitive()) {
			writeType(out, Types.PRIMITIVE)
			out.writeByte(checkNotNull(value.getPrimitiveType()).shortName[0].code)
			return
		}
		val outerType = value.getOuterType()
		if (outerType != null) {
			writeType(out, Types.OUTER_GENERIC)
			write(out, outerType)
			write(out, value.getInnerType())
			return
		}
		val wildcardType = value.getWildcardType()
		if (wildcardType != null) {
			writeType(out, Types.WILDCARD)
			val bound = checkNotNull(value.getWildcardBound())
			out.writeByte(bound.num)
			if (bound != ArgType.WildcardBound.UNBOUND) {
				write(out, wildcardType)
			}
			return
		}
		if (value.isGeneric()) {
			writeType(out, Types.GENERIC)
			out.writeUTF(value.getObject())
			writeTypesList(out, checkNotNull(value.getGenericTypes()))
			return
		}
		if (value.isGenericType()) {
			writeType(out, Types.TYPE_VARIABLE)
			out.writeUTF(value.getObject())
			writeTypesList(out, value.getExtendTypes())
			return
		}
		if (value.isObject()) {
			writeType(out, Types.OBJECT)
			out.writeUTF(value.getObject())
			return
		}
		if (value.isArray()) {
			writeType(out, Types.ARRAY)
			out.writeByte(value.getArrayDimension())
			write(out, value.getArrayRootElement())
			return
		}
		throw JadxRuntimeException("Cannot save type: $value, cls: ${value.javaClass}")
	}

	@Throws(IOException::class)
	private fun writeType(out: DataOutput, type: Types) {
		out.writeByte(type.ordinal)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): ArgType? {
		val typeOrdinal = input.readByte().toInt()
		val type = Types.entries[typeOrdinal]
		return when (type) {
			Types.NULL -> null

			Types.UNKNOWN -> ArgType.UNKNOWN

			Types.PRIMITIVE -> {
				val shortName = input.readByte().toInt().toChar()
				ArgType.parse(shortName)
			}

			Types.OUTER_GENERIC -> {
				val outerType = read(input)
				val innerType = read(input)
				ArgType.outerGeneric(checkNotNull(outerType), checkNotNull(innerType))
			}

			Types.WILDCARD -> {
				val bound = ArgType.WildcardBound.getByNum(input.readByte().toInt())
				if (bound == ArgType.WildcardBound.UNBOUND) {
					ArgType.WILDCARD
				} else {
					val objType = read(input)
					ArgType.wildcard(checkNotNull(objType), bound)
				}
			}

			Types.GENERIC -> {
				val clsType = input.readUTF()
				ArgType.generic(clsType, readTypesList(input))
			}

			Types.TYPE_VARIABLE -> {
				val typeVar = input.readUTF()
				val extendTypes = readTypesList(input)
				ArgType.genericType(typeVar, extendTypes)
			}

			Types.OBJECT -> ArgType.`object`(input.readUTF())

			Types.ARRAY -> {
				val dim = input.readByte().toInt()
				val rootType = read(input)
				ArgType.array(checkNotNull(rootType), dim)
			}
		}
	}

	@Throws(IOException::class)
	private fun writeTypesList(out: DataOutput, types: List<ArgType>) {
		out.writeByte(types.size)
		for (type in types) {
			write(out, type)
		}
	}

	@Throws(IOException::class)
	private fun readTypesList(input: DataInput): List<ArgType> {
		val size = input.readByte().toInt()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<ArgType>(size)
		for (i in 0 until size) {
			list.add(checkNotNull(read(input)))
		}
		return list
	}

	companion object {
		val INSTANCE = ArgTypeAdapter()
	}
}
