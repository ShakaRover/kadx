package jadx.plugins.input.java.data.code

import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import jadx.plugins.input.java.data.code.decoders.IJavaInsnDecoder

/**
 * 单条字节码指令的静态元信息（由 [JavaInsnsRegister] 按 opcode 注册）。
 *
 **做什么**：保存助记符名、payload 大小、寄存器数、对外 API opcode、索引类型与解码器；
 * 解码主循环靠它驱动"读操作数 → 分配寄存器"的过程。
 */
class JavaInsnInfo(
	/** JVM 原始 opcode（0x00..0xC9） */
	val opcode: Int,
	/** 助记符名（如 "invokevirtual"），用于调试输出 */
	val name: String,
	/** 操作数字节数；-1 表示变长（switch/wide 等由解码器自行处理） */
	val payloadSize: Int,
	/** 涉及的寄存器总数；-1 表示变长 */
	val regsCount: Int,
	/** 映射到对外 API 的 [Opcode] */
	val apiOpcode: Opcode,
	/** 操作数索引的含义（字段引用/方法引用/类型等） */
	val indexType: InsnIndexType,
	/** 自定义解码器；null 表示无额外操作数处理 */
	val decoder: IJavaInsnDecoder?,
) {

	override fun toString(): String = "0x" + Integer.toHexString(opcode) + ": " + name
}
