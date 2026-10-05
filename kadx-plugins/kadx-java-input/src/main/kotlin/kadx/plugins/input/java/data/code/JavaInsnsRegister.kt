package kadx.plugins.input.java.data.code

import kadx.api.plugins.input.insns.InsnIndexType
import kadx.api.plugins.input.insns.Opcode
import kadx.plugins.input.java.data.attributes.stack.StackValueType
import kadx.plugins.input.java.data.attributes.stack.StackValueType.NARROW
import kadx.plugins.input.java.data.attributes.stack.StackValueType.WIDE
import kadx.plugins.input.java.data.code.decoders.IJavaInsnDecoder
import kadx.plugins.input.java.data.code.decoders.InvokeDecoder
import kadx.plugins.input.java.data.code.decoders.LoadConstDecoder
import kadx.plugins.input.java.data.code.decoders.LookupSwitchDecoder
import kadx.plugins.input.java.data.code.decoders.TableSwitchDecoder
import kadx.plugins.input.java.data.code.decoders.WideDecoder
import org.jetbrains.annotations.Nullable

/**
 * JVM 字节码指令注册表（opcode → [JavaInsnInfo]）。
 *
 **做什么**：类加载时一次性登记全部 0x00..0xC9 操作码——助记符、payload 大小、寄存器数、
 * API opcode、索引类型与解码器；解码主循环按 opcode 号取用。
 * 解码器是"读操作数 + 分配寄存器"的声明式 lambda（或专用 [IJavaInsnDecoder]）。
 *
 **为什么用数组而不是 map**：opcode 是稠密小整数，数组直接下标更快更省内存；
 * 未登记槽位保持 null，[get] 原样返回。
 */
object JavaInsnsRegister {

	// float/double 常量在 ldc 指令里以"位模式 long"形式存入字面量字段（与原 Java static final 相同）
	val FLOAT_ZERO: Long = 0.0f.toRawBits().toLong()

	val FLOAT_ONE: Long = 1.0f.toRawBits().toLong()

	val FLOAT_TWO: Long = 2.0f.toRawBits().toLong()

	val DOUBLE_ZERO: Long = 0.0.toRawBits()

	val DOUBLE_ONE: Long = 1.0.toRawBits()

	private val INSN_INFO: Array<JavaInsnInfo?> = buildInsnInfo()

	/** @return [opcode] 对应的指令信息；未登记的 opcode 返回 null */
	@Nullable
	fun get(opcode: Int): JavaInsnInfo? = INSN_INFO[opcode]

