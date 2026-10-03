package jadx.core.utils

import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.data.MethodHandleType
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.ConstClassNode
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InvokeType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections

/**
 * 注解编码值（[EncodedValue]）到 jadx 内部常量/指令的转换工具。
 *
 * **用途**：把 class/Dex 里注解的编码值转成反编译输出时能直接使用的常量参数，
 * 以及把 `invoke-custom` 的 method type / method handle 展开成普通 Java 调用指令。
 */
object EncodedValueUtils {

	/**
	 * 把编码值转换为常量对象。
	 *
	 * @return [LiteralArg]、[String]、[ArgType]，无法转换时返回 null
	 */
	fun convertToConstValue(encodedValue: EncodedValue?): Any? {
		if (encodedValue == null) {
			return null
		}
		val value = encodedValue.value
		return when (encodedValue.type) {
			EncodedType.ENCODED_NULL -> InsnArg.lit(0L, ArgType.OBJECT)
			EncodedType.ENCODED_BOOLEAN -> if (java.lang.Boolean.TRUE == value) LiteralArg.litTrue() else LiteralArg.litFalse()
			EncodedType.ENCODED_BYTE -> InsnArg.lit((value as Byte).toLong(), ArgType.BYTE)
			EncodedType.ENCODED_SHORT -> InsnArg.lit((value as Short).toLong(), ArgType.SHORT)
			EncodedType.ENCODED_CHAR -> InsnArg.lit((value as Char).code.toLong(), ArgType.CHAR)
			EncodedType.ENCODED_INT -> InsnArg.lit((value as Int).toLong(), ArgType.INT)
			EncodedType.ENCODED_LONG -> InsnArg.lit(value as Long, ArgType.LONG)
			EncodedType.ENCODED_FLOAT -> InsnArg.lit(java.lang.Float.floatToIntBits(value as Float).toLong(), ArgType.FLOAT)
			EncodedType.ENCODED_DOUBLE -> InsnArg.lit(java.lang.Double.doubleToLongBits(value as Double), ArgType.DOUBLE)
			EncodedType.ENCODED_STRING -> value as String?
			EncodedType.ENCODED_TYPE -> ArgType.parse(value as String?)
			else -> null
		}
	}

	fun convertToInsnArg(root: RootNode, value: EncodedValue): InsnArg {
		val obj = value.value
		return when (value.type) {
			EncodedType.ENCODED_NULL,
			EncodedType.ENCODED_BYTE,
			EncodedType.ENCODED_SHORT,
			EncodedType.ENCODED_CHAR,
			EncodedType.ENCODED_INT,
			EncodedType.ENCODED_LONG,
			EncodedType.ENCODED_FLOAT,
			EncodedType.ENCODED_DOUBLE,
			-> convertToConstValue(value) as InsnArg

			EncodedType.ENCODED_BOOLEAN -> InsnArg.lit(if (obj as Boolean) 0L else 1L, ArgType.BOOLEAN)

			EncodedType.ENCODED_STRING -> InsnArg.wrapArg(ConstStringNode(obj as String?))

			EncodedType.ENCODED_TYPE -> InsnArg.wrapArg(ConstClassNode(ArgType.parse(obj as String?)))

			EncodedType.ENCODED_METHOD_TYPE -> InsnArg.wrapArg(buildMethodType(root, obj as IMethodProto))

			EncodedType.ENCODED_METHOD_HANDLE -> InsnArg.wrapArg(buildMethodHandle(root, obj as IMethodHandle))

			else -> throw JadxRuntimeException("Unsupported type for raw invoke-custom: " + value.type)
		}
	}

	private fun buildMethodType(root: RootNode, methodProto: IMethodProto): InvokeNode {
		val retType = ArgType.parse(methodProto.returnType)
		val argTypes = Utils.collectionMap(methodProto.argTypes) { ArgType.parse(it) }
		val callTypes = ArrayList<ArgType>(1 + argTypes.size)
		callTypes.add(retType)
		callTypes.addAll(argTypes)
		val mthType = ArgType.`object`("java.lang.invoke.MethodType")
		val cls = ClassInfo.fromType(root, mthType)
		val mth = MethodInfo.fromDetails(root, cls, "methodType", callTypes, mthType)
		val invoke = InvokeNode(mth, InvokeType.STATIC, callTypes.size)
		for (type in callTypes) {
			val argInsn: InsnNode
			if (type.isPrimitive()) {
				argInsn = IndexInsnNode(InsnType.SGET, getTypeField(root, checkNotNull(type.getPrimitiveType())), 0)
			} else {
				argInsn = ConstClassNode(type)
			}
			invoke.addArg(InsnArg.wrapArg(argInsn))
		}
		return invoke
	}

	fun getTypeField(root: RootNode, type: PrimitiveType): FieldInfo {
		val boxType = type.boxType
		val boxCls = ClassInfo.fromType(root, boxType)
		return FieldInfo.from(root, boxCls, "TYPE", boxType)
	}

	/**
	 * 构造 `MethodHandles.lookup().find{type}(methodCls, methodName, methodType)` 调用。
	 */
	private fun buildMethodHandle(root: RootNode, methodHandle: IMethodHandle): InsnNode {
		if (methodHandle.type.isField) {
			// TODO: 字段句柄的 lookup 尚未实现，先用占位字符串
			return ConstStringNode("FIELD:" + methodHandle.fieldRef)
		}
		val methodRef = checkNotNull(methodHandle.methodRef)
		methodRef.load()

		val lookupCls = ClassInfo.fromName(root, "java.lang.invoke.MethodHandles.Lookup")
		val findMethod = MethodInfo.fromDetails(
			root,
			lookupCls,
			getFindMethodName(methodHandle.type),
			Arrays.asList(ArgType.CLASS, ArgType.STRING, ArgType.`object`("java.lang.invoke.MethodType")),
			ArgType.`object`("java.lang.invoke.MethodHandle"),
		)

		val invoke = InvokeNode(findMethod, InvokeType.DIRECT, 4)
		invoke.addArg(buildLookupArg(root))
		invoke.addArg(InsnArg.wrapArg(ConstClassNode(ArgType.`object`(methodRef.parentClassType))))
		invoke.addArg(InsnArg.wrapArg(ConstStringNode(methodRef.name)))
		invoke.addArg(InsnArg.wrapArg(buildMethodType(root, methodRef)))
		return invoke
	}

	fun buildLookupArg(root: RootNode): InsnArg {
		val lookupType = ArgType.`object`("java.lang.invoke.MethodHandles.Lookup")
		val cls = ClassInfo.fromName(root, "java.lang.invoke.MethodHandles")
		val mth = MethodInfo.fromDetails(root, cls, "lookup", Collections.emptyList(), lookupType)
		return InsnArg.wrapArg(InvokeNode(mth, InvokeType.STATIC, 0))
	}

	private fun getFindMethodName(type: MethodHandleType): String = when (type) {
		MethodHandleType.INVOKE_STATIC -> "findStatic"

		MethodHandleType.INVOKE_CONSTRUCTOR -> "findConstructor"

		MethodHandleType.INVOKE_INSTANCE,
		MethodHandleType.INVOKE_DIRECT,
		MethodHandleType.INVOKE_INTERFACE,
		-> "findVirtual"

		else -> "<$type>"
	}
}
