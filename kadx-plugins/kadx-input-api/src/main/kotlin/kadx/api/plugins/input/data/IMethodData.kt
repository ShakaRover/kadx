package kadx.api.plugins.input.data

import kadx.api.plugins.input.data.attributes.IKadxAttribute

/**
 * 方法数据接口：一个完整方法的元信息（引用 + 访问标志 + 代码 + 自定义属性）。
 *
 * **背景**：输入插件解析出的每个方法都实现本接口，kadx-core 据此创建
 * [kadx.api.dex.tree.MethodNode]。注意与 [IMethodRef] 的区别：
 * IMethodRef 是轻量引用（可延迟加载），本接口是完整数据视图。
 */
public interface IMethodData {

	/** @return 方法引用（父类/名字/签名）*/
	public val methodRef: IMethodRef

	/** @return 访问标志位（见 [AccessFlags]，如 ACC_PUBLIC、ACC_ABSTRACT）*/
	public val accessFlags: Int

	/** @return 方法代码读取器；抽象方法 / native 方法等无代码时为 null */
	public val codeReader: ICodeReader?

	/** @return 方法的反汇编文本（调试用，可能为空字符串）*/
	public fun disassembleMethod(): String

	/** @return 方法携带的自定义属性列表（注解、try-catch 等，可为空列表）*/
	public val attributes: List<IKadxAttribute>
}