	private fun buildInsnInfo(): Array<JavaInsnInfo?> {
		val arr = arrayOfNulls<JavaInsnInfo>(0xCA)
		register(arr, 0x00, "nop", 0, 0, Opcode.NOP, null)

		constInsn(arr, 0x01, "aconst_null", Opcode.CONST, 0L)
		constInsn(arr, 0x02, "iconst_m1", Opcode.CONST, -1L)
		constInsn(arr, 0x03, "iconst_0", Opcode.CONST, 0L)
		constInsn(arr, 0x04, "iconst_1", Opcode.CONST, 1L)
		constInsn(arr, 0x05, "iconst_2", Opcode.CONST, 2L)
		constInsn(arr, 0x06, "iconst_3", Opcode.CONST, 3L)
		constInsn(arr, 0x07, "iconst_4", Opcode.CONST, 4L)
		constInsn(arr, 0x08, "iconst_5", Opcode.CONST, 5L)

		constInsn(arr, 0x09, "lconst_0", Opcode.CONST_WIDE, 0L)
		constInsn(arr, 0x0a, "lconst_1", Opcode.CONST_WIDE, 1L)

		constInsn(arr, 0x0b, "fconst_0", Opcode.CONST, FLOAT_ZERO)
		constInsn(arr, 0x0c, "fconst_1", Opcode.CONST, FLOAT_ONE)
		constInsn(arr, 0x0d, "fconst_2", Opcode.CONST, FLOAT_TWO)

		constInsn(arr, 0x0e, "dconst_0", Opcode.CONST_WIDE, DOUBLE_ZERO)
		constInsn(arr, 0x0f, "dconst_1", Opcode.CONST_WIDE, DOUBLE_ONE)

		register(arr, 0x10, "bipush", 1, 2, Opcode.CONST) { s -> s.lit(s.s1().toLong()).push(0) }
		register(arr, 0x11, "sipush", 2, 2, Opcode.CONST) { s -> s.lit(s.s2().toLong()).push(0) }

		loadConst(arr, 0x12, "ldc", false)
		loadConst(arr, 0x13, "ldc_w", true)
		loadConst(arr, 0x14, "ldc2_w", true)

		register(arr, 0x15, "iload", 1, 2, Opcode.MOVE) { s -> s.local(1, s.u1()).push(0) }
		register(arr, 0x16, "lload", 1, 2, Opcode.MOVE_WIDE) { s -> s.local(1, s.u1()).pushWide(0) }
		register(arr, 0x17, "fload", 1, 2, Opcode.MOVE) { s -> s.local(1, s.u1()).push(0) }
		register(arr, 0x18, "dload", 1, 2, Opcode.MOVE_WIDE) { s -> s.local(1, s.u1()).pushWide(0) }
		register(arr, 0x19, "aload", 1, 2, Opcode.MOVE) { s -> s.local(1, s.u1()).push(0) }

		register(arr, 0x1a, "iload_0", 0, 2, Opcode.MOVE) { s -> s.local(1, 0).push(0) }
		register(arr, 0x1b, "iload_1", 0, 2, Opcode.MOVE) { s -> s.local(1, 1).push(0) }
		register(arr, 0x1c, "iload_2", 0, 2, Opcode.MOVE) { s -> s.local(1, 2).push(0) }
		register(arr, 0x1d, "iload_3", 0, 2, Opcode.MOVE) { s -> s.local(1, 3).push(0) }

		register(arr, 0x1e, "lload_0", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 0).pushWide(0) }
		register(arr, 0x1f, "lload_1", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 1).pushWide(0) }
		register(arr, 0x20, "lload_2", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 2).pushWide(0) }
		register(arr, 0x21, "lload_3", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 3).pushWide(0) }

		register(arr, 0x22, "fload_0", 0, 2, Opcode.MOVE) { s -> s.local(1, 0).push(0) }
		register(arr, 0x23, "fload_1", 0, 2, Opcode.MOVE) { s -> s.local(1, 1).push(0) }
		register(arr, 0x24, "fload_2", 0, 2, Opcode.MOVE) { s -> s.local(1, 2).push(0) }
		register(arr, 0x25, "fload_3", 0, 2, Opcode.MOVE) { s -> s.local(1, 3).push(0) }

		register(arr, 0x26, "dload_0", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 0).pushWide(0) }
		register(arr, 0x27, "dload_1", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 1).pushWide(0) }
		register(arr, 0x28, "dload_2", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 2).pushWide(0) }
		register(arr, 0x29, "dload_3", 0, 2, Opcode.MOVE_WIDE) { s -> s.local(1, 3).pushWide(0) }

		register(arr, 0x2a, "aload_0", 0, 2, Opcode.MOVE) { s -> s.local(1, 0).push(0) }
		register(arr, 0x2b, "aload_1", 0, 2, Opcode.MOVE) { s -> s.local(1, 1).push(0) }
		register(arr, 0x2c, "aload_2", 0, 2, Opcode.MOVE) { s -> s.local(1, 2).push(0) }
		register(arr, 0x2d, "aload_3", 0, 2, Opcode.MOVE) { s -> s.local(1, 3).push(0) }

		register(arr, 0x2e, "iaload", 0, 3, Opcode.AGET, aget())
		register(arr, 0x2f, "laload", 0, 3, Opcode.AGET_WIDE, agetWide())
		register(arr, 0x30, "faload", 0, 3, Opcode.AGET, aget())
		register(arr, 0x31, "daload", 0, 3, Opcode.AGET_WIDE, agetWide())
		register(arr, 0x32, "aaload", 0, 3, Opcode.AGET_OBJECT, aget())
		register(arr, 0x33, "baload", 0, 3, Opcode.AGET_BYTE_BOOLEAN, aget())
		register(arr, 0x34, "caload", 0, 3, Opcode.AGET_CHAR, aget())
		register(arr, 0x35, "saload", 0, 3, Opcode.AGET_SHORT, aget())
		register(arr, 0x36, "istore", 1, 2, Opcode.MOVE) { s -> s.pop(1).local(0, s.u1()) }
		register(arr, 0x37, "lstore", 1, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, s.u1()) }
		register(arr, 0x38, "fstore", 1, 2, Opcode.MOVE) { s -> s.pop(1).local(0, s.u1()) }
		register(arr, 0x39, "dstore", 1, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, s.u1()) }
		register(arr, 0x3a, "astore", 1, 2, Opcode.MOVE) { s -> s.pop(1).local(0, s.u1()) }

		register(arr, 0x3b, "istore_0", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 0) }
		register(arr, 0x3c, "istore_1", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 1) }
		register(arr, 0x3d, "istore_2", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 2) }
		register(arr, 0x3e, "istore_3", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 3) }

		register(arr, 0x3f, "lstore_0", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 0) }
		register(arr, 0x40, "lstore_1", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 1) }
		register(arr, 0x41, "lstore_2", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 2) }
		register(arr, 0x42, "lstore_3", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 3) }

		register(arr, 0x43, "fstore_0", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 0) }
		register(arr, 0x44, "fstore_1", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 1) }
		register(arr, 0x45, "fstore_2", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 2) }
		register(arr, 0x46, "fstore_3", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 3) }

		register(arr, 0x47, "dstore_0", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 0) }
		register(arr, 0x48, "dstore_1", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 1) }
		register(arr, 0x49, "dstore_2", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 2) }
		register(arr, 0x4a, "dstore_3", 0, 2, Opcode.MOVE_WIDE) { s -> s.pop(1).local(0, 3) }

		register(arr, 0x4b, "astore_0", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 0) }
		register(arr, 0x4c, "astore_1", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 1) }
		register(arr, 0x4d, "astore_2", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 2) }
		register(arr, 0x4e, "astore_3", 0, 2, Opcode.MOVE) { s -> s.pop(1).local(0, 3) }

		register(arr, 0x4f, "iastore", 0, 3, Opcode.APUT, aput())
		register(arr, 0x50, "lastore", 0, 3, Opcode.APUT_WIDE, aput())
		register(arr, 0x51, "fastore", 0, 3, Opcode.APUT, aput())
		register(arr, 0x52, "dastore", 0, 3, Opcode.APUT_WIDE, aput())
		register(arr, 0x53, "aastore", 0, 3, Opcode.APUT_OBJECT, aput())
		register(arr, 0x54, "bastore", 0, 3, Opcode.APUT_BYTE_BOOLEAN, aput())
		register(arr, 0x55, "castore", 0, 3, Opcode.APUT_CHAR, aput())
		register(arr, 0x56, "sastore", 0, 3, Opcode.APUT_SHORT, aput())

		register(arr, 0x57, "pop", 0, 0, Opcode.NOP) { s -> s.discard() }
		register(arr, 0x58, "pop2", 0, 0, Opcode.NOP) { s -> s.discardWord() }

		register(arr, 0x59, "dup", 0, 2, Opcode.MOVE) { s -> s.peek(1).push(0, s.peekType(1)) }
		register(arr, 0x5a, "dup_x1", 0, 6, Opcode.MOVE_MULTI) { s ->
			s.push(0, s.peekType(1)).peekFrom(1, 1)
				.peekFrom(1, 2).peekFrom(2, 3)
				.peekFrom(2, 4).peekFrom(0, 5)
		}
		register(arr, 0x5b, "dup_x2", 0, 8, Opcode.MOVE_MULTI) { s ->
			s.push(0, s.peekType(1)).peekFrom(1, 1)
				.peekFrom(1, 2).peekFrom(2, 3)
				.peekFrom(2, 4).peekFrom(3, 5)
				.peekFrom(3, 6).peekFrom(0, 7)
		}
		register(arr, 0x5c, "dup2", 0, 4, Opcode.MOVE_MULTI) { s ->
			if (s.peekType(0) == NARROW) {
				s.peekFrom(0, 3).peekFrom(1, 1).push(0, NARROW).push(2, NARROW)
			} else {
				s.peek(1).push(0, s.peekType(1))
			}
		}
		register(arr, 0x5d, "dup2_x1", 0, 10, Opcode.MOVE_MULTI) { s -> dup2x1(s) }
		register(arr, 0x5e, "dup2_x2", 0, 12, Opcode.MOVE_MULTI) { s -> dup2x2(s) }
		register(arr, 0x5f, "swap", 0, 6, Opcode.MOVE_MULTI) { s ->
			s.peekFrom(-1, 0).peekFrom(1, 1)
				.peekFrom(1, 2).peekFrom(0, 3)
				.peekFrom(0, 4).peekFrom(-1, 5)
		}

		register(arr, 0x60, "iadd", 0, 3, Opcode.ADD_INT, twoRegsWithResult(NARROW))
		register(arr, 0x61, "ladd", 0, 3, Opcode.ADD_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x62, "fadd", 0, 3, Opcode.ADD_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x63, "dadd", 0, 3, Opcode.ADD_DOUBLE, twoRegsWithResult(WIDE))

		register(arr, 0x64, "isub", 0, 3, Opcode.SUB_INT, twoRegsWithResult(NARROW))
		register(arr, 0x65, "lsub", 0, 3, Opcode.SUB_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x66, "fsub", 0, 3, Opcode.SUB_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x67, "dsub", 0, 3, Opcode.SUB_DOUBLE, twoRegsWithResult(WIDE))

		register(arr, 0x68, "imul", 0, 3, Opcode.MUL_INT, twoRegsWithResult(NARROW))
		register(arr, 0x69, "lmul", 0, 3, Opcode.MUL_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x6a, "fmul", 0, 3, Opcode.MUL_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x6b, "dmul", 0, 3, Opcode.MUL_DOUBLE, twoRegsWithResult(WIDE))

		register(arr, 0x6c, "idiv", 0, 3, Opcode.DIV_INT, twoRegsWithResult(NARROW))
		register(arr, 0x6d, "ldiv", 0, 3, Opcode.DIV_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x6e, "fdiv", 0, 3, Opcode.DIV_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x6f, "ddiv", 0, 3, Opcode.DIV_DOUBLE, twoRegsWithResult(WIDE))

		register(arr, 0x70, "irem", 0, 3, Opcode.REM_INT, twoRegsWithResult(NARROW))
		register(arr, 0x71, "lrem", 0, 3, Opcode.REM_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x72, "frem", 0, 3, Opcode.REM_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x73, "drem", 0, 3, Opcode.REM_DOUBLE, twoRegsWithResult(WIDE))

		register(arr, 0x74, "ineg", 0, 2, Opcode.NEG_INT, oneRegWithResult(NARROW))
		register(arr, 0x75, "lneg", 0, 2, Opcode.NEG_LONG, oneRegWithResult(WIDE))
		register(arr, 0x76, "fneg", 0, 2, Opcode.NEG_FLOAT, oneRegWithResult(NARROW))
		register(arr, 0x77, "dneg", 0, 2, Opcode.NEG_DOUBLE, oneRegWithResult(WIDE))

		register(arr, 0x78, "ishl", 0, 3, Opcode.SHL_INT, twoRegsWithResult(NARROW))
		register(arr, 0x79, "lshl", 0, 3, Opcode.SHL_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x7a, "ishr", 0, 3, Opcode.SHR_INT, twoRegsWithResult(NARROW))
		register(arr, 0x7b, "lshr", 0, 3, Opcode.SHR_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x7c, "iushr", 0, 3, Opcode.USHR_INT, twoRegsWithResult(NARROW))
		register(arr, 0x7d, "lushr", 0, 3, Opcode.USHR_LONG, twoRegsWithResult(WIDE))

		register(arr, 0x7e, "iand", 0, 3, Opcode.AND_INT, twoRegsWithResult(NARROW))
		register(arr, 0x7f, "land", 0, 3, Opcode.AND_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x80, "ior", 0, 3, Opcode.OR_INT, twoRegsWithResult(NARROW))
		register(arr, 0x81, "lor", 0, 3, Opcode.OR_LONG, twoRegsWithResult(WIDE))
		register(arr, 0x82, "ixor", 0, 3, Opcode.XOR_INT, twoRegsWithResult(NARROW))
		register(arr, 0x83, "lxor", 0, 3, Opcode.XOR_LONG, twoRegsWithResult(WIDE))

		register(arr, 0x84, "iinc", 2, 2, Opcode.ADD_INT_LIT) { s ->
			val varNum = s.u1()
			s.local(0, varNum).local(1, varNum).lit(s.reader().readS1().toLong())
		}

		register(arr, 0x85, "i2l", 0, 2, Opcode.INT_TO_LONG, oneRegWithResult(WIDE))
		register(arr, 0x86, "i2f", 0, 2, Opcode.INT_TO_FLOAT, oneRegWithResult(NARROW))
		register(arr, 0x87, "i2d", 0, 2, Opcode.INT_TO_DOUBLE, oneRegWithResult(WIDE))
		register(arr, 0x88, "l2i", 0, 2, Opcode.LONG_TO_INT, oneRegWithResult(NARROW))
		register(arr, 0x89, "l2f", 0, 2, Opcode.LONG_TO_FLOAT, oneRegWithResult(NARROW))
		register(arr, 0x8a, "l2d", 0, 2, Opcode.LONG_TO_DOUBLE, oneRegWithResult(WIDE))
		register(arr, 0x8b, "f2i", 0, 2, Opcode.FLOAT_TO_INT, oneRegWithResult(NARROW))
		register(arr, 0x8c, "f2l", 0, 2, Opcode.FLOAT_TO_LONG, oneRegWithResult(WIDE))
		register(arr, 0x8d, "f2d", 0, 2, Opcode.FLOAT_TO_DOUBLE, oneRegWithResult(WIDE))
		register(arr, 0x8e, "d2i", 0, 2, Opcode.DOUBLE_TO_INT, oneRegWithResult(NARROW))
		register(arr, 0x8f, "d2l", 0, 2, Opcode.DOUBLE_TO_LONG, oneRegWithResult(WIDE))
		register(arr, 0x90, "d2f", 0, 2, Opcode.DOUBLE_TO_FLOAT, oneRegWithResult(NARROW))
		register(arr, 0x91, "i2b", 0, 2, Opcode.INT_TO_BYTE, oneRegWithResult(NARROW))
		register(arr, 0x92, "i2c", 0, 2, Opcode.INT_TO_CHAR, oneRegWithResult(NARROW))
		register(arr, 0x93, "i2s", 0, 2, Opcode.INT_TO_SHORT, oneRegWithResult(NARROW))

		register(arr, 0x94, "lcmp", 0, 3, Opcode.CMP_LONG, twoRegsWithResult(NARROW))
		register(arr, 0x95, "fcmpl", 0, 3, Opcode.CMPL_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x96, "fcmpg", 0, 3, Opcode.CMPG_FLOAT, twoRegsWithResult(NARROW))
		register(arr, 0x97, "dcmpl", 0, 3, Opcode.CMPL_DOUBLE, twoRegsWithResult(NARROW))
		register(arr, 0x98, "dcmpg", 0, 3, Opcode.CMPG_DOUBLE, twoRegsWithResult(NARROW))
		register(arr, 0x99, "ifeq", 2, 1, Opcode.IF_EQZ, zeroCmp())
		register(arr, 0x9a, "ifne", 2, 1, Opcode.IF_NEZ, zeroCmp())
		register(arr, 0x9b, "iflt", 2, 1, Opcode.IF_LTZ, zeroCmp())
		register(arr, 0x9c, "ifge", 2, 1, Opcode.IF_GEZ, zeroCmp())
		register(arr, 0x9d, "ifgt", 2, 1, Opcode.IF_GTZ, zeroCmp())
		register(arr, 0x9e, "ifle", 2, 1, Opcode.IF_LEZ, zeroCmp())

		register(arr, 0x9f, "if_icmpeq", 2, 2, Opcode.IF_EQ, cmp())
		register(arr, 0xa0, "if_icmpne", 2, 2, Opcode.IF_NE, cmp())
		register(arr, 0xa1, "if_icmplt", 2, 2, Opcode.IF_LT, cmp())
		register(arr, 0xa2, "if_icmpge", 2, 2, Opcode.IF_GE, cmp())
		register(arr, 0xa3, "if_icmpgt", 2, 2, Opcode.IF_GT, cmp())
		register(arr, 0xa4, "if_icmple", 2, 2, Opcode.IF_LE, cmp())
		register(arr, 0xa5, "if_acmpeq", 2, 2, Opcode.IF_EQ, cmp())
		register(arr, 0xa6, "if_acmpne", 2, 2, Opcode.IF_NE, cmp())

		register(arr, 0xa7, "goto", 2, 0, Opcode.GOTO) { s -> s.jump(s.s2()) }
		register(arr, 0xa8, "jsr", 2, 1, Opcode.JAVA_JSR) { s -> s.push(0).jump(s.s2()) }
		register(arr, 0xa9, "ret", 1, 1, Opcode.JAVA_RET) { s -> s.local(0, s.u1()) }

		register(arr, 0xaa, "tableswitch", -1, 1, Opcode.PACKED_SWITCH, TableSwitchDecoder())
		register(arr, 0xab, "lookupswitch", -1, 1, Opcode.SPARSE_SWITCH, LookupSwitchDecoder())

		register(arr, 0xac, "ireturn", 0, 1, Opcode.RETURN) { s -> s.pop(0) }
		register(arr, 0xad, "lreturn", 0, 1, Opcode.RETURN) { s -> s.pop(0) }
		register(arr, 0xae, "freturn", 0, 1, Opcode.RETURN) { s -> s.pop(0) }
		register(arr, 0xaf, "dreturn", 0, 1, Opcode.RETURN) { s -> s.pop(0) }
		register(arr, 0xb0, "areturn", 0, 1, Opcode.RETURN) { s -> s.pop(0) }
		register(arr, 0xb1, "return", 0, 0, Opcode.RETURN_VOID, null)

		register(arr, 0xb2, "getstatic", 2, 1, Opcode.SGET, InsnIndexType.FIELD_REF) { s -> s.idx(s.u2()).push(0, s.fieldType()) }
		register(arr, 0xb3, "putstatic", 2, 1, Opcode.SPUT, InsnIndexType.FIELD_REF) { s -> s.idx(s.u2()).pop(0) }
		register(arr, 0xb4, "getfield", 2, 2, Opcode.IGET, InsnIndexType.FIELD_REF) { s -> s.idx(s.u2()).pop(1).push(0, s.fieldType()) }
		register(arr, 0xb5, "putfield", 2, 2, Opcode.IPUT, InsnIndexType.FIELD_REF) { s -> s.idx(s.u2()).pop(0).pop(1) }

		invoke(arr, 0xb6, "invokevirtual", 2, Opcode.INVOKE_VIRTUAL)
		invoke(arr, 0xb7, "invokespecial", 2, Opcode.INVOKE_SPECIAL)
		invoke(arr, 0xb8, "invokestatic", 2, Opcode.INVOKE_STATIC)
		invoke(arr, 0xb9, "invokeinterface", 4, Opcode.INVOKE_INTERFACE)
		invoke(arr, 0xba, "invokedynamic", 4, Opcode.INVOKE_CUSTOM)

		register(arr, 0xbb, "new", 2, 1, Opcode.NEW_INSTANCE, InsnIndexType.TYPE_REF) { s -> s.idx(s.u2()).push(0) }
		register(arr, 0xbc, "newarray", 1, 2, Opcode.NEW_ARRAY, InsnIndexType.TYPE_REF) { s -> s.idx(s.u1()).pop(1).push(0).lit(1L) }
		register(arr, 0xbd, "anewarray", 2, 2, Opcode.NEW_ARRAY, InsnIndexType.TYPE_REF) { s -> s.idx(s.u2()).pop(1).push(0).lit(1L) }
		register(arr, 0xbe, "arraylength", 0, 2, Opcode.ARRAY_LENGTH, oneRegWithResult(NARROW))
		register(arr, 0xbf, "athrow", 0, 1, Opcode.THROW) { s -> s.pop(0).clear() }

		register(arr, 0xc0, "checkcast", 2, 2, Opcode.CHECK_CAST, InsnIndexType.TYPE_REF) { s -> s.idx(s.u2()).pop(1).push(0) }
		register(arr, 0xc1, "instanceof", 2, 2, Opcode.INSTANCE_OF, InsnIndexType.TYPE_REF) { s -> s.idx(s.u2()).pop(1).push(0) }

		register(arr, 0xc2, "monitorenter", 0, 1, Opcode.MONITOR_ENTER) { s -> s.pop(0) }
		register(arr, 0xc3, "monitorexit", 0, 1, Opcode.MONITOR_EXIT) { s -> s.pop(0) }

		register(arr, 0xc4, "wide", -1, -1, Opcode.NOP, WideDecoder())

		register(arr, 0xc5, "multianewarray", 3, -1, Opcode.NEW_ARRAY, InsnIndexType.TYPE_REF, newArrayMulti())
		register(arr, 0xc6, "ifnull", 2, 1, Opcode.IF_EQZ, zeroCmp())
		register(arr, 0xc7, "ifnonnull", 2, 1, Opcode.IF_NEZ, zeroCmp())

		register(arr, 0xc8, "goto_w", 4, 0, Opcode.GOTO) { s -> s.jump(s.reader().readS4()) }
		register(arr, 0xc9, "jsr_w", 4, 1, Opcode.JAVA_JSR) { s -> s.push(0).jump(s.reader().readS4()) }

		return arr
	}

	private fun dup2x1(s: CodeDecodeState) {
		if (s.peekType(0) == NARROW) {
			s.insert(2, NARROW)
			s.insert(2, NARROW)
			s.peekFrom(0, 0).peekFrom(2, 1)
			s.peekFrom(1, 2).peekFrom(3, 3)
			s.peekFrom(2, 4).peekFrom(4, 5)
			s.peekFrom(3, 6).peekFrom(0, 7)
			s.peekFrom(4, 8).peekFrom(1, 9)
		} else {
			s.insn().setRegsCount(6)
			s.insert(1, WIDE)
			s.peekFrom(0, 0).peekFrom(1, 1)
			s.peekFrom(1, 2).peekFrom(2, 3)
			s.peekFrom(2, 4).peekFrom(0, 5)
		}
	}

	private fun dup2x2(s: CodeDecodeState) {
		if (s.peekType(0) == NARROW) {
			s.insert(2, NARROW)
			s.insert(2, NARROW)
			s.peekFrom(0, 0).peekFrom(2, 1)
			s.peekFrom(1, 2).peekFrom(3, 3)
			s.peekFrom(2, 4).peekFrom(4, 5)
			s.peekFrom(3, 6).peekFrom(5, 7)
			s.peekFrom(4, 8).peekFrom(0, 9)
			s.peekFrom(5, 10).peekFrom(1, 11)
		} else {
			s.insn().setRegsCount(8)
			s.insert(2, WIDE)
			s.peekFrom(0, 0).peekFrom(1, 1)
			s.peekFrom(1, 2).peekFrom(2, 3)
			s.peekFrom(2, 4).peekFrom(3, 5)
			s.peekFrom(3, 6).peekFrom(0, 7)
		}
	}

	private fun newArrayMulti(): IJavaInsnDecoder = IJavaInsnDecoder { s ->
		s.idx(s.u2())
		val dim = s.u1()
		val insn = s.insn()
		// operand is already the full array type (like new-array): literal 0 = no wrapping; the
		// dimension count is carried by regsCount
		insn.setLiteral(0)
		insn.setRegsCount(dim + 1)
		for (i in dim downTo 1) {
			s.pop(i)
		}
		s.push(0)
	}

	private fun oneRegWithResult(type: StackValueType): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(1).push(0, type) }

	private fun twoRegsWithResult(type: StackValueType): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(2).pop(1).push(0, type) }

	private fun aget(): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(2).pop(1).push(0) }

	private fun agetWide(): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(2).pop(1).pushWide(0) }

	private fun aput(): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(0).pop(2).pop(1) }

	private fun zeroCmp(): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(0).jump(s.s2()) }

	private fun cmp(): IJavaInsnDecoder = IJavaInsnDecoder { s -> s.pop(1).pop(0).jump(s.s2()) }

	private fun invoke(arr: Array<JavaInsnInfo?>, opcode: Int, name: String, payloadSize: Int, apiOpcode: Opcode) {
		val indexType = if (apiOpcode == Opcode.INVOKE_CUSTOM) InsnIndexType.CALL_SITE else InsnIndexType.METHOD_REF
		register(arr, opcode, name, payloadSize, -1, apiOpcode, indexType, InvokeDecoder(payloadSize, apiOpcode))
	}

	private fun constInsn(arr: Array<JavaInsnInfo?>, opcode: Int, name: String, apiOpcode: Opcode, literal: Long) {
		register(arr, opcode, name, 0, 1, apiOpcode, InsnIndexType.NONE) { s ->
			s.insn().setLiteral(literal)
			s.push(0, if (apiOpcode == Opcode.CONST_WIDE) StackValueType.WIDE else NARROW)
		}
	}

	private fun loadConst(arr: Array<JavaInsnInfo?>, opcode: Int, name: String, wide: Boolean) {
		register(arr, opcode, name, if (wide) 2 else 1, 2, Opcode.CONST, InsnIndexType.NONE, LoadConstDecoder(wide))
	}

	private fun register(
		arr: Array<JavaInsnInfo?>,
		opcode: Int,
		name: String,
		payloadSize: Int,
		regsCount: Int,
		apiOpcode: Opcode,
		decoder: IJavaInsnDecoder?,
	) {
		register(arr, opcode, name, payloadSize, regsCount, apiOpcode, InsnIndexType.NONE, decoder)
	}

	private fun register(
		arr: Array<JavaInsnInfo?>,
		opcode: Int,
		name: String,
		payloadSize: Int,
		regsCount: Int,
		apiOpcode: Opcode,
		indexType: InsnIndexType,
		decoder: IJavaInsnDecoder?,
	) {
		if (arr[opcode] != null) {
			throw IllegalStateException("Duplicate opcode init: 0x" + Integer.toHexString(opcode))
		}
		arr[opcode] = JavaInsnInfo(opcode, name, payloadSize, regsCount, apiOpcode, indexType, decoder)
	}
}
