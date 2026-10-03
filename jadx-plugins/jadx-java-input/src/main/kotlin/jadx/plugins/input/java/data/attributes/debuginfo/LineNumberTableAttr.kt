package jadx.plugins.input.java.data.attributes.debuginfo

import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import java.util.HashMap

/**
 * LineNumberTable attribute：字节码偏移 → 源码行号映射。
 *
 **做什么**：把"数量 + N 个(偏移, 行号)"序列读成 map，供异常堆栈与断点定位显示行号。
 */
class LineNumberTableAttr(
	/** 字节码偏移 → 源码行号 */
	val lineMap: Map<Int, Int>,
) : IJavaAttribute {

	companion object {
		/** @return 读取器：逐条读 (start_pc, line_number) 对 */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val len = reader.readU2()
				val map = HashMap<Int, Int>(len)
				for (i in 0 until len) {
					val offset = reader.readU2()
					val line = reader.readU2()
					map[offset] = line
				}
				return LineNumberTableAttr(map)
			}
		}
	}
}
