package kadx.plugins.input.java.data.attributes.types

import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.JavaClassData
import kadx.plugins.input.java.data.attributes.IJavaAttribute
import kadx.plugins.input.java.data.attributes.IJavaAttributeReader

/**
 * ConstantValue attribute：字段（static final）的常量值。
 *
 **做什么**：把常量池索引读成 [EncodedValue] 存起来，供后续生成 `= 常量` 初始化表达式。
 */
class ConstValueAttr(
	/** 常量的编码形式（类型 + 实际值） */
	val value: EncodedValue,
) : IJavaAttribute {

	companion object {
		/** @return 读取器：读一个 u2 常量池索引并按其类型解码为 [EncodedValue] */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute = ConstValueAttr(clsData.constPoolReader.readAsEncodedValue(reader.readU2()))
		}
	}
}
