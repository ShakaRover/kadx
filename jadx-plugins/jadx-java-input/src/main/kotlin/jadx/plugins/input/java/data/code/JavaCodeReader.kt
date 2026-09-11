package jadx.plugins.input.java.data.code

import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IDebugInfo
import jadx.api.plugins.input.data.ILocalVar
import jadx.api.plugins.input.data.ITry
import jadx.api.plugins.input.data.impl.CatchData
import jadx.api.plugins.input.data.impl.DebugInfo
import jadx.api.plugins.input.insns.InsnData
import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType
import jadx.plugins.input.java.data.attributes.debuginfo.JavaLocalVar
import jadx.plugins.input.java.data.attributes.debuginfo.LineNumberTableAttr
import jadx.plugins.input.java.data.attributes.debuginfo.LocalVarTypesAttr
import jadx.plugins.input.java.data.attributes.debuginfo.LocalVarsAttr
import jadx.plugins.input.java.data.attributes.types.StackMapTableAttr
import jadx.plugins.input.java.data.code.trycatch.JavaSingleCatch
import jadx.plugins.input.java.data.code.trycatch.JavaTryData
import jadx.plugins.input.java.utils.JavaClassParseException
import org.jetbrains.annotations.Nullable
import java.util.Collections
import java.util.function.Consumer

/**
 * class 文件 code attribute 的字节码读取器。
 *
 **做什么**：驱动"读 opcode → 查 [JavaInsnsRegister] → 解码器分配寄存器"的主循环，
 * 把原始字节码转成带寄存器号的 [InsnData] 序列交给 jadx-core；
 * 同时提供调试信息（行号表/局部变量）与异常表的读取。
 */
