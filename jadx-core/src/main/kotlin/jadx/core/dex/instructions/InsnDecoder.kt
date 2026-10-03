package jadx.core.dex.instructions

import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.insns.InsnData
import jadx.api.plugins.input.insns.Opcode
import jadx.api.plugins.input.insns.custom.IArrayPayload
import jadx.api.plugins.input.insns.custom.ICustomPayload
import jadx.api.plugins.input.insns.custom.ISwitchPayload
import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr.CodeFeature
import jadx.core.dex.attributes.nodes.JadxError
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.java.JsrNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.DecodeException
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.input.InsnDataUtils

/**
 * 指令解码器：把输入的原始指令流（Dex / class 文件）翻译成 jadx 的 [InsnNode] 树。
 *
 * 核心流程：
 * 1. [process] 遍历方法的所有指令，逐条调用 [decode]；
 * 2. [decode] 按 opcode 分派，构造对应的指令节点并设置寄存器参数；
 * 3. 解码失败时记录错误；同一方法第二次失败则直接抛出，避免产生不可用的半成品。
 *
 * Kotlin 转换说明：
 * - [process] 与 [decode] 保持 `open`，因为 jadx-gui 的 SmaliInsnDecoder 会继承并覆写；
 * - [decode] 声明 `@Throws(DecodeException::class)`，保持 Java 侧的受检异常签名；
 * - 返回值用 `Array<InsnNode?>`（JVM 类型仍是 `InsnNode[]`），因为未被指令覆盖的
 *   槽位会保留为 null，与原 Java `new InsnNode[...]` 行为一致。
 */
open class InsnDecoder(private val method: MethodNode) {

	private val root: RootNode = method.root()

	/** 遍历并解码方法的所有指令，返回以偏移为下标、可能含 null 的指令数组。 */
	open fun process(codeReader: ICodeReader): Array<InsnNode?> {
		val instructions = arrayOfNulls<InsnNode>(codeReader.unitsCount)
		codeReader.visitInstructions { rawInsn ->
			val offset = rawInsn.offset
			var insn: InsnNode
			try {
				rawInsn.decode()
				insn = decode(rawInsn)
			} catch (e: Exception) {
				val mthWithErrors = method.contains(AType.JADX_ERROR)
				method.addError("Failed to decode insn: $rawInsn", e)
				if (mthWithErrors) {
					// 同一方法内第二次出错 => 放弃处理，向上抛出
					throw JadxRuntimeException("Failed to decode insn: $rawInsn", e)
				}
				insn = InsnNode(InsnType.NOP, 0)
				insn.addAttr(AType.JADX_ERROR, JadxError("decode failed: " + e.message, e))
			}
			insn.setOffset(offset)
			instructions[offset] = insn
		}
		return instructions
	}

