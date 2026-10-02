package jadx.core.xmlgen

import jadx.api.ICodeInfo
import jadx.api.ICodeWriter
import jadx.core.xmlgen.entry.ResourceEntry
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.NumberFormat
import java.util.HashSet
import java.util.Locale

/**
 * XML 生成与资源值解码的通用工具。
 *
 * 全部为静态方法，使用 `object` + `@JvmStatic` 保持 Java 调用 `XmlGenUtils.xxx(...)` 不变。
 */
object XmlGenUtils {

	/** 读取输入流全部字节。 */
	@JvmStatic
	@Throws(IOException::class)
	fun readData(i: InputStream): ByteArray {
		val buffer = ByteArrayOutputStream()
		val data = ByteArray(16384)
		while (true) {
			val read = i.read(data, 0, data.size)
			if (read == -1) {
				break
			}
			buffer.write(data, 0, read)
		}
		return buffer.toByteArray()
	}

	/** 生成 `public.xml` 形式的资源 id 清单。 */
	@JvmStatic
	fun makeXmlDump(writer: ICodeWriter, resStorage: ResourceStorage): ICodeInfo {
		writer.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
		writer.startLine("<resources>")
		writer.incIndent()

		val addedValues = HashSet<String>()
		for (ri in resStorage.resources) {
			if (addedValues.add(ri.getTypeName() + '.' + ri.getKeyName())) {
				val format = String.format(
					"<public type=\"%s\" name=\"%s\" id=\"0x%08x\" />",
					ri.getTypeName(),
					ri.getKeyName(),
					ri.getId(),
				)
				writer.startLine(format)
			}
		}
		writer.decIndent()
		writer.startLine("</resources>")
		return writer.finish()
	}

	/**
	 * 解码 `TYPE_DIMENSION` / `TYPE_FRACTION` 的“复数”编码。
	 *
	 * 32 位布局：位 0-3 单位、位 4-5 基数（radix）、位 8-31 尾数（带符号）。
	 * 尾数已在高位，故先与 `MANTISSA_MASK << MANTISSA_SHIFT` 相与，再乘 `RADIX_MULTS[radix]`。
	 */
	@JvmStatic
	fun decodeComplex(data: Int, isFraction: Boolean): String {
		var value = (
			data and (ParserConstants.COMPLEX_MANTISSA_MASK shl ParserConstants.COMPLEX_MANTISSA_SHIFT)
			) *
			ParserConstants.RADIX_MULTS[
				(data shr ParserConstants.COMPLEX_RADIX_SHIFT) and ParserConstants.COMPLEX_RADIX_MASK,
			]
		val unitType = data and ParserConstants.COMPLEX_UNIT_MASK
		val unit: String
		if (isFraction) {
			value *= 100
			unit = when (unitType) {
				ParserConstants.COMPLEX_UNIT_FRACTION -> "%"
				ParserConstants.COMPLEX_UNIT_FRACTION_PARENT -> "%p"
				else -> "?f" + Integer.toHexString(unitType)
			}
		} else {
			unit = when (unitType) {
				ParserConstants.COMPLEX_UNIT_PX -> "px"
				ParserConstants.COMPLEX_UNIT_DIP -> "dp"
				ParserConstants.COMPLEX_UNIT_SP -> "sp"
				ParserConstants.COMPLEX_UNIT_PT -> "pt"
				ParserConstants.COMPLEX_UNIT_IN -> "in"
				ParserConstants.COMPLEX_UNIT_MM -> "mm"
				else -> "?d" + Integer.toHexString(unitType)
			}
		}
		return doubleToString(value) + unit
	}

	/** double 转字符串：整数去掉小数，其余最多保留 4 位小数。 */
	@JvmStatic
	fun doubleToString(value: Double): String {
		if (java.lang.Double.compare(value, Math.floor(value)) == 0 &&
			!java.lang.Double.isInfinite(value)
		) {
			return value.toInt().toString()
		}
		// remove trailing zeroes
		val f = NumberFormat.getInstance(Locale.ROOT)
		f.maximumFractionDigits = 4
		f.minimumIntegerDigits = 1
		return f.format(value)
	}

	@JvmStatic
	fun floatToString(value: Float): String = doubleToString(value.toDouble())

	/** 把 `attr` 的 format 位掩码转成 `reference|string|...` 形式；无匹配返回 null。 */
	@JvmStatic
	fun getAttrTypeAsString(type: Int): String? {
		var s = ""
		if ((type and ParserConstants.ATTR_TYPE_REFERENCE) != 0) {
			s += "|reference"
		}
		if ((type and ParserConstants.ATTR_TYPE_STRING) != 0) {
			s += "|string"
		}
		if ((type and ParserConstants.ATTR_TYPE_INTEGER) != 0) {
			s += "|integer"
		}
		if ((type and ParserConstants.ATTR_TYPE_BOOLEAN) != 0) {
			s += "|boolean"
		}
		if ((type and ParserConstants.ATTR_TYPE_COLOR) != 0) {
			s += "|color"
		}
		if ((type and ParserConstants.ATTR_TYPE_FLOAT) != 0) {
			s += "|float"
		}
		if ((type and ParserConstants.ATTR_TYPE_DIMENSION) != 0) {
			s += "|dimension"
		}
		if ((type and ParserConstants.ATTR_TYPE_FRACTION) != 0) {
			s += "|fraction"
		}
		if (s.isEmpty()) {
			return null
		}
		return s.substring(1)
	}
}
