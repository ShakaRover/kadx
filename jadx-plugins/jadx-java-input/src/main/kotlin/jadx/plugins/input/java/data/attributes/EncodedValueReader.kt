package jadx.plugins.input.java.data.attributes

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.impl.JadxFieldRef
import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr
import jadx.plugins.input.java.utils.JavaClassParseException
import java.util.ArrayList

/**
 * 注解元素值（element_value）读取器。
 *
 **做什么**：按 JVMS §4.7.16 的 tag 字符把字节流解析成 [EncodedValue]——
 * 基本类型直接读常量池，'e' 是枚举常量（字段引用），'@' 是嵌套注解，'[' 是数组。
 *
 **为什么递归**：注解值可以是嵌套注解或任意深度的数组，结构天然递归，
 * read() 在 '@' 和 '[' 分支里调用自身完成下钻。
 */
object EncodedValueReader {

	/**
	 * 从 [reader] 当前位置读取一个注解元素值。
	 * @throws JavaClassParseException tag 字符不是合法的 element_value 类型时抛出
	 */
	@JvmStatic
	fun read(clsData: JavaClassData, reader: DataReader): EncodedValue {
		val constPool: ConstPoolReader = clsData.constPoolReader
		val tag = reader.readU1().toChar()
		return when (tag) {
			'B' -> EncodedValue(EncodedType.ENCODED_BYTE, constPool.getInt(reader.readU2()).toByte())

			'C' -> EncodedValue(EncodedType.ENCODED_CHAR, constPool.getInt(reader.readU2()).toChar())

			'D' -> EncodedValue(EncodedType.ENCODED_DOUBLE, constPool.getDouble(reader.readU2()))

			'F' -> EncodedValue(EncodedType.ENCODED_FLOAT, constPool.getFloat(reader.readU2()))

			'I' -> EncodedValue(EncodedType.ENCODED_INT, constPool.getInt(reader.readU2()))

			'J' -> EncodedValue(EncodedType.ENCODED_LONG, constPool.getLong(reader.readU2()))

			'S' -> EncodedValue(EncodedType.ENCODED_SHORT, constPool.getInt(reader.readU2()).toShort())

			'Z' -> EncodedValue(EncodedType.ENCODED_BOOLEAN, 1 == constPool.getInt(reader.readU2()))

			's' -> EncodedValue(EncodedType.ENCODED_STRING, constPool.getUtf8(reader.readU2()))

			'e' -> {
				val cls = constPool.getUtf8(reader.readU2())
				val name = constPool.getUtf8(reader.readU2())
				// 枚举常量编码为"所属类 + 字段名"的字段引用（第三个参数是签名占位）
				EncodedValue(EncodedType.ENCODED_ENUM, JadxFieldRef(cls, name, cls))
			}

			'c' -> EncodedValue(EncodedType.ENCODED_TYPE, constPool.getUtf8(reader.readU2()))

			'@' -> EncodedValue(
				EncodedType.ENCODED_ANNOTATION,
				JavaAnnotationsAttr.readAnnotation(AnnotationVisibility.RUNTIME, clsData, reader),
			)

			'[' -> {
				val len = reader.readU2()
				val values = ArrayList<EncodedValue>(len)
				for (i in 0 until len) {
					values.add(read(clsData, reader))
				}
				EncodedValue(EncodedType.ENCODED_ARRAY, values)
			}

			else -> throw JavaClassParseException("Unknown element value tag: " + tag)
		}
	}
}
