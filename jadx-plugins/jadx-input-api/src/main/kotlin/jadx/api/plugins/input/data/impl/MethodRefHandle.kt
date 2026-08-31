package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.data.MethodHandleType

/**
 * 指向方法的 [IMethodHandle] 实现（如 REF_invokeVirtual）。
 *
 * @param type 句柄类型标签
 * @param methodRef 指向的方法引用
 */
public class MethodRefHandle(
	private val type: MethodHandleType,
	private val methodRef: IMethodRef,
) : IMethodHandle {

	override fun getType(): MethodHandleType = type

	override fun getMethodRef(): IMethodRef? = methodRef

	// 原 Java 此处返回类型误写为 IFieldData（协变），实际恒返回 null；
	// Kotlin 中按接口签名声明为 IFieldRef?，运行时行为完全一致。
	override fun getFieldRef(): IFieldRef? = null

	override fun load() {
		methodRef.load()
	}

	override fun toString(): String = "$type: $methodRef"
}
