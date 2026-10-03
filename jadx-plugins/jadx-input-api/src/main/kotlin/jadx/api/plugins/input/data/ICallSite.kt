package jadx.api.plugins.input.data

import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.insns.custom.ICustomPayload

/**
 * 调用点（call site）接口：invoke-custom 指令携带的 bootstrap 方法参数。
 *
 * **背景**：Dex 的 `invoke-custom` 指令用于 lambda / string concatenation 等场景，
 * 其操作数是一个 call-site 索引，指向一组 [EncodedValue] 形式的 bootstrap 参数。
 * 本接口同时实现 [ICustomPayload]，因为 call-site 数据也是指令的附加载荷之一。
 */
public interface ICallSite : ICustomPayload {

	/** @return bootstrap 方法的参数值列表（编码值）*/
	public val values: List<EncodedValue>

	/**
	 * 惰性加载：首次访问前调用，填充 [getValues] 的数据。
	 * **注意**：load() 之前只能依赖构造时已知的信息。
	 */
	public fun load()
}
