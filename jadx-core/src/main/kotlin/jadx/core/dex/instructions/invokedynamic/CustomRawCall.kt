package jadx.core.dex.instructions.invokedynamic

import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.InvokeCustomRawNode
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.EncodedValueUtils.buildLookupArg
import jadx.core.utils.EncodedValueUtils.convertToInsnArg
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 原始 invoke-custom 指令的构建器。
 *
 * 当 call-site 无法匹配 lambda 或字符串拼接时，jadx 会退化成“类似 polymorphic 调用”
 * 的输出方式：先构造一个解析调用（resolve），再把 call-site 常量作为参数。
 *
 * Kotlin 转换说明：静态方法 [build] 使用 companion + `@JvmStatic` 平替。
 */
class CustomRawCall {

	companion object {

		/** 构建原始 invoke-custom 节点。 */
		@JvmStatic
		fun build(mth: MethodNode, insn: InsnData, isRange: Boolean, values: List<EncodedValue>): InsnNode {
			val resolveHandle = values[0].value as IMethodHandle
			val invokeName = values[1].value as String
			val invokeProto = values[2].value as IMethodProto
			val resolveArgs = buildArgs(mth, values)

			if (resolveHandle.getType().isField()) {
				throw JadxRuntimeException("Field handle not yet supported")
			}

			val root = mth.root()
			val resolveMth = MethodInfo.fromRef(root, checkNotNull(resolveHandle.getMethodRef()))
			val resolveInvokeType = InvokeCustomUtils.convertInvokeType(resolveHandle.getType())
			val resolve = InvokeNode(resolveMth, resolveInvokeType, resolveArgs.size)
			resolveArgs.forEach(resolve::addArg)

			val invokeCls = ClassInfo.fromType(root, ArgType.OBJECT) // 运行时才能确定具体类型
			val invokeMth = MethodInfo.fromMethodProto(root, invokeCls, invokeName, invokeProto)
			val customRawNode = InvokeCustomRawNode(resolve, invokeMth, insn, isRange)
			customRawNode.callSiteValues = values
			return customRawNode
		}

		/**
		 * 把 call-site 常量转换成指令参数。
		 *
		 * 第一个参数固定是 `MethodHandles.lookup()`（对应 resolve 方法的接收者）；
		 * 其余常量逐个转换，转换失败时退化为字符串常量并记录告警。
		 */
		private fun buildArgs(mth: MethodNode, values: List<EncodedValue>): List<InsnArg> {
			val valuesCount = values.size
			val list = ArrayList<InsnArg>(valuesCount)
			val root = mth.root()
			list.add(buildLookupArg(root)) // 用 `java.lang.invoke.MethodHandles.lookup()` 作为第一个参数
			for (i in 1 until valuesCount) {
				val value = values[i]
				try {
					list.add(convertToInsnArg(root, value))
				} catch (e: Exception) {
					mth.addWarnComment("Failed to build arg in invoke-custom insn: $value", e)
					list.add(InsnArg.wrapArg(ConstStringNode(value.toString())))
				}
			}
			return list
		}
	}
}
