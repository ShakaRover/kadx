package kadx.plugins.input.java.data.attributes.types

import kadx.api.plugins.input.data.attributes.types.InnerClassesAttr
import kadx.api.plugins.input.data.attributes.types.InnerClsInfo
import kadx.plugins.input.java.data.ConstPoolReader
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.JavaClassData
import kadx.plugins.input.java.data.attributes.IJavaAttribute
import kadx.plugins.input.java.data.attributes.IJavaAttributeReader
import java.util.HashMap

/**
 * InnerClasses attribute：内部/成员类信息表。
 *
 **做什么**：把"数量 + N 个(内部类索引, 外部类索引, 名字索引, 访问标志)"序列
 * 解析成"内部类名 → [InnerClsInfo]"映射，供还原嵌套类结构与访问修饰符；
 * 继承通用 [InnerClassesAttr] 并挂上 IJavaAttribute 标记。
 */
class JavaInnerClsAttr(map: Map<String, InnerClsInfo>) :
	InnerClassesAttr(map),
	IJavaAttribute {

	companion object {
		/** @return 读取器：逐条读内部类四元组（外部类索引为 0 表示顶级类） */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val len = reader.readU2()
				val constPool: ConstPoolReader = clsData.constPoolReader
				// getClass 带 @Nullable（损坏 class），原 Java 允许 null 键，这里保持同样行为
				val clsMap = HashMap<String?, InnerClsInfo>(len)
				for (i in 0 until len) {
					val innerCls = constPool.getClass(reader.readU2())
					val outerClsIdx = reader.readU2()
					val outerCls = if (outerClsIdx == 0) null else constPool.getClass(outerClsIdx)
					val name = constPool.getUtf8(reader.readU2())
					val accFlags = reader.readU2()
					clsMap[innerCls] = InnerClsInfo(innerCls, outerCls, name, accFlags)
				}
				@Suppress("UNCHECKED_CAST")
				return JavaInnerClsAttr(clsMap as Map<String, InnerClsInfo>)
			}
		}
	}
}
