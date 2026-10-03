package jadx.plugins.input.java.data.attributes.types

import jadx.api.plugins.input.data.attributes.types.MethodParametersAttr
import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import java.util.ArrayList

/**
 * MethodParameters attribute：方法/构造器参数名与标志（-parameters 编译选项生成）。
 *
 **做什么**：读取"数量 + N 个(名字索引, 访问标志)"序列，还原真实参数名
 * （否则只能显示 arg0/arg1）；继承通用 [MethodParametersAttr]。
 */
class JavaMethodParametersAttr(list: List<MethodParametersAttr.Info>) :
	MethodParametersAttr(list),
	IJavaAttribute {

	companion object {
		/** @return 读取器：逐条读参数名与访问标志 */
		@JvmStatic
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val constPool: ConstPoolReader = clsData.getConstPoolReader()
				val count = reader.readU1()
				val params = ArrayList<MethodParametersAttr.Info>(count)
				for (i in 0 until count) {
					// Info.name 声明非空；损坏 class 时 getUtf8 为 null，原 Java 后续解引用同样 NPE
					val name = constPool.getUtf8(reader.readU2()) ?: throw NullPointerException("parameter name is null")
					val accessFlags = reader.readU2()
					params.add(MethodParametersAttr.Info(accessFlags, name))
				}
				return JavaMethodParametersAttr(params)
			}
		}
	}
}
