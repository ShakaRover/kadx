package jadx.plugins.input.java.data.attributes.types

import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader

/**
 * Code attribute 的占位数据：只记录 code 区在 class 文件中的偏移。
 *
 **做什么**：真正的字节码解析由 [jadx.plugins.input.java.data.code.JavaCodeReader]
 * 按这个偏移跳过去完成；这里只是把"代码在哪"存进属性表。
 */
class CodeAttr(
	/** code 区数据起始偏移（相对 class 文件开头） */
	val offset: Int,
) : IJavaAttribute {

	companion object {
		/** @return 读取器：只记录当前读头位置，不消费任何字节 */
		@JvmStatic
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute = CodeAttr(reader.offset)
		}
	}
}
