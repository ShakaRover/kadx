package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.IMethodRef
import kadx.api.plugins.input.data.MethodHandleType

/**
 * 指向字段的 [IMethodHandle] 实现（如 REF_getStatic / REF_setInstance）。
 *
 * @param type 句柄类型标签
 * @param fieldRef 指向的字段引用；getter 声明可空，故此处允许 null
 */
public class FieldRefHandle(
	private val typeValue: MethodHandleType,
	private val fieldRefValue: IFieldRef?,
) : IMethodHandle {

	override val type: MethodHandleType get() = typeValue

	override val fieldRef: IFieldRef? get() = fieldRefValue

	override val methodRef: IMethodRef? get() = null

	override fun load() {
		// 字段引用构造时已完整，无需加载
	}

	override fun toString(): String = "$typeValue: $fieldRefValue"
}
