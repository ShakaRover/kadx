package kadx.plugins.input.java.data.code

import kadx.api.plugins.input.data.ICallSite
import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.IMethodProto
import kadx.api.plugins.input.data.IMethodRef
import kadx.api.plugins.input.insns.InsnData
import kadx.api.plugins.input.insns.InsnIndexType
import kadx.api.plugins.input.insns.Opcode
import kadx.api.plugins.input.insns.custom.ICustomPayload
import kadx.plugins.input.java.data.ConstPoolReader
import kadx.plugins.input.java.data.code.decoders.IJavaInsnDecoder
import org.jetbrains.annotations.Nullable

/**
 * class 文件字节码中单条指令的运行时数据。
 *
 **做什么**：持有解码主循环逐步填充的操作数（寄存器号、字面量、跳转目标、常量池索引等）；
 * [decode] 委托给 [JavaInsnInfo.decoder] 完成"读操作数 + 分配寄存器"，
 * 之后 kadx-core 通过 InsnData 接口读取结果。
 *
 **为什么字段多为可变**：指令解析是流式的——主循环先建空壳（setOpcode/setInsnStart），
 * 再按格式逐个填操作数，最后标记 decoded。
 */
class JavaInsnData(private val state: CodeDecodeState) : InsnData {

	private var insnInfo: JavaInsnInfo? = null
	private var opcodeValue: Opcode? = null
	private var decoded = false
	private var opcodeUnit = 0
	private var payloadSizeValue = 0
	private var insnStart = 0
	private var offsetValue = 0
	private var regsCountValue = 0
	private var argsReg = IntArray(16)
	private var resultRegValue = 0
	private var literalValue = 0L
	private var targetValue = 0
	private var indexValue = 0

	@Nullable
	private var payloadValue: ICustomPayload? = null

	private fun insnInfoOrThrow(): JavaInsnInfo = insnInfo ?: throw NullPointerException("insnInfo is not set")

	override fun decode() {
		// 原 Java 直接 insnInfo.getDecoder()，insnInfo 为 null 时 NPE；保持等价
		val decoder: IJavaInsnDecoder? = insnInfoOrThrow().decoder
		if (decoder != null) {
			decoder.decode(state)
			state.decoded()
		}
		decoded = true
	}

	fun skip() {
		val decoder: IJavaInsnDecoder? = insnInfoOrThrow().decoder
		if (decoder != null) {
			decoder.skip(state)
		}
	}

	override val offset: Int get() = offsetValue

	override val fileOffset: Int get() = insnStart

	override val opcode: Opcode get() {
		// 接口声明非空；setOpcode 在 decode 前必已执行，提前调用时原 Java 同样 NPE
		return opcodeValue ?: throw NullPointerException("opcode is not set")
	}

	fun setOpcode(opcodeValue: Opcode) {
		this.opcodeValue = opcodeValue
	}

	// 原 Java 直接 insnInfo.getName()，insnInfo 为 null 时 NPE；保持等价
	override val opcodeMnemonic: String? get() = insnInfoOrThrow().name

	override val byteCode: ByteArray get() {
		val reader = state.reader()
		val startOffset = reader.offset
		try {
			reader.absPos(insnStart)
			return reader.readBytes(1 + payloadSizeValue)
		} finally {
			reader.absPos(startOffset)
		}
	}

	// 接口声明非空（调用前 setInsnInfo 必已执行）
	override val indexType: InsnIndexType get() = insnInfoOrThrow().indexType

	override val rawOpcodeUnit: Int get() = opcodeUnit

	override val regsCount: Int get() = regsCountValue

	override fun getReg(argNum: Int): Int = argsReg[argNum]

	override val resultReg: Int get() = resultRegValue

	fun setResultReg(resultRegValue: Int) {
		this.resultRegValue = resultRegValue
	}

	override val literal: Long get() = literalValue

	override val target: Int get() = targetValue

	override val index: Int get() = indexValue

	val payloadSize: Int get() = payloadSizeValue

	override val indexAsString: String? get() = constPoolReader().getUtf8(indexValue)

	override val indexAsType: String? get() {
		if (insnInfoOrThrow().opcode == 0xbc) { // newarray
			return ArrayType.byValue(indexValue)
		}
		return constPoolReader().getClass(indexValue)
	}

	override val indexAsField: IFieldRef? get() = constPoolReader().getFieldRef(indexValue)

	override val indexAsMethod: IMethodRef? get() = constPoolReader().getMethodRef(indexValue)

	override val indexAsCallSite: ICallSite? get() = constPoolReader().getCallSite(indexValue)

	override fun getIndexAsProto(protoIndex: Int): IMethodProto? = null

	override val indexAsMethodHandle: IMethodHandle? get() = null

	@get:Nullable
	override val payload: ICustomPayload? get() = payloadValue

	fun setInsnInfo(insnInfo: JavaInsnInfo) {
		this.insnInfo = insnInfo
	}

	val isDecoded: Boolean get() = decoded

	fun setDecoded(decoded: Boolean) {
		this.decoded = decoded
	}

	fun setOpcodeUnit(opcodeUnit: Int) {
		this.opcodeUnit = opcodeUnit
	}

	fun setPayloadSize(payloadSizeValue: Int) {
		this.payloadSizeValue = payloadSizeValue
	}

	fun setInsnStart(insnStart: Int) {
		this.insnStart = insnStart
	}

	fun setOffset(offsetValue: Int) {
		this.offsetValue = offsetValue
	}

	fun setArgReg(arg: Int, reg: Int) {
		argsReg[arg] = reg
	}

	fun setRegsCount(regsCountValue: Int) {
		this.regsCountValue = regsCountValue
		if (argsReg.size < regsCountValue) {
			argsReg = IntArray(regsCountValue)
		}
	}

	val regsArray: IntArray get() = argsReg

	fun setLiteral(literalValue: Long) {
		this.literalValue = literalValue
	}

	fun setTarget(targetValue: Int) {
		this.targetValue = targetValue
	}

	fun setIndex(indexValue: Int) {
		this.indexValue = indexValue
	}

	fun setPayload(payloadValue: ICustomPayload?) {
		this.payloadValue = payloadValue
	}

	fun constPoolReader(): ConstPoolReader = state.clsData().constPoolReader

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(String.format("0x%04X", offsetValue))
		sb.append(": ").append(opcode)
		if (insnInfo == null) {
			sb.append(String.format("(0x%04X)", opcodeUnit))
		} else {
			val regsCountValue = regsCount
			if (isDecoded) {
				sb.append(' ')
				for (i in 0 until regsCountValue) {
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
