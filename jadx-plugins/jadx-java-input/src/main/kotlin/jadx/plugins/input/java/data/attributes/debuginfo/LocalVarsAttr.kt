package jadx.plugins.input.java.data.attributes.debuginfo

import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import java.util.ArrayList

/**
 * LocalVariableTable attribute：局部变量名与描述符表。
 *
 **做什么**：把"数量 + N 个(起始偏移, 长度, 名字索引, 类型索引, 变量号)"序列
 * 读成 [JavaLocalVar] 列表（sign 置 null，泛型签名由 LocalVariableTypeTable 补充）。
 */
class LocalVarsAttr(
	/** 解析出的局部变量列表 */
	val vars: List<JavaLocalVar>,
) : IJavaAttribute {

	companion object {
		/** @return 读取器：逐条读五元组并查常量池还原名字/描述符 */
		@JvmStatic
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val constPool: ConstPoolReader = clsData.constPoolReader
				val count = reader.readU2()
				val varsList = ArrayList<JavaLocalVar>(count)
				for (i in 0 until count) {
					val startOffset = reader.readU2()
					val length = reader.readU2()
					val endOffset = startOffset + length - 1
					val nameIdx = reader.readU2()
					val typeIdx = reader.readU2()
					val varNum = reader.readU2()
					varsList.add(
						JavaLocalVar(
							varNum,
							constPool.getUtf8(nameIdx),
							constPool.getUtf8(typeIdx),
							null,
							startOffset,
							endOffset,
						),
					)
				}
				return LocalVarsAttr(varsList)
			}
		}
	}
}