	/** 单条指令解码入口：按 opcode 分派到对应分支。 */
	@Throws(DecodeException::class)
	protected open fun decode(insn: InsnData): InsnNode {
		when (insn.opcode) {
			// ===== 常量 / 寄存器移动 =====
			Opcode.NOP -> return InsnNode(InsnType.NOP, 0)

			// move-result 会在 invoke 与 filled-new-array 指令里处理
			Opcode.MOVE_RESULT -> return makeInsn(InsnType.MOVE_RESULT, InsnArg.reg(insn, 0, ArgType.UNKNOWN))

			Opcode.CONST -> {
				val narrowLitArg = InsnArg.lit(insn, ArgType.NARROW)
				return makeInsn(InsnType.CONST, InsnArg.reg(insn, 0, narrowLitArg.getType()), narrowLitArg)
			}

			Opcode.CONST_WIDE -> {
				val wideLitArg = InsnArg.lit(insn, ArgType.WIDE)
				return makeInsn(InsnType.CONST, InsnArg.reg(insn, 0, wideLitArg.getType()), wideLitArg)
			}

			Opcode.CONST_STRING -> {
				val constStrInsn = ConstStringNode(insn.indexAsString)
				constStrInsn.setResult(InsnArg.reg(insn, 0, ArgType.STRING))
				return constStrInsn
			}

			Opcode.CONST_CLASS -> {
				val clsType = ArgType.parse(insn.indexAsType)
				val constClsInsn = ConstClassNode(clsType)
				constClsInsn.setResult(InsnArg.reg(insn, 0, ArgType.generic(Consts.CLASS_CLASS, clsType)))
				return constClsInsn
			}

			Opcode.MOVE -> return makeInsn(
				InsnType.MOVE,
				InsnArg.reg(insn, 0, ArgType.NARROW),
				InsnArg.reg(insn, 1, ArgType.NARROW),
			)

			Opcode.MOVE_MULTI -> {
				val len = insn.regsCount
				val mmv = InsnNode(InsnType.MOVE_MULTI, len)
				for (i in 0 until len) {
					mmv.addArg(InsnArg.reg(insn, i, ArgType.UNKNOWN))
				}
				return mmv
			}

			Opcode.MOVE_WIDE -> return makeInsn(
				InsnType.MOVE,
				InsnArg.reg(insn, 0, ArgType.WIDE),
				InsnArg.reg(insn, 1, ArgType.WIDE),
			)

			Opcode.MOVE_OBJECT -> return makeInsn(
				InsnType.MOVE,
				InsnArg.reg(insn, 0, ArgType.UNKNOWN_OBJECT),
				InsnArg.reg(insn, 1, ArgType.UNKNOWN_OBJECT),
			)

			// ===== 算术 / 位运算 =====
			Opcode.ADD_INT -> return arith(insn, ArithOp.ADD, ArgType.INT)

			Opcode.ADD_DOUBLE -> return arith(insn, ArithOp.ADD, ArgType.DOUBLE)

			Opcode.ADD_FLOAT -> return arith(insn, ArithOp.ADD, ArgType.FLOAT)

			Opcode.ADD_LONG -> return arith(insn, ArithOp.ADD, ArgType.LONG)

			Opcode.ADD_INT_LIT -> return arithLit(insn, ArithOp.ADD, ArgType.INT)

			Opcode.SUB_INT -> return arith(insn, ArithOp.SUB, ArgType.INT)

			Opcode.RSUB_INT -> return ArithNode(
				ArithOp.SUB,
				InsnArg.reg(insn, 0, ArgType.INT),
				InsnArg.lit(insn, ArgType.INT),
				InsnArg.reg(insn, 1, ArgType.INT),
			)

			Opcode.SUB_LONG -> return arith(insn, ArithOp.SUB, ArgType.LONG)

			Opcode.SUB_FLOAT -> return arith(insn, ArithOp.SUB, ArgType.FLOAT)

			Opcode.SUB_DOUBLE -> return arith(insn, ArithOp.SUB, ArgType.DOUBLE)

			Opcode.MUL_INT -> return arith(insn, ArithOp.MUL, ArgType.INT)

			Opcode.MUL_DOUBLE -> return arith(insn, ArithOp.MUL, ArgType.DOUBLE)

			Opcode.MUL_FLOAT -> return arith(insn, ArithOp.MUL, ArgType.FLOAT)

			Opcode.MUL_LONG -> return arith(insn, ArithOp.MUL, ArgType.LONG)

			Opcode.MUL_INT_LIT -> return arithLit(insn, ArithOp.MUL, ArgType.INT)

			Opcode.DIV_INT -> return arith(insn, ArithOp.DIV, ArgType.INT)

			Opcode.REM_INT -> return arith(insn, ArithOp.REM, ArgType.INT)

			Opcode.REM_LONG -> return arith(insn, ArithOp.REM, ArgType.LONG)

			Opcode.REM_FLOAT -> return arith(insn, ArithOp.REM, ArgType.FLOAT)

			Opcode.REM_DOUBLE -> return arith(insn, ArithOp.REM, ArgType.DOUBLE)

			Opcode.DIV_DOUBLE -> return arith(insn, ArithOp.DIV, ArgType.DOUBLE)

			Opcode.DIV_FLOAT -> return arith(insn, ArithOp.DIV, ArgType.FLOAT)

			Opcode.DIV_LONG -> return arith(insn, ArithOp.DIV, ArgType.LONG)

			Opcode.DIV_INT_LIT -> return arithLit(insn, ArithOp.DIV, ArgType.INT)

			Opcode.REM_INT_LIT -> return arithLit(insn, ArithOp.REM, ArgType.INT)

			Opcode.AND_INT -> return arith(insn, ArithOp.AND, ArgType.INT)

			Opcode.AND_INT_LIT -> return arithLit(insn, ArithOp.AND, ArgType.INT)

			Opcode.XOR_INT_LIT -> return arithLit(insn, ArithOp.XOR, ArgType.INT)

			Opcode.AND_LONG -> return arith(insn, ArithOp.AND, ArgType.LONG)

			Opcode.OR_INT -> return arith(insn, ArithOp.OR, ArgType.INT)

			Opcode.OR_INT_LIT -> return arithLit(insn, ArithOp.OR, ArgType.INT)

			Opcode.XOR_INT -> return arith(insn, ArithOp.XOR, ArgType.INT)

			Opcode.OR_LONG -> return arith(insn, ArithOp.OR, ArgType.LONG)

			Opcode.XOR_LONG -> return arith(insn, ArithOp.XOR, ArgType.LONG)

			Opcode.USHR_INT -> return arith(insn, ArithOp.USHR, ArgType.INT)

			Opcode.USHR_LONG -> return arith(insn, ArithOp.USHR, ArgType.LONG)

			Opcode.SHL_INT -> return arith(insn, ArithOp.SHL, ArgType.INT)

			Opcode.SHL_LONG -> return arith(insn, ArithOp.SHL, ArgType.LONG)

			Opcode.SHR_INT -> return arith(insn, ArithOp.SHR, ArgType.INT)

			Opcode.SHR_LONG -> return arith(insn, ArithOp.SHR, ArgType.LONG)

			Opcode.SHL_INT_LIT -> return arithLit(insn, ArithOp.SHL, ArgType.INT)

			Opcode.SHR_INT_LIT -> return arithLit(insn, ArithOp.SHR, ArgType.INT)

			Opcode.USHR_INT_LIT -> return arithLit(insn, ArithOp.USHR, ArgType.INT)

			Opcode.NEG_INT -> return neg(insn, ArgType.INT)

			Opcode.NEG_LONG -> return neg(insn, ArgType.LONG)

			Opcode.NEG_FLOAT -> return neg(insn, ArgType.FLOAT)

			Opcode.NEG_DOUBLE -> return neg(insn, ArgType.DOUBLE)

			Opcode.NOT_INT -> return not(insn, ArgType.INT)

			Opcode.NOT_LONG -> return not(insn, ArgType.LONG)

			// ===== 基本类型转换 =====
			Opcode.INT_TO_BYTE -> return cast(insn, ArgType.INT, ArgType.BYTE)

			Opcode.INT_TO_CHAR -> return cast(insn, ArgType.INT, ArgType.CHAR)

			Opcode.INT_TO_SHORT -> return cast(insn, ArgType.INT, ArgType.SHORT)

			Opcode.INT_TO_FLOAT -> return cast(insn, ArgType.INT, ArgType.FLOAT)

			Opcode.INT_TO_DOUBLE -> return cast(insn, ArgType.INT, ArgType.DOUBLE)

			Opcode.INT_TO_LONG -> return cast(insn, ArgType.INT, ArgType.LONG)

			Opcode.FLOAT_TO_INT -> return cast(insn, ArgType.FLOAT, ArgType.INT)

			Opcode.FLOAT_TO_DOUBLE -> return cast(insn, ArgType.FLOAT, ArgType.DOUBLE)

			Opcode.FLOAT_TO_LONG -> return cast(insn, ArgType.FLOAT, ArgType.LONG)

			Opcode.DOUBLE_TO_INT -> return cast(insn, ArgType.DOUBLE, ArgType.INT)

			Opcode.DOUBLE_TO_FLOAT -> return cast(insn, ArgType.DOUBLE, ArgType.FLOAT)

			Opcode.DOUBLE_TO_LONG -> return cast(insn, ArgType.DOUBLE, ArgType.LONG)

			Opcode.LONG_TO_INT -> return cast(insn, ArgType.LONG, ArgType.INT)

			Opcode.LONG_TO_FLOAT -> return cast(insn, ArgType.LONG, ArgType.FLOAT)

			Opcode.LONG_TO_DOUBLE -> return cast(insn, ArgType.LONG, ArgType.DOUBLE)

			// ===== 比较 / 条件跳转 / 无条件跳转 =====
			Opcode.IF_EQ, Opcode.IF_EQZ -> return IfNode(insn, IfOp.EQ)

			Opcode.IF_NE, Opcode.IF_NEZ -> return IfNode(insn, IfOp.NE)

			Opcode.IF_GT, Opcode.IF_GTZ -> return IfNode(insn, IfOp.GT)

			Opcode.IF_GE, Opcode.IF_GEZ -> return IfNode(insn, IfOp.GE)

			Opcode.IF_LT, Opcode.IF_LTZ -> return IfNode(insn, IfOp.LT)

			Opcode.IF_LE, Opcode.IF_LEZ -> return IfNode(insn, IfOp.LE)

			Opcode.CMP_LONG -> return cmp(insn, InsnType.CMP_L, ArgType.LONG)

			Opcode.CMPL_FLOAT -> return cmp(insn, InsnType.CMP_L, ArgType.FLOAT)

			Opcode.CMPL_DOUBLE -> return cmp(insn, InsnType.CMP_L, ArgType.DOUBLE)

			Opcode.CMPG_FLOAT -> return cmp(insn, InsnType.CMP_G, ArgType.FLOAT)

			Opcode.CMPG_DOUBLE -> return cmp(insn, InsnType.CMP_G, ArgType.DOUBLE)

			Opcode.GOTO -> return GotoNode(insn.target)

			Opcode.JAVA_JSR -> {
				method.add(AFlag.RESOLVE_JAVA_JSR)
				val jsr = JsrNode(insn.target)
				jsr.setResult(InsnArg.reg(insn, 0, ArgType.UNKNOWN_INT))
				return jsr
			}

			Opcode.JAVA_RET -> {
				method.add(AFlag.RESOLVE_JAVA_JSR)
				return makeInsn(InsnType.JAVA_RET, null, InsnArg.reg(insn, 0, ArgType.UNKNOWN_INT))
			}

			// ===== 返回 / 异常 / 监视器 =====
			Opcode.THROW -> return makeInsn(InsnType.THROW, null, InsnArg.reg(insn, 0, ArgType.THROWABLE))

			Opcode.MOVE_EXCEPTION -> {
				// try/catch 块处理需要后支配关系
				method.add(AFlag.COMPUTE_POST_DOM)
				return makeInsn(InsnType.MOVE_EXCEPTION, InsnArg.reg(insn, 0, ArgType.UNKNOWN_OBJECT_NO_ARRAY))
			}

			Opcode.RETURN_VOID -> return InsnNode(InsnType.RETURN, 0)

			Opcode.RETURN -> return makeInsn(
				InsnType.RETURN,
				null,
				InsnArg.reg(insn, 0, method.returnType),
			)

			Opcode.MONITOR_ENTER -> return makeInsn(
				InsnType.MONITOR_ENTER,
				null,
				InsnArg.reg(insn, 0, ArgType.UNKNOWN_OBJECT),
			)

			Opcode.MONITOR_EXIT -> return makeInsn(
				InsnType.MONITOR_EXIT,
				null,
				InsnArg.reg(insn, 0, ArgType.UNKNOWN_OBJECT),
			)

			// ===== 类型判断 / 字段访问 =====
			Opcode.INSTANCE_OF -> {
				val instInsn = IndexInsnNode(InsnType.INSTANCE_OF, ArgType.parse(insn.indexAsType), 1)
				instInsn.setResult(InsnArg.reg(insn, 0, ArgType.BOOLEAN))
				instInsn.addArg(InsnArg.reg(insn, 1, ArgType.UNKNOWN_OBJECT))
				return instInsn
			}

			Opcode.CHECK_CAST -> {
				val castType = ArgType.parse(insn.indexAsType)
				val checkCastInsn = IndexInsnNode(InsnType.CHECK_CAST, castType, 1)
				checkCastInsn.setResult(InsnArg.reg(insn, 0, castType))
				checkCastInsn.addArg(InsnArg.reg(insn, if (insn.regsCount == 2) 1 else 0, ArgType.UNKNOWN_OBJECT))
				return checkCastInsn
			}

			Opcode.IGET -> {
				val igetFld = FieldInfo.fromRef(root, checkNotNull(insn.indexAsField))
				val igetInsn = IndexInsnNode(InsnType.IGET, igetFld, 1)
				igetInsn.setResult(InsnArg.reg(insn, 0, tryResolveFieldType(igetFld)))
				igetInsn.addArg(InsnArg.reg(insn, 1, igetFld.declClass.type))
				return igetInsn
			}

			Opcode.IPUT -> {
				val iputFld = FieldInfo.fromRef(root, checkNotNull(insn.indexAsField))
				val iputInsn = IndexInsnNode(InsnType.IPUT, iputFld, 2)
				iputInsn.addArg(InsnArg.reg(insn, 0, tryResolveFieldType(iputFld)))
				iputInsn.addArg(InsnArg.reg(insn, 1, iputFld.declClass.type))
				return iputInsn
			}

			Opcode.SGET -> {
				val sgetFld = FieldInfo.fromRef(root, checkNotNull(insn.indexAsField))
				val sgetInsn = IndexInsnNode(InsnType.SGET, sgetFld, 0)
				sgetInsn.setResult(InsnArg.reg(insn, 0, tryResolveFieldType(sgetFld)))
				return sgetInsn
			}

			Opcode.SPUT -> {
				val sputFld = FieldInfo.fromRef(root, checkNotNull(insn.indexAsField))
				val sputInsn = IndexInsnNode(InsnType.SPUT, sputFld, 1)
				sputInsn.addArg(InsnArg.reg(insn, 0, tryResolveFieldType(sputFld)))
				return sputInsn
			}

			// ===== 数组 =====
			Opcode.ARRAY_LENGTH -> {
				val arrLenInsn = InsnNode(InsnType.ARRAY_LENGTH, 1)
				arrLenInsn.setResult(InsnArg.reg(insn, 0, ArgType.INT))
				arrLenInsn.addArg(InsnArg.reg(insn, 1, ArgType.array(ArgType.UNKNOWN)))
				return arrLenInsn
			}

			Opcode.AGET -> return arrayGet(insn, ArgType.INT_FLOAT, ArgType.NARROW_NUMBERS_NO_BOOL)

			Opcode.AGET_BOOLEAN -> return arrayGet(insn, ArgType.BOOLEAN)

			Opcode.AGET_BYTE -> return arrayGet(insn, ArgType.BYTE, ArgType.NARROW_INTEGRAL)

			Opcode.AGET_BYTE_BOOLEAN -> return arrayGet(insn, ArgType.BYTE_BOOLEAN)

			Opcode.AGET_CHAR -> return arrayGet(insn, ArgType.CHAR)

			Opcode.AGET_SHORT -> return arrayGet(insn, ArgType.SHORT)

			Opcode.AGET_WIDE -> return arrayGet(insn, ArgType.WIDE)

			Opcode.AGET_OBJECT -> return arrayGet(insn, ArgType.UNKNOWN_OBJECT)

			Opcode.APUT -> return arrayPut(insn, ArgType.INT_FLOAT, ArgType.NARROW_NUMBERS_NO_BOOL)

			Opcode.APUT_BOOLEAN -> return arrayPut(insn, ArgType.BOOLEAN)

			Opcode.APUT_BYTE -> return arrayPut(insn, ArgType.BYTE)

			Opcode.APUT_BYTE_BOOLEAN -> return arrayPut(insn, ArgType.BYTE_BOOLEAN)

			Opcode.APUT_CHAR -> return arrayPut(insn, ArgType.CHAR)

			Opcode.APUT_SHORT -> return arrayPut(insn, ArgType.SHORT)

			Opcode.APUT_WIDE -> return arrayPut(insn, ArgType.WIDE)

			Opcode.APUT_OBJECT -> return arrayPut(insn, ArgType.UNKNOWN_OBJECT)

			Opcode.NEW_ARRAY -> return makeNewArray(insn)

			Opcode.FILL_ARRAY_DATA -> return FillArrayInsn(InsnArg.reg(insn, 0, ArgType.UNKNOWN_ARRAY), insn.target)

			Opcode.FILL_ARRAY_DATA_PAYLOAD -> return FillArrayData(insn.payload as IArrayPayload)

			Opcode.FILLED_NEW_ARRAY -> return filledNewArray(insn, false)

			Opcode.FILLED_NEW_ARRAY_RANGE -> return filledNewArray(insn, true)

			// ===== 方法调用 =====
			Opcode.INVOKE_STATIC -> return invoke(insn, InvokeType.STATIC, false)

			Opcode.INVOKE_STATIC_RANGE -> return invoke(insn, InvokeType.STATIC, true)

			Opcode.INVOKE_DIRECT -> return invoke(insn, InvokeType.DIRECT, false)

			Opcode.INVOKE_INTERFACE -> return invoke(insn, InvokeType.INTERFACE, false)

			Opcode.INVOKE_SUPER -> return invoke(insn, InvokeType.SUPER, false)

			Opcode.INVOKE_VIRTUAL -> return invoke(insn, InvokeType.VIRTUAL, false)

			Opcode.INVOKE_CUSTOM -> return invokeCustom(insn, false)

			Opcode.INVOKE_SPECIAL -> return invokeSpecial(insn)

			Opcode.INVOKE_POLYMORPHIC -> return invokePolymorphic(insn, false)

			Opcode.INVOKE_DIRECT_RANGE -> return invoke(insn, InvokeType.DIRECT, true)

			Opcode.INVOKE_INTERFACE_RANGE -> return invoke(insn, InvokeType.INTERFACE, true)

			Opcode.INVOKE_SUPER_RANGE -> return invoke(insn, InvokeType.SUPER, true)

			Opcode.INVOKE_VIRTUAL_RANGE -> return invoke(insn, InvokeType.VIRTUAL, true)

			Opcode.INVOKE_CUSTOM_RANGE -> return invokeCustom(insn, true)

			Opcode.INVOKE_POLYMORPHIC_RANGE -> return invokePolymorphic(insn, true)

			// ===== new 实例 / switch =====
			Opcode.NEW_INSTANCE -> {
				val clsType = ArgType.parse(insn.indexAsType)
				val newInstInsn = IndexInsnNode(InsnType.NEW_INSTANCE, clsType, 0)
				newInstInsn.setResult(InsnArg.reg(insn, 0, clsType))
				return newInstInsn
			}

			Opcode.PACKED_SWITCH -> return makeSwitch(insn, true)

			Opcode.SPARSE_SWITCH -> return makeSwitch(insn, false)

			Opcode.PACKED_SWITCH_PAYLOAD,
			Opcode.SPARSE_SWITCH_PAYLOAD,
			-> return SwitchData(insn.payload as ISwitchPayload)

			else -> throw DecodeException("Unknown instruction: '$insn'")
		}
	}

