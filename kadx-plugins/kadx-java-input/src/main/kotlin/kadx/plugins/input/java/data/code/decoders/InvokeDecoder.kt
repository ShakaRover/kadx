package kadx.plugins.input.java.data.code.decoders

import kadx.api.plugins.input.data.IMethodProto
import kadx.api.plugins.input.insns.Opcode
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.code.CodeDecodeState
import kadx.plugins.input.java.data.code.JavaInsnData

/**
 * invoke* 指令解码器（virtual/special/static/interface/custom）。
 *
 **做什么**：读方法索引，加载方法原型后按"实例调用多一个 this + long/double 占双寄存器"
 * 的规则精确计算所需寄存器数并逐个 pop 分配；非 void 返回再 push 一个结果寄存器。
 */
class InvokeDecoder(
	private val payloadSize: Int,
	private val apiOpcode: Opcode,
) : IJavaInsnDecoder {

	override fun decode(state: CodeDecodeState) {
		val reader = state.reader()
		val mthIdx = reader.readS2()
		if (payloadSize == 4) {
			reader.skip(2) // invokeinterface/invokedynamic 的接口方法索引+0
		}
		val insn = state.insn()
		insn.setIndex(mthIdx)
		val instanceCall: Boolean
		val mthProto: IMethodProto
		if (apiOpcode == Opcode.INVOKE_CUSTOM) {
			val callSite = checkNotNull(insn.indexAsCallSite)
			insn.setPayload(callSite)
			mthProto = callSite.values[2].value as IMethodProto
			instanceCall = false // 'this' arg already included in proto args
		} else {
			val mthRef = checkNotNull(insn.indexAsMethod)
			mthRef.load()
			insn.setPayload(mthRef)
			mthProto = mthRef
			instanceCall = apiOpcode != Opcode.INVOKE_STATIC
		}

		var argsCount = mthProto.argTypes.size
		if (instanceCall) {
			argsCount++
		}
		insn.setRegsCount(argsCount * 2) // allocate twice of the size for worst case
		val regs = insn.regsArray

		// calculate actual count of registers
		// set '1' in regs to be filled with stack values later, '0' for skip
		var regsCount = 0
		if (instanceCall) {
			regs[regsCount++] = 1
		}
		for (type in mthProto.argTypes) {
			val size = getRegsCountForType(type)
			regs[regsCount++] = 1
			if (size == 2) {
				regs[regsCount++] = 0
			}
		}
		insn.setRegsCount(regsCount)
		for (i in regsCount - 1 downTo 0) {
			if (regs[i] == 1) {
				state.pop(i)
			}
		}
		val returnType = mthProto.returnType
		if (!returnType.equals("V")) {
			insn.setResultReg(state.push(returnType))
		} else {
			insn.setResultReg(-1)
		}
	}

	private fun getRegsCountForType(type: String): Int {
		val c = type[0]
		if (c == 'J' || c == 'D') {
			return 2
		}
		return 1
	}
}
