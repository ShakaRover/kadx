package jadx.api.plugins.input.data

/**
 * 方法原型接口：只描述方法的"签名形状"（返回类型 + 参数类型列表）。
 *
 * **背景**：有些场景只需要知道方法长什么样，而不需要完整的方法数据
 * （如 [IMethodRef] 在 load() 之前），本接口提供这种轻量视图。
 */
public interface IMethodProto {

	/** @return 返回类型的描述符/类型字符串（如 "V"、"I"）*/
	public val returnType: String

	/** @return 参数类型列表，顺序与方法声明一致 */
	public val argTypes: List<String>
}
