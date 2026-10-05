package kadx.core.utils.input

import kadx.api.plugins.input.data.ICallSite
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.IMethodRef
import kadx.api.plugins.input.data.annotations.EncodedType
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.insns.InsnData
import kadx.api.plugins.input.insns.InsnIndexType
import kadx.api.plugins.input.insns.custom.ICustomPayload

/**
 * 指令数据（[InsnData]）辅助工具。
 *
 * **用途**：从指令索引中解析出 call-site / method-ref / method-handle。
 * invoke-custom 的索引在不同输入实现里可能内联在 payload 中，也可能需要再次解引用，
 * 这里统一处理，并保证类型不匹配时返回 null（而不是抛异常）。
 *
 * **Kotlin 转换说明**：全部为静态工具方法，用 `object` + `@JvmStatic` 保持 Java 调用不变；
 * 参数/返回值可空性与原 Java 的 `@Nullable` 语义一致。
 */
object InsnDataUtils {

	/** 若当前指令是 invoke-custom（索引类型 CALL_SITE），返回其 call-site，否则返回 null。 */
	fun getCallSite(insnData: InsnData): ICallSite? {
		if (insnData.indexType !== InsnIndexType.CALL_SITE) {
			return null
		}
		val payload = insnData.payload
		if (payload != null) {
			return payload as ICallSite
		}
		return insnData.indexAsCallSite
	}

	/** 若当前指令是方法引用（索引类型 METHOD_REF），返回其方法引用，否则返回 null。 */
	fun getMethodRef(insnData: InsnData): IMethodRef? {
		if (insnData.indexType !== InsnIndexType.METHOD_REF) {
			return null
		}
		val payload = insnData.payload
		if (payload != null) {
			return payload as IMethodRef
		}
		return insnData.indexAsMethod
	}

	/**
	 * 从 call-site 的参数列表中取出第 [argNum] 个 method-handle。
	 *
	 * @param callSite 目标 call-site（可为 null，此时直接返回 null）
	 * @param argNum   参数下标
	 */
	fun getMethodHandleAt(callSite: ICallSite?, argNum: Int): IMethodHandle? {
		if (callSite == null) {
			return null
		}
		val values = callSite.values
		if (argNum < values.size) {
			val encodedValue = values[argNum]
			if (encodedValue.type === EncodedType.ENCODED_METHOD_HANDLE) {
				return encodedValue.value as IMethodHandle
			}
		}
		return null
	}
}
