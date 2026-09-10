package jadx.plugins.input.dex.insns

import jadx.api.plugins.input.data.ICallSite
import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.insns.InsnData
import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import jadx.api.plugins.input.insns.custom.ICustomPayload
import jadx.plugins.input.dex.sections.DexCodeReader
import jadx.plugins.input.dex.sections.SectionReader

/**
 * 一条 DEX 指令的数据容器：持有原始操作码、解码后的寄存器/字面量/索引与可选 payload。
 *
 * **背景**：[DexCodeReader] 遍历方法体字节流时为每条指令创建本对象；[decode] 惰性触发
 * [DexInsnInfo.format] 对应的格式解码，把操作数填入各字段；smali 输出（[jadx.plugins.input.dex.smali.SmaliPrinter]）
 * 与 jadx-core 的 IR 转换都通过 [InsnData] 接口读取这些字段。
 *
 * **Kotlin 转换说明**：[InsnData] 是 Kotlin 接口且以抽象函数声明方法，故这里全部用显式
 * `override fun`（属性不能覆写 Kotlin 接口的抽象函数）；非接口成员保持原 Java getter/setter
 * 命名，Java/Kotlin 调用方零改动。
 */
public class DexInsnData(
	private val codeData: DexCodeReader,
	private val externalReader: SectionReader,
) : InsnData {

	/** 独立的 section 读取器副本（invoke-custom 解析 call site 时不干扰主游标）*/
	private val secondExtReader: SectionReader = externalReader.copy()

	private var insnInfo: DexInsnInfo? = null
	private var decoded = false
	private var opcodeUnit = 0

	/** 指令长度（code unit 数）；DexCodeReader 用属性语法 `.length` 累加偏移 */
	var length = 0
	private var insnStart = 0

	private var offset = 0
	private var argsReg = IntArray(5)
	private var regsCount = 0
	private var literal = 0L
	private var target = 0
	private var index = 0
	private var payload: ICustomPayload? = null

	/** 惰性解码：首次调用时按指令格式从字节流读取操作数 */
	override fun decode() {
		val info = insnInfo ?: return
		if (!decoded) {
			codeData.decode(this)
		}
	}

	override fun getOffset(): Int = offset

	override fun getFileOffset(): Int = insnStart

	override fun getOpcode(): Opcode {
		val info = insnInfo
		if (info == null) {
			return Opcode.UNKNOWN
		}
		return info.getApiOpcode()
	}

	override fun getOpcodeMnemonic(): String = DexInsnMnemonics.get(opcodeUnit)

	override fun getByteCode(): ByteArray = externalReader.getByteCode(insnStart, length * 2) // a unit is 2 bytes

	override fun getRawOpcodeUnit(): Int = opcodeUnit

	override fun getRegsCount(): Int = regsCount

	override fun getReg(argNum: Int): Int = argsReg[argNum]

	override fun getResultReg(): Int = -1

	override fun getLiteral(): Long = literal

	override fun getTarget(): Int = target

	override fun getIndex(): Int = index

	override fun getIndexType(): InsnIndexType {
		// 与原 Java 一致：insnInfo 未设置时抛 NPE（正常流程 decode 前必已设置）
		val info = checkNotNull(insnInfo) { "insn info is not set" }
		return info.getIndexType()
	}

	override fun getIndexAsString(): String? = externalReader.getString(index)

	override fun getIndexAsType(): String? = externalReader.getType(index)

	override fun getIndexAsField(): IFieldRef = externalReader.getFieldRef(index)

	override fun getIndexAsMethod(): IMethodRef = externalReader.getMethodRef(index)

	override fun getIndexAsCallSite(): ICallSite = externalReader.getCallSite(index, secondExtReader)

	/**
	 * 按 proto 表索引取方法原型。
	 *
	 * Currently, protoIndex is either being stored at index or target, index for const-method-type,
	 * target for invoke-polymorphic(/range)
	 */
	override fun getIndexAsProto(protoIndex: Int): IMethodProto = externalReader.getMethodProto(protoIndex)

	override fun getIndexAsMethodHandle(): IMethodHandle = externalReader.getMethodHandle(index)

	override fun getPayload(): ICustomPayload? = payload

	public fun getArgsReg(): IntArray = argsReg

	public fun setArgsReg(argsReg: IntArray) {
		this.argsReg = argsReg
	}

	public fun setRegsCount(regsCount: Int) {
		this.regsCount = regsCount
	}

	public fun setInsnStart(start: Int) {
		this.insnStart = start
	}

	public fun setLiteral(literal: Long) {
		this.literal = literal
	}

	public fun setTarget(target: Int) {
		this.target = target
	}

	public fun setIndex(index: Int) {
		this.index = index
	}

	public fun isDecoded(): Boolean = decoded

	public fun setDecoded(decoded: Boolean) {
		this.decoded = decoded
	}

	public fun setOffset(offset: Int) {
		this.offset = offset
	}

	public fun getInsnInfo(): DexInsnInfo? = insnInfo

	public fun setInsnInfo(insnInfo: DexInsnInfo?) {
		this.insnInfo = insnInfo
	}

	public fun getCodeData(): DexCodeReader = codeData

	public fun getOpcodeUnit(): Int = opcodeUnit

	public fun setOpcodeUnit(opcodeUnit: Int) {
		this.opcodeUnit = opcodeUnit
	}

	public fun setPayload(payload: ICustomPayload?) {
		this.payload = payload
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("0x%04X".format(offset))
		sb.append(": ").append(getOpcode())
		val info = insnInfo
		if (info == null) {
			sb.append("(0x%04X)".format(opcodeUnit))
		} else {
			val count = getRegsCount()
			if (isDecoded()) {
				sb.append(' ')
				for (i in 0 until count) {
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