	// ======================= 辅助方法 =======================

	private fun makeSwitch(insn: InsnData, packed: Boolean): SwitchInsn {
		val swInsn = SwitchInsn(InsnArg.reg(insn, 0, ArgType.NARROW_INTEGRAL), insn.target, packed)
		val payload = insn.payload
		if (payload != null) {
			swInsn.attachSwitchData(SwitchData(payload as ISwitchPayload), insn.target)
		}
		method.add(AFlag.COMPUTE_POST_DOM)
		CodeFeaturesAttr.add(method, CodeFeature.SWITCH)
		return swInsn
	}

	private fun makeNewArray(insn: InsnData): InsnNode {
		val indexType = ArgType.parse(insn.indexAsType)
		// NEW_ARRAY 的字面量 = 需要用操作数包裹的维度：
		// 0 表示操作数本身已是完整数组类型（dalvik new-array、java multianewarray），
		// 1 表示 newarray/anewarray（操作数是元素类型）
		val dim = insn.literal.toInt()
		val arrType = if (dim == 0) indexType else ArgType.array(indexType, dim)
		val regsCount = insn.regsCount
		val newArr = NewArrayNode(arrType, regsCount - 1)
		newArr.setResult(InsnArg.reg(insn, 0, arrType))
		for (i in 1 until regsCount) {
			newArr.addArg(InsnArg.typeImmutableReg(insn, i, ArgType.INT))
		}
		CodeFeaturesAttr.add(method, CodeFeature.NEW_ARRAY)
		return newArr
	}

