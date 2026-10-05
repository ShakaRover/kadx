package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.IMethodRef
import kadx.api.plugins.input.data.MethodHandleType

/**
 * 指向方法的 [IMethodHandle] 实现（如 REF_invokeVirtual）。
 *
 * @param type 句柄类型标签
 * @param methodRef 指向的方法引用
 */
public class MethodRefHandle(
	private val typeValue: MethodHandleType,
	private val methodRefValue: IMethodRef,
) : IMethodHandle {

	override val type: MethodHandleType get() = typeValue

	override val methodRef: IMethodRef? get() = methodRefValue

	// 原 Java 此处返回类型误写为 IFieldData（协变），实际恒返回 null；
	// Kotlin 中按接口签名声明为 IFieldRef?，运行时行为完全一致。
	override val fieldRef: IFieldRef? get() = null

	override fun load() {
		methodRefValue.load()
	}

	override fun toString(): String = "$typeValue: $methodRefValue"
}