class JavaCodeReader(
	private val clsData: JavaClassData,
	offset: Int,
) : ICodeReader {

	private val reader: DataReader = clsData.getData()
	private val codeOffset: Int = offset

	override fun copy(): ICodeReader = this

	override fun visitInstructions(insnConsumer: Consumer<InsnData>) {
		val excHandlers = getExcHandlers()
		jumpToCodeAttributes()
		val stackMapTable = clsData.getAttributesReader().loadOne(reader, JavaAttrType.STACK_MAP_TABLE)

		val maxStack = readMaxStack()
		reader.skip(2) // max_locals
		val codeSize = reader.readU4()

		val state = CodeDecodeState(clsData, reader, maxStack, excHandlers, stackMapTable)
		val insn = JavaInsnData(state)
		state.setInsn(insn)
		var offset = 0
		while (offset < codeSize) {
			insn.setDecoded(false)
			insn.setOffset(offset)
			insn.setInsnStart(reader.offset)

			val opcode = reader.readU1()
			val insnInfo = JavaInsnsRegister.get(opcode) ?: throw JavaClassParseException("Unknown opcode: 0x" + Integer.toHexString(opcode))
			insn.setOpcodeUnit(opcode)
			insn.setInsnInfo(insnInfo)
			insn.setRegsCount(insnInfo.regsCount)
			insn.setOpcode(insnInfo.apiOpcode)
			insn.setPayloadSize(insnInfo.payloadSize)
			insn.setPayload(null)

			state.onInsn(offset)
			insnConsumer.accept(insn)

			var payloadSize = insn.getPayloadSize()
			if (!insn.isDecoded()) {
				if (payloadSize == -1) {
					insn.skip()
					payloadSize = insn.getPayloadSize()
				} else {
					reader.skip(payloadSize)
				}
			}
			offset += 1 + payloadSize
		}
	}

	override fun getRegistersCount(): Int {
		val maxStack = readMaxStack()
		val maxLocals = reader.readU2()
		return maxStack + maxLocals
	}

	override fun getArgsStartReg(): Int = readMaxStack()

	private fun readMaxStack(): Int {
		reader.absPos(codeOffset)
		val maxStack = reader.readU2()
		return maxStack + 1 // add one temporary register (for `swap` opcode)
	}

	override fun getUnitsCount(): Int = reader.absPos(codeOffset + 4).readU4()

	companion object {
		private val DEBUG_INFO_ATTRIBUTES: Set<JavaAttrType<*>> = setOf(
			JavaAttrType.LINE_NUMBER_TABLE,
			JavaAttrType.LOCAL_VAR_TABLE,
			JavaAttrType.LOCAL_VAR_TYPE_TABLE,
		)

		private fun convertSingleCatches(list: MutableList<JavaSingleCatch>): CatchData {
			var allHandler = -1
			for (singleCatch in list) {
				if (singleCatch.type == null) {
					allHandler = singleCatch.handler
					list.remove(singleCatch)
					break
				}
			}
			val len = list.size
			val handlers = IntArray(len)
			val types = arrayOfNulls<String>(len)
			for (i in 0 until len) {
				val singleCatch = list[i]
				handlers[i] = singleCatch.handler
				types[i] = singleCatch.type
			}
			// CatchData 声明 Array<String>，但 type 运行时可为 null（原 Java String[] 同样允许）
			@Suppress("UNCHECKED_CAST")
			return CatchData(handlers, types as Array<String>, allHandler)
		}
	}

	@Nullable
	override fun getDebugInfo(): IDebugInfo? {
		val maxStack = readMaxStack()
		jumpToCodeAttributes()
		val attrs: JavaAttrStorage = clsData.getAttributesReader().loadMulti(reader, DEBUG_INFO_ATTRIBUTES)
		val linesAttr = attrs.get(JavaAttrType.LINE_NUMBER_TABLE)
		val varsAttr = attrs.get(JavaAttrType.LOCAL_VAR_TABLE)
		if (linesAttr == null && varsAttr == null) {
			return null
		}
		val linesMap: Map<Int, Int> = if (linesAttr != null) linesAttr.lineMap else Collections.emptyMap()

		val vars: List<ILocalVar>
		if (varsAttr == null) {
			vars = Collections.emptyList()
		} else {
			val javaVars = varsAttr.vars
			val typedVars = attrs.get(JavaAttrType.LOCAL_VAR_TYPE_TABLE)
			if (typedVars != null && !typedVars.vars.isEmpty()) {
				// merge signature from typedVars into javaVars
				val varsMap = HashMap<JavaLocalVar, JavaLocalVar>(javaVars.size)
				for (v in javaVars) {
					varsMap[v] = v
				}
				for (typedVar in typedVars.vars) {
					val jv = varsMap[typedVar]
					if (jv != null) {
						jv.setSignature(typedVar.getSignature())
					}
				}
			}
			for (v in javaVars) {
				v.shiftRegNum(maxStack)
			}
			vars = Collections.unmodifiableList(javaVars)
		}
		return DebugInfo(linesMap, vars)
	}

	override fun getCodeOffset(): Int = codeOffset

	override fun getTries(): List<ITry> {
		jumpToTries()
		val excTableLen = reader.readU2()
		if (excTableLen == 0) {
			return Collections.emptyList()
		}
		val constPool: ConstPoolReader = clsData.getConstPoolReader()
		val tries = HashMap<JavaTryData, MutableList<JavaSingleCatch>>(excTableLen)
		for (i in 0 until excTableLen) {
			val start = reader.readU2()
			val end = reader.readU2()
			val handler = reader.readU2()
			val type = reader.readU2()
			val tryData = JavaTryData(start, end)
			val catches = tries.computeIfAbsent(tryData) { ArrayList<JavaSingleCatch>() }
			if (type == 0) {
				catches.add(JavaSingleCatch(handler, null))
			} else {
				catches.add(JavaSingleCatch(handler, constPool.getClass(type)))
			}
		}
		// 与原 Java stream().map(...).collect(toList()) 等价：保持 entrySet 迭代顺序，边遍历边 setCatch
		val result = ArrayList<ITry>(tries.size)
		for (e in tries.entries) {
			val tryData = e.key
			tryData.setCatch(convertSingleCatches(e.value))
			result.add(tryData)
		}
		return result
	}

	private fun getExcHandlers(): Set<Int> {
		jumpToTries()
		val excTableLen = reader.readU2()
		if (excTableLen == 0) {
			return Collections.emptySet()
		}
		val set = HashSet<Int>(excTableLen)
		for (i in 0 until excTableLen) {
			reader.skip(4) // start_pc + end_pc
			val handler = reader.readU2()
			reader.skip(2) // catch_type
			set.add(handler)
		}
		return set
	}

	private fun jumpToTries() {
		reader.absPos(codeOffset + 4)
		reader.skip(reader.readU4()) // code length
	}

	private fun jumpToCodeAttributes() {
		jumpToTries()
		reader.skip(reader.readU2() * 8) // exceptions table
	}
}
