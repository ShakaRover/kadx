package kadx.api.plugins.input.data

import kadx.api.plugins.input.data.attributes.IKadxAttribute

/**
 * 类数据接口：输入插件解析出的一个类的完整元信息。
 *
 * **背景**：每个输入插件（dex / class file...）把每个类封装成本接口的实现，
 * kadx-core 通过 [ICodeLoader.visitClasses] 逐个接收并构建 ClassNode。
 */
public interface IClassData {

	/** @return 本数据的独立副本（修改副本不影响原对象）*/
	public fun copy(): IClassData

	/** @return 输入文件名（调试/日志用，可能为空字符串）*/
	public val inputFileName: String

	/** @return 类的完整类型名（如 "com.example.Foo"）*/
	public val type: String

	/** @return 访问标志位（见 [AccessFlags]，如 ACC_PUBLIC、ACC_ABSTRACT）*/
	public val accessFlags: Int

	/** @return 类在输入文件中的起始偏移 */
	public val inputFileOffset: Int

	/** @return 父类类型名；java.lang.Object 或无父类信息时为 null */
	public val superType: String?

	/** @return 实现的接口类型列表（可为空列表）*/
	public val interfacesTypes: List<String>

	/**
	 * 遍历本类的字段与方法，分别回调两个消费者。
	 * @param fieldsConsumer 接收每个 [IFieldData] 的消费者
	 * @param mthConsumer 接收每个 [IMethodData] 的消费者
	 */
	public fun visitFieldsAndMethods(fieldsConsumer: ISeqConsumer<IFieldData>, mthConsumer: ISeqConsumer<IMethodData>)

	/** @return 类携带的自定义属性列表（注解等，可为空列表）*/
	public val attributes: List<IKadxAttribute>

	/** @return 整个类的反汇编文本（调试用，可能为空字符串）*/
	public val disassembledCode: String
}
