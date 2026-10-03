package jadx.plugins.input.java.data.attributes.debuginfo

import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import java.util.ArrayList

/**
 * LocalVariableTypeTable attribute：局部变量的泛型签名表。
 *
 **做什么**：结构与 [LocalVarsAttr] 相同，但 typeIdx 位置存的是泛型签名而非描述符；
 * 解析出的变量 type=null、sign=签名字符串，由 [JavaCodeReader] 按 (偏移, 寄存器) 合并进主表。
 */
class LocalVarTypesAttr(
	/** 解析出的局部变量列表（携带泛型签名） */
	val vars: List<JavaLocalVar>,
) : IJavaAttribute {

	companion object {
		/** @return 读取器：逐条读五元组，类型位置取的是签名 */
		@JvmStatic
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val constPool: ConstPoolReader = clsData.constPoolReader
				val len = reader.readU2()
				val varsList = ArrayList<JavaLocalVar>(len)
				for (i in 0 until len) {
					val startOffset = reader.readU2()
					val endOffset = startOffset + reader.readU2() - 1
					val nameIdx = reader.readU2()
					val typeIdx = reader.readU2()
					val varNum = reader.readU2()
					varsList.add(
						JavaLocalVar(
							varNum,
							constPool.getUtf8(nameIdx),
							null,
							constPool.getUtf8(typeIdx),
							startOffset,
							endOffset,
						),
					)
				}
				return LocalVarTypesAttr(varsList)
			}
		}
	}
}
