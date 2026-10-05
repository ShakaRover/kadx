package kadx.plugins.input.java.data.code.decoders

import kadx.api.plugins.input.insns.Opcode
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.code.CodeDecodeState
import kadx.plugins.input.java.data.code.JavaInsnData
import kadx.plugins.input.java.utils.JavaClassParseException

/**
 * wide 前缀指令解码器（16 位寄存器号的扩展形式）。
 *
 **做什么**：wide 后跟一个原始 opcode——iinc 直接就地展开为 ADD_INT_LIT；
 * load/store 类按宽索引重新走 local/pop/push 分配。[skip] 只推进读头并记录 payload 大小。
 */
class WideDecoder : IJavaInsnDecoder {

	override fun decode(state: CodeDecodeState) {
		val reader = state.reader()
		val insn = state.insn()
		val opcode = reader.readU1()
		if (opcode == IINC) {
			val varNum = reader.readU2()
			val constValue = reader.readS2()
			state.local(0, varNum).local(1, varNum).lit(constValue.toLong())
			insn.setPayloadSize(5)
			insn.setRegsCount(2)
			insn.setOpcode(Opcode.ADD_INT_LIT)
			return
		}
		val index = reader.readU2()
		when (opcode) {
			0x15, // iload
			0x17, // fload
			0x19,
			-> // aload
				state.local(1, index).push(0)

			0x16, // lload
			0x18,
			-> // dload
				state.local(1, index).pushWide(0)

			0x36, 0x37, 0x38, 0x39, 0x3a ->
				// *store
				state.pop(1).local(0, index)

			else -> throw JavaClassParseException("Unexpected opcode in 'wide': 0x" + Integer.toHexString(opcode))
		}
		insn.setPayloadSize(3)
		insn.setRegsCount(2)
		insn.setOpcode(Opcode.MOVE)
	}

	override fun skip(state: CodeDecodeState) {
		val reader = state.reader()
		val insn = state.insn()
		val opcode = reader.readU1()
		if (opcode == IINC) {
			reader.skip(4)
			insn.setPayloadSize(5)
		} else {
			reader.skip(2)
			insn.setPayloadSize(3)
		}
	}

	companion object {
		private const val IINC = 0x84
	}
}
