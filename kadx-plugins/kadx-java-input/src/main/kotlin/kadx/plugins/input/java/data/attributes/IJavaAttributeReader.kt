package kadx.plugins.input.java.data.attributes

import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.JavaClassData

/**
 * 单个 attribute 的读取器接口。
 *
 * **做什么**：每种 .class 属性（Code、Exceptions、注解……）对应一个实现，
 * 负责从 [reader] 当前位置解析出该属性的数据对象；[clsData] 提供常量池等上下文。
 *
 * **为什么按类型拆分读取器**：attribute 区是"名字 + 长度 + 不透明字节"的序列，
 * 只有知道属性名对应的具体格式才能正确解析；[JavaAttrType] 维护名字 → 读取器的绑定表。
 */
interface IJavaAttributeReader {

	/**
	 * 从 [reader] 当前位置读取一个属性值。
	 * @param clsData 当前 class 的上下文（常量池、偏移索引等）
	 * @param reader 已定位到该 attribute 数据起始处的读取器
	 */
	fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute
}