	private fun tryResolveFieldType(igetFld: FieldInfo): ArgType {
		val fieldNode: FieldNode? = root.resolveField(igetFld)
		if (fieldNode != null) {
			return fieldNode.type
		}
		return igetFld.type
	}

	private fun filledNewArray(insn: InsnData, isRange: Boolean): InsnNode {
		val arrType = ArgType.parse(insn.indexAsType)
		val elType = checkNotNull(arrType.getArrayElement())
		val typeImmutable = elType.isPrimitive()
		val regsCount = insn.regsCount
		val regs = arrayOfNulls<InsnArg>(regsCount)
		if (isRange) {
			var r = insn.getReg(0)
			for (i in 0 until regsCount) {
				regs[i] = InsnArg.reg(r, elType, typeImmutable)
				r++
			}
		} else {
			for (i in 0 until regsCount) {
				val regNum = insn.getReg(i)
				regs[i] = InsnArg.reg(regNum, elType, typeImmutable)
			}
		}
		val node = FilledNewArrayNode(elType, regs.size)
		// node.setResult(resReg == -1 ? null : InsnArg.reg(resReg, arrType));
		for (arg in regs) {
			node.addArg(checkNotNull(arg))
		}
		return node
	}

	private fun cmp(insn: InsnData, itype: InsnType, argType: ArgType): InsnNode {
		val inode = InsnNode(itype, 2)
		inode.setResult(InsnArg.reg(insn, 0, ArgType.INT))
		inode.addArg(InsnArg.reg(insn, 1, argType))
		inode.addArg(InsnArg.reg(insn, 2, argType))
		return inode
	}

