package jadx.plugins.input.dex.insns

/**
 * DEX 操作码常量表（对应 DEX 规范 instruction opcode 编码）。
 *
 * **背景**：每条 Dex 指令的第一个字节即操作码，[DexInsnInfo] 依据这些值建立
 * 「原始操作码 → 指令信息」的映射；0x100/0x200/0x300 三个伪操作码表示
 * packed-switch / sparse-switch / fill-array-data 三种 payload 数据块。
 *
 * **Kotlin 转换说明**：原 Java `public static final int` → companion object 内
 * `const val`（编译为静态常量字段，Java 调用方 `DexOpcodes.XXX` 零改动）。
 */
public class DexOpcodes {

	public companion object {
		/** 基本 move/return 指令（0x00-0x11） */
		public const val NOP: Int = 0x00
		public const val MOVE: Int = 0x01
		public const val MOVE_FROM16: Int = 0x02
		public const val MOVE_16: Int = 0x03
		public const val MOVE_WIDE: Int = 0x04
		public const val MOVE_WIDE_FROM16: Int = 0x05
		public const val MOVE_WIDE_16: Int = 0x06
		public const val MOVE_OBJECT: Int = 0x07
		public const val MOVE_OBJECT_FROM16: Int = 0x08
		public const val MOVE_OBJECT_16: Int = 0x09
		public const val MOVE_RESULT: Int = 0x0a
		public const val MOVE_RESULT_WIDE: Int = 0x0b
		public const val MOVE_RESULT_OBJECT: Int = 0x0c
		public const val MOVE_EXCEPTION: Int = 0x0d
		public const val RETURN_VOID: Int = 0x0e
		public const val RETURN: Int = 0x0f
		public const val RETURN_WIDE: Int = 0x10
		public const val RETURN_OBJECT: Int = 0x11

		/** const 常量加载指令（0x12-0x1c） */
		public const val CONST_4: Int = 0x12
		public const val CONST_16: Int = 0x13
		public const val CONST: Int = 0x14
		public const val CONST_HIGH16: Int = 0x15
		public const val CONST_WIDE_16: Int = 0x16
		public const val CONST_WIDE_32: Int = 0x17
		public const val CONST_WIDE: Int = 0x18
		public const val CONST_WIDE_HIGH16: Int = 0x19
		public const val CONST_STRING: Int = 0x1a
		public const val CONST_STRING_JUMBO: Int = 0x1b
		public const val CONST_CLASS: Int = 0x1c

		/** 对象与数组操作（0x1d-0x27） */
		public const val MONITOR_ENTER: Int = 0x1d
		public const val MONITOR_EXIT: Int = 0x1e
		public const val CHECK_CAST: Int = 0x1f
		public const val INSTANCE_OF: Int = 0x20
		public const val ARRAY_LENGTH: Int = 0x21
		public const val NEW_INSTANCE: Int = 0x22
		public const val NEW_ARRAY: Int = 0x23
		public const val FILLED_NEW_ARRAY: Int = 0x24
		public const val FILLED_NEW_ARRAY_RANGE: Int = 0x25
		public const val FILL_ARRAY_DATA: Int = 0x26
		public const val THROW: Int = 0x27

		/** 跳转与 switch（0x28-0x2c） */
		public const val GOTO: Int = 0x28
		public const val GOTO_16: Int = 0x29
		public const val GOTO_32: Int = 0x2a
		public const val PACKED_SWITCH: Int = 0x2b
		public const val SPARSE_SWITCH: Int = 0x2c

		/** 比较指令（0x2d-0x31） */
		public const val CMPL_FLOAT: Int = 0x2d
		public const val CMPG_FLOAT: Int = 0x2e
		public const val CMPL_DOUBLE: Int = 0x2f
		public const val CMPG_DOUBLE: Int = 0x30
		public const val CMP_LONG: Int = 0x31

		/** 条件/无条件分支 if-*（0x32-0x3d） */
		public const val IF_EQ: Int = 0x32
		public const val IF_NE: Int = 0x33
		public const val IF_LT: Int = 0x34
		public const val IF_GE: Int = 0x35
		public const val IF_GT: Int = 0x36
		public const val IF_LE: Int = 0x37
		public const val IF_EQZ: Int = 0x38
		public const val IF_NEZ: Int = 0x39
		public const val IF_LTZ: Int = 0x3a
		public const val IF_GEZ: Int = 0x3b
		public const val IF_GTZ: Int = 0x3c
		public const val IF_LEZ: Int = 0x3d

		/** 数组读写 aget/aput（0x44-0x51） */
		public const val AGET: Int = 0x44
		public const val AGET_WIDE: Int = 0x45
		public const val AGET_OBJECT: Int = 0x46
		public const val AGET_BOOLEAN: Int = 0x47
		public const val AGET_BYTE: Int = 0x48
		public const val AGET_CHAR: Int = 0x49
		public const val AGET_SHORT: Int = 0x4a
		public const val APUT: Int = 0x4b
		public const val APUT_WIDE: Int = 0x4c
		public const val APUT_OBJECT: Int = 0x4d
		public const val APUT_BOOLEAN: Int = 0x4e
		public const val APUT_BYTE: Int = 0x4f
		public const val APUT_CHAR: Int = 0x50
		public const val APUT_SHORT: Int = 0x51

		/** 实例字段读写 iget/iput（0x52-0x5f） */
		public const val IGET: Int = 0x52
		public const val IGET_WIDE: Int = 0x53
		public const val IGET_OBJECT: Int = 0x54
		public const val IGET_BOOLEAN: Int = 0x55
		public const val IGET_BYTE: Int = 0x56
		public const val IGET_CHAR: Int = 0x57
		public const val IGET_SHORT: Int = 0x58
		public const val IPUT: Int = 0x59
		public const val IPUT_WIDE: Int = 0x5a
		public const val IPUT_OBJECT: Int = 0x5b
		public const val IPUT_BOOLEAN: Int = 0x5c
		public const val IPUT_BYTE: Int = 0x5d
		public const val IPUT_CHAR: Int = 0x5e
		public const val IPUT_SHORT: Int = 0x5f

		/** 静态字段读写 sget/sput（0x60-0x6d） */
		public const val SGET: Int = 0x60
		public const val SGET_WIDE: Int = 0x61
		public const val SGET_OBJECT: Int = 0x62
		public const val SGET_BOOLEAN: Int = 0x63
		public const val SGET_BYTE: Int = 0x64
		public const val SGET_CHAR: Int = 0x65
		public const val SGET_SHORT: Int = 0x66
		public const val SPUT: Int = 0x67
		public const val SPUT_WIDE: Int = 0x68
		public const val SPUT_OBJECT: Int = 0x69
		public const val SPUT_BOOLEAN: Int = 0x6a
		public const val SPUT_BYTE: Int = 0x6b
		public const val SPUT_CHAR: Int = 0x6c
		public const val SPUT_SHORT: Int = 0x6d

		/** 方法调用 invoke-*（0x6e-0x78） */
		public const val INVOKE_VIRTUAL: Int = 0x6e
		public const val INVOKE_SUPER: Int = 0x6f
		public const val INVOKE_DIRECT: Int = 0x70
		public const val INVOKE_STATIC: Int = 0x71
		public const val INVOKE_INTERFACE: Int = 0x72
		public const val INVOKE_VIRTUAL_RANGE: Int = 0x74
		public const val INVOKE_SUPER_RANGE: Int = 0x75
		public const val INVOKE_DIRECT_RANGE: Int = 0x76
		public const val INVOKE_STATIC_RANGE: Int = 0x77
		public const val INVOKE_INTERFACE_RANGE: Int = 0x78

		/** 一元运算与类型转换（0x7b-0x8f） */
		public const val NEG_INT: Int = 0x7b
		public const val NOT_INT: Int = 0x7c
		public const val NEG_LONG: Int = 0x7d
		public const val NOT_LONG: Int = 0x7e
		public const val NEG_FLOAT: Int = 0x7f
		public const val NEG_DOUBLE: Int = 0x80
		public const val INT_TO_LONG: Int = 0x81
		public const val INT_TO_FLOAT: Int = 0x82
		public const val INT_TO_DOUBLE: Int = 0x83
		public const val LONG_TO_INT: Int = 0x84
		public const val LONG_TO_FLOAT: Int = 0x85
		public const val LONG_TO_DOUBLE: Int = 0x86
		public const val FLOAT_TO_INT: Int = 0x87
		public const val FLOAT_TO_LONG: Int = 0x88
		public const val FLOAT_TO_DOUBLE: Int = 0x89
		public const val DOUBLE_TO_INT: Int = 0x8a
		public const val DOUBLE_TO_LONG: Int = 0x8b
		public const val DOUBLE_TO_FLOAT: Int = 0x8c
		public const val INT_TO_BYTE: Int = 0x8d
		public const val INT_TO_CHAR: Int = 0x8e
		public const val INT_TO_SHORT: Int = 0x8f

		/** 双操作数算术/位运算（0x90-0xaf） */
		public const val ADD_INT: Int = 0x90
		public const val SUB_INT: Int = 0x91
		public const val MUL_INT: Int = 0x92
		public const val DIV_INT: Int = 0x93
		public const val REM_INT: Int = 0x94
		public const val AND_INT: Int = 0x95
		public const val OR_INT: Int = 0x96
		public const val XOR_INT: Int = 0x97
		public const val SHL_INT: Int = 0x98
		public const val SHR_INT: Int = 0x99
		public const val USHR_INT: Int = 0x9a
		public const val ADD_LONG: Int = 0x9b
		public const val SUB_LONG: Int = 0x9c
		public const val MUL_LONG: Int = 0x9d
		public const val DIV_LONG: Int = 0x9e
		public const val REM_LONG: Int = 0x9f
		public const val AND_LONG: Int = 0xa0
		public const val OR_LONG: Int = 0xa1
		public const val XOR_LONG: Int = 0xa2
		public const val SHL_LONG: Int = 0xa3
		public const val SHR_LONG: Int = 0xa4
		public const val USHR_LONG: Int = 0xa5
		public const val ADD_FLOAT: Int = 0xa6
		public const val SUB_FLOAT: Int = 0xa7
		public const val MUL_FLOAT: Int = 0xa8
		public const val DIV_FLOAT: Int = 0xa9
		public const val REM_FLOAT: Int = 0xaa
		public const val ADD_DOUBLE: Int = 0xab
		public const val SUB_DOUBLE: Int = 0xac
		public const val MUL_DOUBLE: Int = 0xad
		public const val DIV_DOUBLE: Int = 0xae
		public const val REM_DOUBLE: Int = 0xaf

		/** 2addr 形式算术/位运算（0xb0-0xcf） */
		public const val ADD_INT_2ADDR: Int = 0xb0
		public const val SUB_INT_2ADDR: Int = 0xb1
		public const val MUL_INT_2ADDR: Int = 0xb2
		public const val DIV_INT_2ADDR: Int = 0xb3
		public const val REM_INT_2ADDR: Int = 0xb4
		public const val AND_INT_2ADDR: Int = 0xb5
		public const val OR_INT_2ADDR: Int = 0xb6
		public const val XOR_INT_2ADDR: Int = 0xb7
		public const val SHL_INT_2ADDR: Int = 0xb8
		public const val SHR_INT_2ADDR: Int = 0xb9
		public const val USHR_INT_2ADDR: Int = 0xba
		public const val ADD_LONG_2ADDR: Int = 0xbb
		public const val SUB_LONG_2ADDR: Int = 0xbc
		public const val MUL_LONG_2ADDR: Int = 0xbd
		public const val DIV_LONG_2ADDR: Int = 0xbe
		public const val REM_LONG_2ADDR: Int = 0xbf
		public const val AND_LONG_2ADDR: Int = 0xc0
		public const val OR_LONG_2ADDR: Int = 0xc1
		public const val XOR_LONG_2ADDR: Int = 0xc2
		public const val SHL_LONG_2ADDR: Int = 0xc3
		public const val SHR_LONG_2ADDR: Int = 0xc4
		public const val USHR_LONG_2ADDR: Int = 0xc5
		public const val ADD_FLOAT_2ADDR: Int = 0xc6
		public const val SUB_FLOAT_2ADDR: Int = 0xc7
		public const val MUL_FLOAT_2ADDR: Int = 0xc8
		public const val DIV_FLOAT_2ADDR: Int = 0xc9
		public const val REM_FLOAT_2ADDR: Int = 0xca
		public const val ADD_DOUBLE_2ADDR: Int = 0xcb
		public const val SUB_DOUBLE_2ADDR: Int = 0xcc
		public const val MUL_DOUBLE_2ADDR: Int = 0xcd
		public const val DIV_DOUBLE_2ADDR: Int = 0xce
		public const val REM_DOUBLE_2ADDR: Int = 0xcf

		/** 立即数形式算术指令 lit16/lit8（0xd0-0xe2） */
		public const val ADD_INT_LIT16: Int = 0xd0
		public const val RSUB_INT: Int = 0xd1
		public const val MUL_INT_LIT16: Int = 0xd2
		public const val DIV_INT_LIT16: Int = 0xd3
		public const val REM_INT_LIT16: Int = 0xd4
		public const val AND_INT_LIT16: Int = 0xd5
		public const val OR_INT_LIT16: Int = 0xd6
		public const val XOR_INT_LIT16: Int = 0xd7
		public const val ADD_INT_LIT8: Int = 0xd8
		public const val RSUB_INT_LIT8: Int = 0xd9
		public const val MUL_INT_LIT8: Int = 0xda
		public const val DIV_INT_LIT8: Int = 0xdb
		public const val REM_INT_LIT8: Int = 0xdc
		public const val AND_INT_LIT8: Int = 0xdd
		public const val OR_INT_LIT8: Int = 0xde
		public const val XOR_INT_LIT8: Int = 0xdf
		public const val SHL_INT_LIT8: Int = 0xe0
		public const val SHR_INT_LIT8: Int = 0xe1
		public const val USHR_INT_LIT8: Int = 0xe2

		/** 方法句柄与多态调用（0xfa-0xff，DEX 099+） */
		public const val INVOKE_POLYMORPHIC: Int = 0xfa
		public const val INVOKE_POLYMORPHIC_RANGE: Int = 0xfb
		public const val INVOKE_CUSTOM: Int = 0xfc
		public const val INVOKE_CUSTOM_RANGE: Int = 0xfd
		public const val CONST_METHOD_HANDLE: Int = 0xfe
		public const val CONST_METHOD_TYPE: Int = 0xff

		/** payload 伪指令（switch/数组数据块，非真实字节码操作码）*/
		public const val PACKED_SWITCH_PAYLOAD: Int = 0x0100
		public const val SPARSE_SWITCH_PAYLOAD: Int = 0x0200
		public const val FILL_ARRAY_DATA_PAYLOAD: Int = 0x0300
	}
}
