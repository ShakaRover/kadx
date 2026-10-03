package jadx.plugins.input.dex.insns

import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import org.jetbrains.annotations.Nullable
import java.util.concurrent.ConcurrentHashMap

/**
 * 指令元数据：原始操作码 → API 操作码 + [DexInsnFormat] + 索引类型的查表条目。
 *
 **背景**：companion 的 init 块按 DEX 规范注册全部 ~250 个操作码到 [INSN_INFO]（普通指令）
 * 与 [PAYLOAD_INFO]（payload 伪指令，opcodeUnit 高字节非 0）；[DexCodeReader] 每读一条
 * 指令就通过 [get] 查表拿到格式信息驱动解码。
 *
 **Kotlin 转换说明**：四个字段全部为公开 val（Kotlin 调用方用属性语法，如 DexCodeReader
 * 的 `insnInfo.format`）；原 Java 静态块 → companion init 块，注册逻辑逐行机械对应。
 */
public class DexInsnInfo(
	/** 原始 DEX 操作码值（0-255，payload 为完整 opcodeUnit）*/
	public val opcode: Int,
	/** 映射到插件 API 的 [Opcode] 枚举 */
	public val apiOpcode: Opcode,
	/** 该指令的操作数格式（决定解码方式与长度）*/
	public val format: DexInsnFormat,
	/** 索引字段指向的池类型（字符串/类型/字段/方法引用等）*/
	public val indexType: InsnIndexType,
) {

	companion object {
		private val INSN_INFO: Array<DexInsnInfo?> = Array(0x100) { null }
		private val PAYLOAD_INFO: MutableMap<Int, DexInsnInfo> = ConcurrentHashMap(3)

		init {
			register(INSN_INFO, DexOpcodes.NOP, Opcode.NOP, DexInsnFormat.FORMAT_10X)

			register(INSN_INFO, DexOpcodes.MOVE, Opcode.MOVE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MOVE_FROM16, Opcode.MOVE, DexInsnFormat.FORMAT_22X)
			register(INSN_INFO, DexOpcodes.MOVE_16, Opcode.MOVE, DexInsnFormat.FORMAT_32X)

			register(INSN_INFO, DexOpcodes.MOVE_WIDE, Opcode.MOVE_WIDE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE, DexInsnFormat.FORMAT_22X)
			register(INSN_INFO, DexOpcodes.MOVE_WIDE_16, Opcode.MOVE_WIDE, DexInsnFormat.FORMAT_32X)

			register(INSN_INFO, DexOpcodes.MOVE_OBJECT, Opcode.MOVE_OBJECT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT, DexInsnFormat.FORMAT_22X)
			register(INSN_INFO, DexOpcodes.MOVE_OBJECT_16, Opcode.MOVE_OBJECT, DexInsnFormat.FORMAT_32X)

			register(INSN_INFO, DexOpcodes.MOVE_RESULT, Opcode.MOVE_RESULT, DexInsnFormat.FORMAT_11X)
			register(INSN_INFO, DexOpcodes.MOVE_RESULT_WIDE, Opcode.MOVE_RESULT, DexInsnFormat.FORMAT_11X)
			register(INSN_INFO, DexOpcodes.MOVE_RESULT_OBJECT, Opcode.MOVE_RESULT, DexInsnFormat.FORMAT_11X)

			register(INSN_INFO, DexOpcodes.MOVE_EXCEPTION, Opcode.MOVE_EXCEPTION, DexInsnFormat.FORMAT_11X)

			register(INSN_INFO, DexOpcodes.RETURN_VOID, Opcode.RETURN_VOID, DexInsnFormat.FORMAT_10X)
			register(INSN_INFO, DexOpcodes.RETURN, Opcode.RETURN, DexInsnFormat.FORMAT_11X)
			register(INSN_INFO, DexOpcodes.RETURN_WIDE, Opcode.RETURN, DexInsnFormat.FORMAT_11X)
			register(INSN_INFO, DexOpcodes.RETURN_OBJECT, Opcode.RETURN, DexInsnFormat.FORMAT_11X)

			register(INSN_INFO, DexOpcodes.CONST_4, Opcode.CONST, DexInsnFormat.FORMAT_11N)
			register(INSN_INFO, DexOpcodes.CONST_16, Opcode.CONST, DexInsnFormat.FORMAT_21S)
			register(INSN_INFO, DexOpcodes.CONST, Opcode.CONST, DexInsnFormat.FORMAT_31I)
			register(INSN_INFO, DexOpcodes.CONST_HIGH16, Opcode.CONST, DexInsnFormat.FORMAT_21H)

			register(INSN_INFO, DexOpcodes.CONST_WIDE_16, Opcode.CONST_WIDE, DexInsnFormat.FORMAT_21S)
			register(INSN_INFO, DexOpcodes.CONST_WIDE_32, Opcode.CONST_WIDE, DexInsnFormat.FORMAT_31I)
			register(INSN_INFO, DexOpcodes.CONST_WIDE, Opcode.CONST_WIDE, DexInsnFormat.FORMAT_51I)
			register(INSN_INFO, DexOpcodes.CONST_WIDE_HIGH16, Opcode.CONST_WIDE, DexInsnFormat.FORMAT_21H)

			register(INSN_INFO, DexOpcodes.CONST_STRING, Opcode.CONST_STRING, DexInsnFormat.FORMAT_21C, InsnIndexType.STRING_REF)
			register(INSN_INFO, DexOpcodes.CONST_STRING_JUMBO, Opcode.CONST_STRING, DexInsnFormat.FORMAT_31C, InsnIndexType.STRING_REF)

			register(INSN_INFO, DexOpcodes.CONST_CLASS, Opcode.CONST_CLASS, DexInsnFormat.FORMAT_21C, InsnIndexType.TYPE_REF)

			register(INSN_INFO, DexOpcodes.MONITOR_ENTER, Opcode.MONITOR_ENTER, DexInsnFormat.FORMAT_11X)
			register(INSN_INFO, DexOpcodes.MONITOR_EXIT, Opcode.MONITOR_EXIT, DexInsnFormat.FORMAT_11X)

			register(INSN_INFO, DexOpcodes.CHECK_CAST, Opcode.CHECK_CAST, DexInsnFormat.FORMAT_21C, InsnIndexType.TYPE_REF)
			register(INSN_INFO, DexOpcodes.INSTANCE_OF, Opcode.INSTANCE_OF, DexInsnFormat.FORMAT_22C, InsnIndexType.TYPE_REF)
			register(INSN_INFO, DexOpcodes.ARRAY_LENGTH, Opcode.ARRAY_LENGTH, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.NEW_INSTANCE, Opcode.NEW_INSTANCE, DexInsnFormat.FORMAT_21C, InsnIndexType.TYPE_REF)
			register(INSN_INFO, DexOpcodes.NEW_ARRAY, Opcode.NEW_ARRAY, DexInsnFormat.FORMAT_22C, InsnIndexType.TYPE_REF)

			register(INSN_INFO, DexOpcodes.FILLED_NEW_ARRAY, Opcode.FILLED_NEW_ARRAY, DexInsnFormat.FORMAT_35C, InsnIndexType.TYPE_REF)
			register(INSN_INFO, DexOpcodes.FILLED_NEW_ARRAY_RANGE, Opcode.FILLED_NEW_ARRAY_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.TYPE_REF)
			register(INSN_INFO, DexOpcodes.FILL_ARRAY_DATA, Opcode.FILL_ARRAY_DATA, DexInsnFormat.FORMAT_31T)

			register(INSN_INFO, DexOpcodes.THROW, Opcode.THROW, DexInsnFormat.FORMAT_11X)

			register(INSN_INFO, DexOpcodes.GOTO, Opcode.GOTO, DexInsnFormat.FORMAT_10T)
			register(INSN_INFO, DexOpcodes.GOTO_16, Opcode.GOTO, DexInsnFormat.FORMAT_20T)
			register(INSN_INFO, DexOpcodes.GOTO_32, Opcode.GOTO, DexInsnFormat.FORMAT_30T)

			register(INSN_INFO, DexOpcodes.PACKED_SWITCH, Opcode.PACKED_SWITCH, DexInsnFormat.FORMAT_31T)
			register(INSN_INFO, DexOpcodes.SPARSE_SWITCH, Opcode.SPARSE_SWITCH, DexInsnFormat.FORMAT_31T)

			register(INSN_INFO, DexOpcodes.CMPL_FLOAT, Opcode.CMPL_FLOAT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.CMPG_FLOAT, Opcode.CMPG_FLOAT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.CMPL_DOUBLE, Opcode.CMPL_DOUBLE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.CMPG_DOUBLE, Opcode.CMPG_DOUBLE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.CMP_LONG, Opcode.CMP_LONG, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.IF_EQ, Opcode.IF_EQ, DexInsnFormat.FORMAT_22T)
			register(INSN_INFO, DexOpcodes.IF_NE, Opcode.IF_NE, DexInsnFormat.FORMAT_22T)
			register(INSN_INFO, DexOpcodes.IF_LT, Opcode.IF_LT, DexInsnFormat.FORMAT_22T)
			register(INSN_INFO, DexOpcodes.IF_GE, Opcode.IF_GE, DexInsnFormat.FORMAT_22T)
			register(INSN_INFO, DexOpcodes.IF_GT, Opcode.IF_GT, DexInsnFormat.FORMAT_22T)
			register(INSN_INFO, DexOpcodes.IF_LE, Opcode.IF_LE, DexInsnFormat.FORMAT_22T)

			register(INSN_INFO, DexOpcodes.IF_EQZ, Opcode.IF_EQZ, DexInsnFormat.FORMAT_21T)
			register(INSN_INFO, DexOpcodes.IF_NEZ, Opcode.IF_NEZ, DexInsnFormat.FORMAT_21T)
			register(INSN_INFO, DexOpcodes.IF_LTZ, Opcode.IF_LTZ, DexInsnFormat.FORMAT_21T)
			register(INSN_INFO, DexOpcodes.IF_GEZ, Opcode.IF_GEZ, DexInsnFormat.FORMAT_21T)
			register(INSN_INFO, DexOpcodes.IF_GTZ, Opcode.IF_GTZ, DexInsnFormat.FORMAT_21T)
			register(INSN_INFO, DexOpcodes.IF_LEZ, Opcode.IF_LEZ, DexInsnFormat.FORMAT_21T)

			register(INSN_INFO, DexOpcodes.AGET, Opcode.AGET, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AGET_WIDE, Opcode.AGET_WIDE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AGET_OBJECT, Opcode.AGET_OBJECT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AGET_BOOLEAN, Opcode.AGET_BOOLEAN, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AGET_BYTE, Opcode.AGET_BYTE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AGET_CHAR, Opcode.AGET_CHAR, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AGET_SHORT, Opcode.AGET_SHORT, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.APUT, Opcode.APUT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.APUT_WIDE, Opcode.APUT_WIDE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.APUT_OBJECT, Opcode.APUT_OBJECT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.APUT_BOOLEAN, Opcode.APUT_BOOLEAN, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.APUT_BYTE, Opcode.APUT_BYTE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.APUT_CHAR, Opcode.APUT_CHAR, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.APUT_SHORT, Opcode.APUT_SHORT, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.IGET, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IGET_WIDE, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IGET_OBJECT, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IGET_BOOLEAN, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IGET_BYTE, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IGET_CHAR, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IGET_SHORT, Opcode.IGET, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)

			register(INSN_INFO, DexOpcodes.IPUT, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IPUT_WIDE, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IPUT_OBJECT, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IPUT_BOOLEAN, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IPUT_BYTE, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IPUT_CHAR, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.IPUT_SHORT, Opcode.IPUT, DexInsnFormat.FORMAT_22C, InsnIndexType.FIELD_REF)

			register(INSN_INFO, DexOpcodes.SGET, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SGET_WIDE, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SGET_OBJECT, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SGET_BOOLEAN, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SGET_BYTE, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SGET_CHAR, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SGET_SHORT, Opcode.SGET, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)

			register(INSN_INFO, DexOpcodes.SPUT, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SPUT_WIDE, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SPUT_OBJECT, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SPUT_BOOLEAN, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SPUT_BYTE, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SPUT_CHAR, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)
			register(INSN_INFO, DexOpcodes.SPUT_SHORT, Opcode.SPUT, DexInsnFormat.FORMAT_21C, InsnIndexType.FIELD_REF)

			register(INSN_INFO, DexOpcodes.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL, DexInsnFormat.FORMAT_35C, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_SUPER, Opcode.INVOKE_SUPER, DexInsnFormat.FORMAT_35C, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_DIRECT, Opcode.INVOKE_DIRECT, DexInsnFormat.FORMAT_35C, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_STATIC, Opcode.INVOKE_STATIC, DexInsnFormat.FORMAT_35C, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE, DexInsnFormat.FORMAT_35C, InsnIndexType.METHOD_REF)

			register(INSN_INFO, DexOpcodes.INVOKE_VIRTUAL_RANGE, Opcode.INVOKE_VIRTUAL_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_SUPER_RANGE, Opcode.INVOKE_SUPER_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_DIRECT_RANGE, Opcode.INVOKE_DIRECT_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_STATIC_RANGE, Opcode.INVOKE_STATIC_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.METHOD_REF)
			register(INSN_INFO, DexOpcodes.INVOKE_INTERFACE_RANGE, Opcode.INVOKE_INTERFACE_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.METHOD_REF)

			register(INSN_INFO, DexOpcodes.NEG_INT, Opcode.NEG_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.NOT_INT, Opcode.NOT_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.NEG_LONG, Opcode.NEG_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.NOT_LONG, Opcode.NOT_LONG, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.NEG_FLOAT, Opcode.NEG_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.NEG_DOUBLE, Opcode.NEG_DOUBLE, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.INT_TO_LONG, Opcode.INT_TO_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.INT_TO_FLOAT, Opcode.INT_TO_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.INT_TO_DOUBLE, Opcode.INT_TO_DOUBLE, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.LONG_TO_INT, Opcode.LONG_TO_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.LONG_TO_FLOAT, Opcode.LONG_TO_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.LONG_TO_DOUBLE, Opcode.LONG_TO_DOUBLE, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.FLOAT_TO_INT, Opcode.FLOAT_TO_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.FLOAT_TO_LONG, Opcode.FLOAT_TO_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.FLOAT_TO_DOUBLE, Opcode.FLOAT_TO_DOUBLE, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.DOUBLE_TO_INT, Opcode.DOUBLE_TO_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.DOUBLE_TO_LONG, Opcode.DOUBLE_TO_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.DOUBLE_TO_FLOAT, Opcode.DOUBLE_TO_FLOAT, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.INT_TO_BYTE, Opcode.INT_TO_BYTE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.INT_TO_CHAR, Opcode.INT_TO_CHAR, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.INT_TO_SHORT, Opcode.INT_TO_SHORT, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.ADD_INT, Opcode.ADD_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SUB_INT, Opcode.SUB_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.MUL_INT, Opcode.MUL_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.DIV_INT, Opcode.DIV_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.REM_INT, Opcode.REM_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AND_INT, Opcode.AND_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.OR_INT, Opcode.OR_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.XOR_INT, Opcode.XOR_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SHL_INT, Opcode.SHL_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SHR_INT, Opcode.SHR_INT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.USHR_INT, Opcode.USHR_INT, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.ADD_LONG, Opcode.ADD_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SUB_LONG, Opcode.SUB_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.MUL_LONG, Opcode.MUL_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.DIV_LONG, Opcode.DIV_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.REM_LONG, Opcode.REM_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.AND_LONG, Opcode.AND_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.OR_LONG, Opcode.OR_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.XOR_LONG, Opcode.XOR_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SHL_LONG, Opcode.SHL_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SHR_LONG, Opcode.SHR_LONG, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.USHR_LONG, Opcode.USHR_LONG, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.ADD_FLOAT, Opcode.ADD_FLOAT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SUB_FLOAT, Opcode.SUB_FLOAT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.MUL_FLOAT, Opcode.MUL_FLOAT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.DIV_FLOAT, Opcode.DIV_FLOAT, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.REM_FLOAT, Opcode.REM_FLOAT, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.ADD_DOUBLE, Opcode.ADD_DOUBLE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.SUB_DOUBLE, Opcode.SUB_DOUBLE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.MUL_DOUBLE, Opcode.MUL_DOUBLE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.DIV_DOUBLE, Opcode.DIV_DOUBLE, DexInsnFormat.FORMAT_23X)
			register(INSN_INFO, DexOpcodes.REM_DOUBLE, Opcode.REM_DOUBLE, DexInsnFormat.FORMAT_23X)

			register(INSN_INFO, DexOpcodes.ADD_INT_2ADDR, Opcode.ADD_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SUB_INT_2ADDR, Opcode.SUB_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MUL_INT_2ADDR, Opcode.MUL_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.DIV_INT_2ADDR, Opcode.DIV_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.REM_INT_2ADDR, Opcode.REM_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.AND_INT_2ADDR, Opcode.AND_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.OR_INT_2ADDR, Opcode.OR_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.XOR_INT_2ADDR, Opcode.XOR_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SHL_INT_2ADDR, Opcode.SHL_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SHR_INT_2ADDR, Opcode.SHR_INT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.USHR_INT_2ADDR, Opcode.USHR_INT, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.ADD_LONG_2ADDR, Opcode.ADD_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SUB_LONG_2ADDR, Opcode.SUB_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MUL_LONG_2ADDR, Opcode.MUL_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.DIV_LONG_2ADDR, Opcode.DIV_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.REM_LONG_2ADDR, Opcode.REM_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.AND_LONG_2ADDR, Opcode.AND_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.OR_LONG_2ADDR, Opcode.OR_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.XOR_LONG_2ADDR, Opcode.XOR_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SHL_LONG_2ADDR, Opcode.SHL_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SHR_LONG_2ADDR, Opcode.SHR_LONG, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.USHR_LONG_2ADDR, Opcode.USHR_LONG, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.ADD_FLOAT_2ADDR, Opcode.ADD_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SUB_FLOAT_2ADDR, Opcode.SUB_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MUL_FLOAT_2ADDR, Opcode.MUL_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.DIV_FLOAT_2ADDR, Opcode.DIV_FLOAT, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.REM_FLOAT_2ADDR, Opcode.REM_FLOAT, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.ADD_DOUBLE_2ADDR, Opcode.ADD_DOUBLE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.SUB_DOUBLE_2ADDR, Opcode.SUB_DOUBLE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.MUL_DOUBLE_2ADDR, Opcode.MUL_DOUBLE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.DIV_DOUBLE_2ADDR, Opcode.DIV_DOUBLE, DexInsnFormat.FORMAT_12X)
			register(INSN_INFO, DexOpcodes.REM_DOUBLE_2ADDR, Opcode.REM_DOUBLE, DexInsnFormat.FORMAT_12X)

			register(INSN_INFO, DexOpcodes.ADD_INT_LIT16, Opcode.ADD_INT_LIT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.RSUB_INT, Opcode.RSUB_INT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.MUL_INT_LIT16, Opcode.MUL_INT_LIT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.DIV_INT_LIT16, Opcode.DIV_INT_LIT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.REM_INT_LIT16, Opcode.REM_INT_LIT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.AND_INT_LIT16, Opcode.AND_INT_LIT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.OR_INT_LIT16, Opcode.OR_INT_LIT, DexInsnFormat.FORMAT_22S)
			register(INSN_INFO, DexOpcodes.XOR_INT_LIT16, Opcode.XOR_INT_LIT, DexInsnFormat.FORMAT_22S)

			register(INSN_INFO, DexOpcodes.ADD_INT_LIT8, Opcode.ADD_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.RSUB_INT_LIT8, Opcode.RSUB_INT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.MUL_INT_LIT8, Opcode.MUL_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.DIV_INT_LIT8, Opcode.DIV_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.REM_INT_LIT8, Opcode.REM_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.AND_INT_LIT8, Opcode.AND_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.OR_INT_LIT8, Opcode.OR_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.XOR_INT_LIT8, Opcode.XOR_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.SHL_INT_LIT8, Opcode.SHL_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.SHR_INT_LIT8, Opcode.SHR_INT_LIT, DexInsnFormat.FORMAT_22B)
			register(INSN_INFO, DexOpcodes.USHR_INT_LIT8, Opcode.USHR_INT_LIT, DexInsnFormat.FORMAT_22B)

			register(INSN_INFO, DexOpcodes.INVOKE_POLYMORPHIC, Opcode.INVOKE_POLYMORPHIC, DexInsnFormat.FORMAT_45CC, InsnIndexType.METHOD_REF)
			register(
				INSN_INFO,
				DexOpcodes.INVOKE_POLYMORPHIC_RANGE,
				Opcode.INVOKE_POLYMORPHIC_RANGE,
				DexInsnFormat.FORMAT_4RCC,
				InsnIndexType.METHOD_REF,
			)

			register(INSN_INFO, DexOpcodes.INVOKE_CUSTOM, Opcode.INVOKE_CUSTOM, DexInsnFormat.FORMAT_35C, InsnIndexType.CALL_SITE)
			register(INSN_INFO, DexOpcodes.INVOKE_CUSTOM_RANGE, Opcode.INVOKE_CUSTOM_RANGE, DexInsnFormat.FORMAT_3RC, InsnIndexType.CALL_SITE)

			register(INSN_INFO, DexOpcodes.CONST_METHOD_HANDLE, Opcode.CONST_METHOD_HANDLE, DexInsnFormat.FORMAT_21C)
			register(INSN_INFO, DexOpcodes.CONST_METHOD_TYPE, Opcode.CONST_METHOD_TYPE, DexInsnFormat.FORMAT_21C)

			registerPayload(DexOpcodes.PACKED_SWITCH_PAYLOAD, Opcode.PACKED_SWITCH_PAYLOAD, DexInsnFormat.FORMAT_PACKED_SWITCH_PAYLOAD)
			registerPayload(DexOpcodes.SPARSE_SWITCH_PAYLOAD, Opcode.SPARSE_SWITCH_PAYLOAD, DexInsnFormat.FORMAT_SPARSE_SWITCH_PAYLOAD)
			registerPayload(DexOpcodes.FILL_ARRAY_DATA_PAYLOAD, Opcode.FILL_ARRAY_DATA_PAYLOAD, DexInsnFormat.FORMAT_FILL_ARRAY_DATA_PAYLOAD)
		}

		private fun register(arr: Array<DexInsnInfo?>, opcode: Int, apiOpcode: Opcode, format: DexInsnFormat) {
			arr[opcode] = DexInsnInfo(opcode, apiOpcode, format, InsnIndexType.NONE)
		}

		private fun register(
			arr: Array<DexInsnInfo?>,
			opcode: Int,
			apiOpcode: Opcode,
			format: DexInsnFormat,
			indexType: InsnIndexType,
		) {
			arr[opcode] = DexInsnInfo(opcode, apiOpcode, format, indexType)
		}

		private fun registerPayload(opcode: Int, apiOpcode: Opcode, format: DexInsnFormat) {
			PAYLOAD_INFO[opcode] = DexInsnInfo(opcode, apiOpcode, format, InsnIndexType.NONE)
		}

		/**
		 * 按原始 opcodeUnit 查表。
		 * @return 未注册的操作码返回 null（低字节为 0 的高位变体查 [PAYLOAD_INFO]）
		 */
		@Nullable
		public fun get(opcodeUnit: Int): DexInsnInfo? {
			val opcode = opcodeUnit and 0xFF
			if (opcode == 0 && opcodeUnit != 0) {
				return PAYLOAD_INFO[opcodeUnit]
			}
			return INSN_INFO[opcode]
		}
	}

	override fun toString(): String = String.format("0x%X :%d%d", opcode, format.length, format.regsCount)
}
