package jadx.plugins.input.java.data.attributes.types

import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.types.AnnotationDefaultAttr
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.EncodedValueReader
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType

/**
 * AnnotationDefault attribute：@interface 方法上的默认值。
 *
 **做什么**：解析注解方法的默认值元素，继承通用 [AnnotationDefaultAttr] 并挂上
 * IJavaAttribute 标记以便进入 java-input 的属性存储体系。
 */
class JavaAnnotationDefaultAttr(value: EncodedValue) :
	AnnotationDefaultAttr(value),
	IJavaAttribute {

	companion object {
		/** @return 读取器：直接委托 [EncodedValueReader] 读一个元素值 */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute = JavaAnnotationDefaultAttr(EncodedValueReader.read(clsData, reader))
		}

		/** 从属性存储中取出本属性；未解析时返回 null */
		fun convert(attributes: JavaAttrStorage): AnnotationDefaultAttr? = attributes.get(JavaAttrType.ANNOTATION_DEFAULT)
	}
}