	private fun cast(insn: InsnData, from: ArgType, to: ArgType): InsnNode {
		val inode = IndexInsnNode(InsnType.CAST, to, 1)
		inode.setResult(InsnArg.reg(insn, 0, to))
		inode.addArg(InsnArg.reg(insn, 1, from))
		return inode
	}

	private fun invokeCustom(insn: InsnData, isRange: Boolean): InsnNode = InvokeCustomBuilder.build(method, insn, isRange)

	private fun invokePolymorphic(insn: InsnData, isRange: Boolean): InsnNode {
		val mthRef = InsnDataUtils.getMethodRef(insn)
			?: throw JadxRuntimeException("Failed to load method reference for insn: $insn")
		val callMth = MethodInfo.fromRef(root, mthRef)
		val proto = checkNotNull(insn.getIndexAsProto(insn.target))

		// 展开调用参数
		val args = Utils.collectionMap(proto.argTypes) { ArgType.parse(it) }
		val returnType = ArgType.parse(proto.returnType)
		val effectiveCallMth = MethodInfo.fromDetails(root, callMth.declClass, callMth.name, args, returnType)
		return InvokePolymorphicNode(effectiveCallMth, insn, proto, callMth, isRange)
	}

	private fun invokeSpecial(insn: InsnData): InsnNode {
		val mthRef = InsnDataUtils.getMethodRef(insn)
			?: throw JadxRuntimeException("Failed to load method reference for insn: $insn")
		val mthInfo = MethodInfo.fromRef(root, mthRef)
		// 与 dx 一致：把 'special' 转换为 'direct/super'
		val type: InvokeType = if (mthInfo.isConstructor() || mthInfo.declClass == method.parentClass.classInfo) {
			InvokeType.DIRECT
		} else {
			InvokeType.SUPER
		}
		return InvokeNode(mthInfo, insn, type, false)
	}

