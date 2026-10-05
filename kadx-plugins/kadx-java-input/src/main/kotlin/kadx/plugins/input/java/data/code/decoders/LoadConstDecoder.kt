package kadx.plugins.input.java.data.code.decoders

import kadx.api.plugins.input.insns.Opcode
import kadx.plugins.input.java.data.ConstPoolReader
import kadx.plugins.input.java.data.ConstantType
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.attributes.stack.StackValueType
import kadx.plugins.input.java.data.code.CodeDecodeState
import kadx.plugins.input.java.data.code.JavaInsnData
import kadx.plugins.input.java.utils.JavaClassParseException

/**
 * ldc / ldc_w / ldc2_w 指令解码器。
 *
 **做什么**：按常量池条目类型把字面量读入指令——int/float 读 u4，long/double 读 u8（宽值），
 * string/class 只记索引由后续阶段解析；同时把 API opcode 细化为 CONST_STRING/CONST_CLASS。
 */
class LoadConstDecoder(private val wide: Boolean) : IJavaInsnDecoder {

	override fun decode(state: CodeDecodeState) {
		val reader = state.reader()
		val insn = state.insn()
		val index = if (wide) reader.readU2() else reader.readU1()
		val constPoolReader: ConstPoolReader = insn.constPoolReader()
		val constType = constPoolReader.jumpToConst(index)
		when (constType) {
			ConstantType.INTEGER, ConstantType.FLOAT -> {
				insn.setLiteral(constPoolReader.readU4().toLong())
				insn.setOpcode(Opcode.CONST)
				state.push(0, StackValueType.NARROW)
			}

			ConstantType.LONG, ConstantType.DOUBLE -> {
				insn.setLiteral(constPoolReader.readU8())
				insn.setOpcode(Opcode.CONST_WIDE)
				state.push(0, StackValueType.WIDE)
			}

			ConstantType.STRING -> {
				insn.setIndex(constPoolReader.readU2())
				insn.setOpcode(Opcode.CONST_STRING)
				state.push(0, StackValueType.NARROW)
			}

			ConstantType.UTF8 -> {
				insn.setIndex(index)
				insn.setOpcode(Opcode.CONST_STRING)
				state.push(0, StackValueType.NARROW)
			}

			ConstantType.CLASS -> {
				insn.setIndex(index)
				insn.setOpcode(Opcode.CONST_CLASS)
				state.push(0, StackValueType.NARROW)
			}

			else -> throw JavaClassParseException("Unsupported constant type: " + constType)
		}
	}
}
