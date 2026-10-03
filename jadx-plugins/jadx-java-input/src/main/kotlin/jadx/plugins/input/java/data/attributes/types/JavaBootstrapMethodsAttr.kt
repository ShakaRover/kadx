package jadx.plugins.input.java.data.attributes.types

import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import jadx.plugins.input.java.data.attributes.types.data.RawBootstrapMethod
import java.util.ArrayList

/**
 * BootstrapMethods attribute：invoke-dynamic 指令的引导方法表。
 *
 **做什么**：读取"数量 + N 个(method handle 索引, args 数量 + args 索引)"序列，
 * 存为原始 [RawBootstrapMethod]（延迟到真正需要时再解析成对象）。
 */
class JavaBootstrapMethodsAttr(
	/** bootstrap method 条目列表 */
	val list: List<RawBootstrapMethod>,
) : IJavaAttribute {

	companion object {
		/** @return 读取器：逐条读 method handle 索引与 args 索引数组 */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val len = reader.readU2()
				val list = ArrayList<RawBootstrapMethod>(len)
				for (i in 0 until len) {
					val methodHandleIdx = reader.readU2()
					val argsCount = reader.readU2()
					val args = IntArray(argsCount)
					for (j in 0 until argsCount) {
						args[j] = reader.readU2()
					}
					list.add(RawBootstrapMethod(methodHandleIdx, args))
				}
				return JavaBootstrapMethodsAttr(list)
			}
		}
	}
}
