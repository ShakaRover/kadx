package jadx.plugins.input.dex.sections.annotations

import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.plugins.input.dex.DexException
import jadx.plugins.input.dex.sections.SectionReader
import java.util.ArrayList

// DEX 编码值类型标签（encoded_value 的低 5 位），见 DEX 格式文档 Table 4-13
private const val ENCODED_BYTE = 0x00
private const val ENCODED_SHORT = 0x02
private const val ENCODED_CHAR = 0x03
private const val ENCODED_INT = 0x04
private const val ENCODED_LONG = 0x06
private const val ENCODED_FLOAT = 0x10
private const val ENCODED_DOUBLE = 0x11
private const val ENCODED_METHOD_TYPE = 0x15
private const val ENCODED_METHOD_HANDLE = 0x16
private const val ENCODED_STRING = 0x17
private const val ENCODED_TYPE = 0x18
private const val ENCODED_FIELD = 0x19
private const val ENCODED_ENUM = 0x1b
private const val ENCODED_METHOD = 0x1a
private const val ENCODED_ARRAY = 0x1c
private const val ENCODED_ANNOTATION = 0x1d
private const val ENCODED_NULL = 0x1e
private const val ENCODED_BOOLEAN = 0x1f

/**
 * DEX encoded_value 解析器：把注解属性值字节流还原为 [EncodedValue]。
 *
 * **背景**：[AnnotationsParser] 读取 annotation 条目时逐属性调用本类；
 * call_site 数据（invoke-custom）也复用 [parseEncodedArray]。
 */
public class EncodedValueParser {

	companion object {
		/**
		 * 解析单个 encoded_value（含类型头字节）。
		 * @param in annotation section 读取器（停在值头部之前）
		 * @param ext 外部 section 读取器（查字符串/类型/引用池）
		 * @throws DexException 遇到未知类型标签
		 */
		@JvmStatic
		public fun parseValue(reader: SectionReader, ext: SectionReader): EncodedValue {
			val argAndType = reader.readUByte()
			val type = argAndType and 0x1F
			val arg = (argAndType and 0xE0) ushr 5
			val size = arg + 1

			return when (type) {
				ENCODED_NULL -> EncodedValue.NULL

				ENCODED_BOOLEAN -> EncodedValue(EncodedType.ENCODED_BOOLEAN, arg == 1)

				ENCODED_BYTE -> EncodedValue(EncodedType.ENCODED_BYTE, reader.readByte())

				ENCODED_SHORT -> EncodedValue(EncodedType.ENCODED_SHORT, parseNumber(reader, size, true).toShort())

				ENCODED_CHAR -> EncodedValue(EncodedType.ENCODED_CHAR, parseUnsignedInt(reader, size).toChar())

				ENCODED_INT -> EncodedValue(EncodedType.ENCODED_INT, parseNumber(reader, size, true).toInt())

				ENCODED_LONG -> EncodedValue(EncodedType.ENCODED_LONG, parseNumber(reader, size, true))

				ENCODED_FLOAT -> EncodedValue(EncodedType.ENCODED_FLOAT, Float.fromBits(parseNumber(reader, size, false, 4).toInt()))

				ENCODED_DOUBLE -> EncodedValue(EncodedType.ENCODED_DOUBLE, Double.fromBits(parseNumber(reader, size, false, 8)))

				ENCODED_STRING -> EncodedValue(EncodedType.ENCODED_STRING, ext.getString(parseUnsignedInt(reader, size)))

				ENCODED_TYPE -> EncodedValue(EncodedType.ENCODED_TYPE, ext.getType(parseUnsignedInt(reader, size)))

				ENCODED_FIELD, ENCODED_ENUM ->
					EncodedValue(EncodedType.ENCODED_FIELD, ext.getFieldRef(parseUnsignedInt(reader, size)))

				ENCODED_ARRAY -> EncodedValue(EncodedType.ENCODED_ARRAY, parseEncodedArray(reader, ext))

				ENCODED_ANNOTATION ->
					EncodedValue(EncodedType.ENCODED_ANNOTATION, AnnotationsParser.readAnnotation(reader, ext, false))

				ENCODED_METHOD -> EncodedValue(EncodedType.ENCODED_METHOD, ext.getMethodRef(parseUnsignedInt(reader, size)))

				ENCODED_METHOD_TYPE ->
					EncodedValue(EncodedType.ENCODED_METHOD_TYPE, ext.getMethodProto(parseUnsignedInt(reader, size)))

				ENCODED_METHOD_HANDLE ->
					EncodedValue(EncodedType.ENCODED_METHOD_HANDLE, ext.getMethodHandle(parseUnsignedInt(reader, size)))

				else -> throw DexException("Unknown encoded value type: 0x" + Integer.toHexString(type))
			}
		}

		/**
		 * 解析 encoded_array：uleb128 计数 + 逐个元素。
		 */
		@JvmStatic
		public fun parseEncodedArray(reader: SectionReader, ext: SectionReader): List<EncodedValue> {
			val count = reader.readUleb128()
			val values = ArrayList<EncodedValue>(count)
			for (i in 0 until count) {
				values.add(parseValue(reader, ext))
			}
			return values
		}

		private fun parseUnsignedInt(reader: SectionReader, byteCount: Int): Int = parseNumber(reader, byteCount, false, 0).toInt()

		private fun parseNumber(reader: SectionReader, byteCount: Int, isSignExtended: Boolean): Long = parseNumber(reader, byteCount, isSignExtended, 0)

		/**
		 * 按小端序读取 [byteCount] 字节数值。
		 * @param fillOnRight 非 0 时把结果左移补齐到该字节数（float/double 用）；
		 *        为 0 且 [isSignExtended] 时按最高位做符号扩展
		 */
		private fun parseNumber(reader: SectionReader, byteCount: Int, isSignExtended: Boolean, fillOnRight: Int): Long {
			var result = 0L
			var last = 0L
			for (i in 0 until byteCount) {
				last = reader.readUByte().toLong()
				result = result or (last shl (i * 8))
			}
			if (fillOnRight != 0) {
				for (i in byteCount until fillOnRight) {
					result = result shl 8
				}
			} else if (isSignExtended && (last and 0x80L) != 0L) {
				for (i in byteCount until 8) {
					result = result or (0xFFL shl (i * 8))
				}
			}
			return result
		}
	}
}
