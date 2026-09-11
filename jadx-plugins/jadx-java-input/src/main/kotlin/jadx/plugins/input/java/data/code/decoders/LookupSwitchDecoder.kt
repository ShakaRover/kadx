package jadx.plugins.input.java.data.code.decoders

import jadx.api.plugins.input.insns.custom.impl.SwitchPayload
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.code.CodeDecodeState
import jadx.plugins.input.java.data.code.JavaInsnData

/**
 * lookupswitch 指令解码器（稀疏 switch）。
 *
 **做什么**：读"默认目标 + N 个(键, 目标)对"，所有目标都注册为跳转点；
 * [skip] 模式只按 pairs*8 字节跳过数据（用于已解码场景）。
 */
class LookupSwitchDecoder : IJavaInsnDecoder {

	override fun decode(state: CodeDecodeState) {
		read(state, false)
	}

	override fun skip(state: CodeDecodeState) {
		read(state, true)
	}

	companion object {
		private fun read(state: CodeDecodeState, skip: Boolean) {
			val reader = state.reader()
			val insn = state.insn()
			val dataOffset = reader.offset
			val insnOffset = insn.getOffset()
			reader.skip(3 - insnOffset % 4) // 对齐到 4 字节边界
			val defTarget = insnOffset + reader.readS4()
			val pairs = reader.readS4()
			if (skip) {
				reader.skip(pairs * 8)
			} else {
				state.pop(0)
				val keys = IntArray(pairs)
				val targets = IntArray(pairs)
				for (i in 0 until pairs) {
					keys[i] = reader.readS4()
					val target = insnOffset + reader.readS4()
					targets[i] = target
					state.registerJump(target)
				}
				insn.setTarget(defTarget)
				state.registerJump(defTarget)
				insn.setPayload(SwitchPayload(pairs, keys, targets))
			}
			insn.setPayloadSize(reader.offset - dataOffset)
		}
	}
}
