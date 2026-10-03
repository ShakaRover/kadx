package jadx.core.dex.instructions.invokedynamic

import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.MethodHandleType
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.InvokeCustomNode
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InvokeType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.NamedArg
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * `LambdaMetafactory` 生成的 invoke-custom 指令（lambda / 方法引用）解析器。
 *
 * DEX/Java 中 lambda 会被编译成一个 `invoke-custom` 指令，其 bootstrap 参数
 * 是一组 [EncodedValue]（方法句柄、方法原型等）。本类负责判断是否为 lambda
 * 调用，并把 bootstrap 参数还原成 jadx 内部可表示的 [InvokeCustomNode]。
 *
 * Kotlin 转换说明：原 Java 的静态方法全部放入 companion 并标注 `@JvmStatic`，
 * Java 调用方仍可写 `CustomLambdaCall.xxx(...)`；私有辅助方法为 companion 私有函数。
 */
class CustomLambdaCall {

	companion object {

		/**
		 * 判断这组 bootstrap 参数是否为 `LambdaMetafactory.metafactory` / `altMetafactory` 调用。
		 *
		 * 判断依据：
		 * 1. 参数至少 6 个；
		 * 2. 第 0 个是方法句柄（ENCODED_METHOD_HANDLE）且为静态调用；
		 * 3. 目标类必须是 `java.lang.invoke.LambdaMetafactory`；
		 * 4. 方法名是 `metafactory` 或 `altMetafactory`。
		 */
		@JvmStatic
		fun isLambdaInvoke(values: List<EncodedValue>): Boolean {
			if (values.size < 6) {
				return false
			}
			val mthRef = values[0]
			if (mthRef.type != EncodedType.ENCODED_METHOD_HANDLE) {
				return false
			}
			val methodHandle = mthRef.value as IMethodHandle
			if (methodHandle.type != MethodHandleType.INVOKE_STATIC) {
				return false
			}
			val methodRef = methodHandle.methodRef ?: return false
			if (methodRef.parentClassType != "Ljava/lang/invoke/LambdaMetafactory;") {
				return false
			}
			val mthName = methodRef.name
			return mthName == "metafactory" || mthName == "altMetafactory"
		}

		/** 构建 lambda 的 invoke-custom 节点，并把结果寄存器类型设为 lambda 返回类型。 */
		@JvmStatic
		fun buildLambdaMethodCall(mth: MethodNode, insn: InsnData, isRange: Boolean, values: List<EncodedValue>): InvokeCustomNode {
			val callMthHandle = values[4].value as IMethodHandle
			if (callMthHandle.type.isField) {
				throw JadxRuntimeException("Not yet supported")
			}
			val resNode = buildMethodCall(mth, insn, isRange, values, callMthHandle)
			val resReg = insn.resultReg
			if (resReg != -1) {
				resNode.setResult(InsnArg.reg(resReg, mth.getReturnType()))
			}
			return resNode
		}

		/**
		 * 根据 bootstrap 参数构建内部调用指令。
		 *
		 * 参数含义（LambdaMetafactory 规范）：
		 * - 2：被合成 lambda 的方法原型；
		 * - 1：lambda 实现方法名；
		 * - 3：实现方法原型；
		 * - 4：实际调用的方法句柄；
		 * - 5：有效方法原型（用于判断参数是否与实现方法一致）。
		 */
		private fun buildMethodCall(
			mth: MethodNode,
			insn: InsnData,
			isRange: Boolean,
			values: List<EncodedValue>,
			callMthHandle: IMethodHandle,
		): InvokeCustomNode {
			val root = mth.root()
			val lambdaProto = values[2].value as IMethodProto
			val lambdaInfo = MethodInfo.fromMethodProto(root, mth.parentClass.classInfo, "", lambdaProto)

			val methodHandleType = callMthHandle.type
			val invokeCustomNode = InvokeCustomNode(lambdaInfo, insn, false, isRange)
			invokeCustomNode.handleType = methodHandleType

			val implCls = ClassInfo.fromType(root, lambdaInfo.returnType)
			val implName = values[1].value as String
			val implProto = values[3].value as IMethodProto
			val implMthInfo = MethodInfo.fromMethodProto(root, implCls, implName, implProto)
			invokeCustomNode.implMthInfo = implMthInfo

			val callMthInfo = MethodInfo.fromRef(root, checkNotNull(callMthHandle.methodRef))
			val invokeNode = buildInvokeNode(methodHandleType, invokeCustomNode, callMthInfo)

			if (methodHandleType == MethodHandleType.INVOKE_CONSTRUCTOR) {
				val ctrInsn = ConstructorInsn(mth, invokeNode)
				invokeCustomNode.callInsn = ctrInsn
			} else {
				invokeCustomNode.callInsn = invokeNode
			}

			val callMth = root.resolveMethod(callMthInfo)
			if (callMth != null) {
				invokeCustomNode.callInsn?.addAttr(callMth)
				if (callMth.accessFlags.isSynthetic() &&
					callMth.parentClass == mth.parentClass
				) {
					// 只内联同一个类中的合成方法（与原 Java 语义一致）
					callMth.add(AFlag.DONT_GENERATE)
					invokeCustomNode.isInlineInsn = true
				}
			}
			if (!invokeCustomNode.isInlineInsn) {
				val effectiveMthProto = values[5].value as IMethodProto
				val args = Utils.collectionMap(effectiveMthProto.argTypes) { ArgType.parse(it) }
				val sameArgs = args == callMthInfo.argumentsTypes
				invokeCustomNode.isUseRef = sameArgs
			}

			// 防止参数被内联进一个不会生成的 invoke-custom 节点
			for (arg in invokeCustomNode.getArguments()) {
				arg.add(AFlag.DONT_INLINE)
			}
			return invokeCustomNode
		}

		/**
		 * 构建内部 [InvokeNode]，并把 invoke-custom 的参数复制过来。
		 *
		 * 若调用参数比 invoke-custom 参数多（例如实例方法的接收者），
		 * 用占位参数 [NamedArg] 补齐，等待后续类型推断/内联阶段填充。
		 */
		private fun buildInvokeNode(
			methodHandleType: MethodHandleType,
			invokeCustomNode: InvokeCustomNode,
			callMthInfo: MethodInfo,
		): InvokeNode {
			val invokeType = InvokeCustomUtils.convertInvokeType(methodHandleType)
			var callArgsCount = callMthInfo.argsCount
			val instanceCall = invokeType != InvokeType.STATIC
			if (instanceCall) {
				callArgsCount++
			}
			val invokeNode = InvokeNode(callMthInfo, invokeType, callArgsCount)

			// 复制 invoke-custom 的实参
			val argsCount = invokeCustomNode.getArgsCount()
			for (i in 0 until argsCount) {
				val arg = invokeCustomNode.getArg(i)
				invokeNode.addArg(arg.duplicate())
			}
			if (callArgsCount > argsCount) {
				// 用 NamedArg 补齐剩余参数
				var callArgNum = argsCount
				if (instanceCall) {
					callArgNum-- // 从实例类型参数开始
				}
				val callArgTypes = callMthInfo.argumentsTypes
				for (i in argsCount until callArgsCount) {
					val argType: ArgType = if (callArgNum < 0) {
						// 实例参数类型
						callMthInfo.declClass.type
					} else {
						callArgTypes[callArgNum++]
					}
					invokeNode.addArg(NamedArg("v$i", argType))
				}
			}
			return invokeNode
		}
	}
}
