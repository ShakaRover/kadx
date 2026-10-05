package kadx.api.plugins.input.data

import kadx.api.plugins.input.insns.custom.ICustomPayload

/**
 * 方法引用接口：定位某个类中的某个方法（轻量视图，支持延迟加载）。
 *
 * **背景**：与 [IMethodData]（完整数据）不同，本接口只暴露"找到这个方法"所需的信息。
 * 继承自 [IMethodProto] 获得签名形状；同时实现 [ICustomPayload]，因为部分输入格式中
 * 方法引用本身携带附加数据块。
 */
public interface IMethodRef :
	IMethodProto,
	ICustomPayload {

	/**
	 * @return 方法的唯一 id（用于缓存）；无法计算可靠 id 时返回 0（禁用缓存）。
	 */
	public val uniqId: Int

	/**
	 * 惰性加载方法信息：调用 [load] 之前只能使用 [getUniqId]。
	 */
	public fun load()

	/** @return 方法所属类的完整类型名（如 "com.example.Foo"）*/
	public val parentClassType: String

	/** @return 方法名 */
	public val name: String
}
