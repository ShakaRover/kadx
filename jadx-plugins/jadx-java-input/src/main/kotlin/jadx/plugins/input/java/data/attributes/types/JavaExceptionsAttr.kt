package jadx.plugins.input.java.data.attributes.types

import jadx.api.plugins.input.data.attributes.types.ExceptionsAttr
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader

/**
 * Exceptions attribute：方法声明的受检异常列表。
 *
 **做什么**：把异常类名索引列表读成内部形式类名，继承通用 [ExceptionsAttr]
 * 并挂上 IJavaAttribute 标记进入 java-input 的属性存储体系。
 */
class JavaExceptionsAttr(list: List<String>) :
	ExceptionsAttr(list),
	IJavaAttribute {

	companion object {
		/** @return 读取器：读"数量 + N 个类名索引"序列 */
		@JvmStatic
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				// readClassesList 元素理论上可空（损坏 class），与原 Java 一样直接透传
				@Suppress("UNCHECKED_CAST")
				return JavaExceptionsAttr(reader.readClassesList(clsData.getConstPoolReader()) as List<String>)
			}
		}
	}
}
