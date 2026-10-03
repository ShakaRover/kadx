package jadx.core.xmlgen.entry

import jadx.core.utils.android.AndroidResourcesMap
import jadx.core.xmlgen.BinaryXMLStrings
import jadx.core.xmlgen.ParserConstants
import jadx.core.xmlgen.XmlGenUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList

/**
 * 资源值解码器：把 [RawValue] / [ProtoValue] 转成 XML 中的字符串表示。
 *
 * [resMap] 是 `id -> "type/name"` 映射（来自 [jadx.core.xmlgen.ResourceStorage]），
 * 用于把引用类值解码为 `@type/name` 形式；[strings] 为字符串池（测试中可为 null）。
 */
class ValuesParser(
	private val strings: BinaryXMLStrings?,
	private val resMap: Map<Int, String>,
) : ParserConstants() {

	/** 取“简单值”字符串；复杂值（有 namedValues）返回 null。 */
	fun getSimpleValueString(ri: ResourceEntry): String? {
		val protoValue = ri.protoValue
		if (protoValue != null) {
			return protoValue.value
		}
		val simpleValue = ri.simpleValue ?: return null
		return decodeValue(simpleValue)
	}

	/** 取完整值字符串：复杂值以 `name=value` 列表形式输出。 */
	fun getValueString(ri: ResourceEntry): String? {
		val protoValue = ri.protoValue
		if (protoValue != null) {
			if (protoValue.value != null) {
				return protoValue.value
			}
			val values = checkNotNull(protoValue.namedValues)
			val strList = ArrayList<String?>(values.size)
			for (value in values) {
				if (value.name == null) {
					strList.add(value.value)
				} else {
					strList.add(value.name + '=' + value.value)
				}
			}
			return strList.toString()
		}
		val simpleValue = ri.simpleValue
		if (simpleValue != null) {
			return decodeValue(simpleValue)
		}
		val namedValues = checkNotNull(ri.namedValues)
		val strList = ArrayList<String?>(namedValues.size)
		for (value in namedValues) {
			val nameStr = decodeNameRef(value.nameRef)
			val valueStr = decodeValue(value.rawValue)
			if (nameStr == null) {
				strList.add(valueStr)
			} else {
				strList.add(nameStr + '=' + valueStr)
			}
		}
		return strList.toString()
	}

	fun decodeValue(value: RawValue): String? = decodeValue(value.dataType, value.data)

	fun decodeValue(dataType: Int, data: Int): String? {
		return when (dataType) {
			ParserConstants.TYPE_NULL -> null

			ParserConstants.TYPE_STRING -> checkNotNull(strings).get(data)

			ParserConstants.TYPE_INT_DEC -> Integer.toString(data)

			ParserConstants.TYPE_INT_HEX -> "0x" + Integer.toHexString(data)

			ParserConstants.TYPE_INT_BOOLEAN -> if (data == 0) "false" else "true"

			ParserConstants.TYPE_FLOAT -> XmlGenUtils.floatToString(java.lang.Float.intBitsToFloat(data))

			ParserConstants.TYPE_INT_COLOR_ARGB8 -> String.format("#%08x", data)

			ParserConstants.TYPE_INT_COLOR_RGB8 -> String.format("#%06x", data and 0xFFFFFF)

			ParserConstants.TYPE_INT_COLOR_ARGB4 -> String.format("#%04x", data and 0xFFFF)

			ParserConstants.TYPE_INT_COLOR_RGB4 -> String.format("#%03x", data and 0xFFF)

			ParserConstants.TYPE_DYNAMIC_REFERENCE,
			ParserConstants.TYPE_REFERENCE,
			-> {
				val ri = resMap[data]
				if (ri == null) {
					val androidRi = AndroidResourcesMap.getResName(data)
					if (androidRi != null) {
						return "@android:$androidRi"
					}
					if (data == 0) {
						return "0"
					}
					return "?unknown_ref: " + Integer.toHexString(data)
				}
				'@' + ri
			}

			ParserConstants.TYPE_ATTRIBUTE -> {
				val ri = resMap[data]
				if (ri == null) {
					val androidRi = AndroidResourcesMap.getResName(data)
					if (androidRi != null) {
						return "?android:$androidRi"
					}
					return "?unknown_attr_ref: " + Integer.toHexString(data)
				}
				'?' + ri
			}

			ParserConstants.TYPE_DIMENSION -> XmlGenUtils.decodeComplex(data, false)

			ParserConstants.TYPE_FRACTION -> XmlGenUtils.decodeComplex(data, true)

			ParserConstants.TYPE_DYNAMIC_ATTRIBUTE -> {
				LOG.warn("Data type TYPE_DYNAMIC_ATTRIBUTE not yet supported: {}", data)
				"  TYPE_DYNAMIC_ATTRIBUTE: $data"
			}

			else -> {
				LOG.warn("Unknown data type: 0x{} {}", Integer.toHexString(dataType), data)
				"  ?0x" + Integer.toHexString(dataType) + ' ' + data
			}
		}
	}

	/** 解码 `nameRef`：内部属性 id 先去掉内部前缀，再查资源映射。 */
	fun decodeNameRef(nameRef: Int): String? {
		var ref = nameRef
		if (ParserConstants.isResInternalId(nameRef)) {
			ref = nameRef and ParserConstants.ATTR_TYPE_ANY
			if (ref == 0) {
				return null
			}
		}
		val ri = resMap[ref]
		if (ri != null) {
			return ri.replace('/', '.')
		} else {
			val androidRi = AndroidResourcesMap.getResName(ref)
			if (androidRi != null) {
				return "android:" + androidRi.replace('/', '.')
			}
		}
		return "?0x" + Integer.toHexString(nameRef)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ValuesParser::class.java)
	}
}
