package jadx.plugins.input.java.data.code

import jadx.api.plugins.input.data.ICallSite
import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.insns.InsnData
import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import jadx.api.plugins.input.insns.custom.ICustomPayload
import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.code.decoders.IJavaInsnDecoder
import org.jetbrains.annotations.Nullable

/**
 * class 文件字节码中单条指令的运行时数据。
 *
 **做什么**：持有解码主循环逐步填充的操作数（寄存器号、字面量、跳转目标、常量池索引等）；
 * [decode] 委托给 [JavaInsnInfo.decoder] 完成"读操作数 + 分配寄存器"，
 * 之后 jadx-core 通过 InsnData 接口读取结果。
 *
 **为什么字段多为可变**：指令解析是流式的——主循环先建空壳（setOpcode/setInsnStart），
 * 再按格式逐个填操作数，最后标记 decoded。
 */
class JavaInsnData(private val state: CodeDecodeState) : InsnData {

	private var insnInfo: JavaInsnInfo? = null
	private var opcode: Opcode? = null
	private var decoded = false
	private var opcodeUnit = 0
	private var payloadSize = 0
	private var insnStart = 0
	private var offset = 0
	private var regsCount = 0
	private var argsReg = IntArray(16)
	private var resultReg = 0
	private var literal = 0L
	private var target = 0
	private var index = 0

	@Nullable
	private var payload: ICustomPayload? = null

	override fun decode() {
		// 原 Java 直接 insnInfo.getDecoder()，insnInfo 为 null 时 NPE；保持等价
		val decoder: IJavaInsnDecoder? = insnInfo!!.decoder
		if (decoder != null) {
			decoder.decode(state)
			state.decoded()
		}
		decoded = true
	}

	fun skip() {
		val decoder: IJavaInsnDecoder? = insnInfo!!.decoder
		if (decoder != null) {
			decoder.skip(state)
		}
	}

	override fun getOffset(): Int = offset

	override fun getFileOffset(): Int = insnStart

	override fun getOpcode(): Opcode {
		// 接口声明非空；setOpcode 在 decode 前必已执行，提前调用时原 Java 同样 NPE
		return opcode!!
	}

	fun setOpcode(opcode: Opcode) {
		this.opcode = opcode
	}

	// 原 Java 直接 insnInfo.getName()，insnInfo 为 null 时 NPE；保持等价
	override fun getOpcodeMnemonic(): String? = insnInfo!!.name

	override fun getByteCode(): ByteArray {
		val reader = state.reader()
		val startOffset = reader.offset
		try {
			reader.absPos(insnStart)
			return reader.readBytes(1 + payloadSize)
		} finally {
			reader.absPos(startOffset)
		}
	}

	// 接口声明非空（调用前 setInsnInfo 必已执行）
	override fun getIndexType(): InsnIndexType = insnInfo!!.indexType

	override fun getRawOpcodeUnit(): Int = opcodeUnit

	override fun getRegsCount(): Int = regsCount

	override fun getReg(argNum: Int): Int = argsReg[argNum]

	override fun getResultReg(): Int = resultReg

	fun setResultReg(resultReg: Int) {
		this.resultReg = resultReg
	}

	override fun getLiteral(): Long = literal

	override fun getTarget(): Int = target

	override fun getIndex(): Int = index

	fun getPayloadSize(): Int = payloadSize

	override fun getIndexAsString(): String? = constPoolReader().getUtf8(index)

	override fun getIndexAsType(): String? {
		if (insnInfo!!.opcode == 0xbc) { // newarray
			return ArrayType.byValue(index)
		}
		return constPoolReader().getClass(index)
	}

	override fun getIndexAsField(): IFieldRef? = constPoolReader().getFieldRef(index)

	override fun getIndexAsMethod(): IMethodRef? = constPoolReader().getMethodRef(index)

	override fun getIndexAsCallSite(): ICallSite? = constPoolReader().getCallSite(index)

	override fun getIndexAsProto(protoIndex: Int): IMethodProto? = null

	override fun getIndexAsMethodHandle(): IMethodHandle? = null

	@Nullable
	override fun getPayload(): ICustomPayload? = payload

	fun setInsnInfo(insnInfo: JavaInsnInfo) {
		this.insnInfo = insnInfo
	}

	fun isDecoded(): Boolean = decoded

	fun setDecoded(decoded: Boolean) {
		this.decoded = decoded
	}

	fun setOpcodeUnit(opcodeUnit: Int) {
		this.opcodeUnit = opcodeUnit
	}

	fun setPayloadSize(payloadSize: Int) {
		this.payloadSize = payloadSize
	}

	fun setInsnStart(insnStart: Int) {
		this.insnStart = insnStart
	}

	fun setOffset(offset: Int) {
		this.offset = offset
	}

	fun setArgReg(arg: Int, reg: Int) {
		argsReg[arg] = reg
	}

	fun setRegsCount(regsCount: Int) {
		this.regsCount = regsCount
		if (argsReg.size < regsCount) {
			argsReg = IntArray(regsCount)
		}
	}

	fun getRegsArray(): IntArray = argsReg

	fun setLiteral(literal: Long) {
		this.literal = literal
	}

	fun setTarget(target: Int) {
		this.target = target
	}

	fun setIndex(index: Int) {
		this.index = index
	}

	fun setPayload(payload: ICustomPayload?) {
		this.payload = payload
	}

	fun constPoolReader(): ConstPoolReader = state.clsData().getConstPoolReader()

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(String.format("0x%04X", offset))
		sb.append(": ").append(getOpcode())
		if (insnInfo == null) {
			sb.append(String.format("(0x%04X)", opcodeUnit))
		} else {
			val regsCount = getRegsCount()
			if (isDecoded()) {
				sb.append(' ')
				for (i in 0 until regsCount) {
					if (i != 0) {
						sb.append(", ")
					}
					sb.append("r").append(argsReg[i])
				}
			}
		}
		return sb.toString()
	}
}