	private fun invoke(insn: InsnData, type: InvokeType, isRange: Boolean): InsnNode {
		val mthRef = InsnDataUtils.getMethodRef(insn)
			?: throw JadxRuntimeException("Failed to load method reference for insn: $insn")
		val mthInfo = MethodInfo.fromRef(root, mthRef)
		return InvokeNode(mthInfo, insn, type, isRange)
	}

	private fun arrayGet(insn: InsnData, argType: ArgType): InsnNode = arrayGet(insn, argType, argType)

	private fun arrayGet(insn: InsnData, arrElemType: ArgType, resType: ArgType): InsnNode {
		val inode = InsnNode(InsnType.AGET, 2)
		inode.setResult(InsnArg.typeImmutableIfKnownReg(insn, 0, resType))
		inode.addArg(InsnArg.typeImmutableIfKnownReg(insn, 1, ArgType.array(arrElemType)))
		inode.addArg(InsnArg.reg(insn, 2, ArgType.NARROW_INTEGRAL))
		return inode
	}

	private fun arrayPut(insn: InsnData, argType: ArgType): InsnNode = arrayPut(insn, argType, argType)

	private fun arrayPut(insn: InsnData, arrElemType: ArgType, argType: ArgType): InsnNode {
		val inode = InsnNode(InsnType.APUT, 3)
		inode.addArg(InsnArg.typeImmutableIfKnownReg(insn, 1, ArgType.array(arrElemType)))
		inode.addArg(InsnArg.reg(insn, 2, ArgType.NARROW_INTEGRAL))
		inode.addArg(InsnArg.typeImmutableIfKnownReg(insn, 0, argType))
		return inode
	}

	private fun arith(insn: InsnData, op: ArithOp, type: ArgType): InsnNode = ArithNode.build(insn, op, type)

	private fun arithLit(insn: InsnData, op: ArithOp, type: ArgType): InsnNode = ArithNode.buildLit(insn, op, type)

	private fun neg(insn: InsnData, type: ArgType): InsnNode {
		val inode = InsnNode(InsnType.NEG, 1)
		inode.setResult(InsnArg.reg(insn, 0, type))
		inode.addArg(InsnArg.reg(insn, 1, type))
		return inode
	}

	private fun not(insn: InsnData, type: ArgType): InsnNode {
		val inode = InsnNode(InsnType.NOT, 1)
		inode.setResult(InsnArg.reg(insn, 0, type))
		inode.addArg(InsnArg.reg(insn, 1, type))
		return inode
	}

	private fun makeInsn(type: InsnType, res: RegisterArg?): InsnNode {
		val node = InsnNode(type, 0)
		node.setResult(res)
		return node
	}

	private fun makeInsn(type: InsnType, res: RegisterArg?, arg: InsnArg): InsnNode {
		val node = InsnNode(type, 1)
		node.setResult(res)
		node.addArg(arg)
		return node
	}
}
