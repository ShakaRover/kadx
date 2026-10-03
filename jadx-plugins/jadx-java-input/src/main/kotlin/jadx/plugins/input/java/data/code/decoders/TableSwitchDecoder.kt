package jadx.plugins.input.java.data.code.decoders

import jadx.api.plugins.input.insns.custom.impl.SwitchPayload
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.code.CodeDecodeState
import jadx.plugins.input.java.data.code.JavaInsnData

/**
 * tableswitch 指令解码器（稠密 switch）。
 *
 **做什么**：读"默认目标 + low/high + (high-low+1) 个目标"，键值即 low..high 连续区间；
 * [skip] 模式只按 count*4 字节跳过数据。
 */
class TableSwitchDecoder : IJavaInsnDecoder {

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
			val insnOffset = insn.offset
			reader.skip(3 - insnOffset % 4) // 对齐到 4 字节边界
			val defTarget = insnOffset + reader.readS4()
			val low = reader.readS4()
			val high = reader.readS4()
			val count = high - low + 1
			if (skip) {
				reader.skip(count * 4)
			} else {
				state.pop(0)
				val keys = IntArray(count)
				val targets = IntArray(count)
				for (i in 0 until count) {
					val target = insnOffset + reader.readS4()
					keys[i] = low + i
					targets[i] = target
					state.registerJump(target)
				}
				insn.setTarget(defTarget)
				state.registerJump(defTarget)
				insn.setPayload(SwitchPayload(count, keys, targets))
			}
			insn.setPayloadSize(reader.offset - dataOffset)
		}
	}
}
