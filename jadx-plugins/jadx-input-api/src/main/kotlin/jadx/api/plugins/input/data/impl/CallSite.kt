package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.data.ICallSite
import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue

/**
 * 调用点（call site）的默认实现。
 *
 * **背景**：Dex invoke-custom 指令的 bootstrap 参数列表，见 [ICallSite]。
 *
 * @param values bootstrap 方法的编码值参数列表
 */
public class CallSite(private val valuesValue: List<EncodedValue>) : ICallSite {

	override fun load() {
		for (value in valuesValue) {
			when (value.type) {
				// 参数本身是方法句柄/方法引用时，递归触发其惰性加载
				EncodedType.ENCODED_METHOD_HANDLE -> (value.value as IMethodHandle).load()

				EncodedType.ENCODED_METHOD -> (value.value as IMethodRef).load()

				else -> {
					// 原 Java switch 无 default：其余编码类型静默忽略
				}
			}
		}
	}

	override val values: List<EncodedValue> get() = valuesValue

	override fun toString(): String = "CallSite{$valuesValue}"
}
