package kadx.plugins.input.dex.insns

import kadx.api.plugins.input.data.ICallSite
import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.IMethodProto
import kadx.api.plugins.input.data.IMethodRef
import kadx.api.plugins.input.insns.InsnData
import kadx.api.plugins.input.insns.InsnIndexType
import kadx.api.plugins.input.insns.Opcode
import kadx.api.plugins.input.insns.custom.ICustomPayload
import kadx.plugins.input.dex.sections.DexCodeReader
import kadx.plugins.input.dex.sections.SectionReader

/**
 * 一条 DEX 指令的数据容器：持有原始操作码、解码后的寄存器/字面量/索引与可选 payload。
 *
 * **背景**：[DexCodeReader] 遍历方法体字节流时为每条指令创建本对象；[decode] 惰性触发
 * [DexInsnInfo.format] 对应的格式解码，把操作数填入各字段；smali 输出（[kadx.plugins.input.dex.smali.SmaliPrinter]）
 * 与 kadx-core 的 IR 转换都通过 [InsnData] 接口读取这些字段。
 *
 * **Kotlin 转换说明**：[InsnData] 是 Kotlin 接口且以抽象函数声明方法，故这里全部用显式
 * `override fun`（属性不能覆写 Kotlin 接口的抽象函数）；非接口成员保持原 Java getter/setter
 * 命名，Java/Kotlin 调用方零改动。
 */
public class DexInsnData(
	private val codeDataValue: DexCodeReader,
	private val externalReader: SectionReader,
) : InsnData {

	/** 独立的 section 读取器副本（invoke-custom 解析 call site 时不干扰主游标）*/
	private val secondExtReader: SectionReader = externalReader.copy()

	private var insnInfoValue: DexInsnInfo? = null
	private var decoded = false
	private var opcodeUnitValue = 0

	/** 指令长度（code unit 数）；DexCodeReader 用属性语法 `.length` 累加偏移 */
	var length = 0
	private var insnStart = 0

	private var offsetValue = 0
	private var argsRegValue = IntArray(5)
	private var regsCountValue = 0
	private var literalValue = 0L
	private var targetValue = 0
	private var indexValue = 0
	private var payloadValue: ICustomPayload? = null

	/** 惰性解码：首次调用时按指令格式从字节流读取操作数 */
	override fun decode() {
		val info = insnInfoValue ?: return
		if (!decoded) {
			codeDataValue.decode(this)
		}
	}

	override val offset: Int get() = offsetValue

	override val fileOffset: Int get() = insnStart

	override val opcode: Opcode get() {
		val info = insnInfoValue
		if (info == null) {
			return Opcode.UNKNOWN
		}
		return info.apiOpcode
	}

	override val opcodeMnemonic: String get() = DexInsnMnemonics.get(opcodeUnitValue)

	override val byteCode: ByteArray get() = externalReader.getByteCode(insnStart, length * 2) // a unit is 2 bytes

	override val rawOpcodeUnit: Int get() = opcodeUnitValue

	override val regsCount: Int get() = regsCountValue

	override fun getReg(argNum: Int): Int = argsRegValue[argNum]

	override val resultReg: Int get() = -1

	override val literal: Long get() = literalValue

	override val target: Int get() = targetValue

	override val index: Int get() = indexValue

	override val indexType: InsnIndexType get() {
		// 与原 Java 一致：insnInfo 未设置时抛 NPE（正常流程 decode 前必已设置）
		val info = checkNotNull(insnInfoValue) { "insn info is not set" }
		return info.indexType
	}

	override val indexAsString: String? get() = externalReader.getString(indexValue)

	override val indexAsType: String? get() = externalReader.getType(indexValue)

	override val indexAsField: IFieldRef get() = externalReader.getFieldRef(indexValue)

	override val indexAsMethod: IMethodRef get() = externalReader.getMethodRef(indexValue)

	override val indexAsCallSite: ICallSite get() = externalReader.getCallSite(indexValue, secondExtReader)

	/**
	 * 按 proto 表索引取方法原型。
	 *
	 * Currently, protoIndex is either being stored at index or target, index for const-method-type,
	 * target for invoke-polymorphic(/range)
	 */
	override fun getIndexAsProto(protoIndex: Int): IMethodProto = externalReader.getMethodProto(protoIndex)

	override val indexAsMethodHandle: IMethodHandle get() = externalReader.getMethodHandle(indexValue)

	override val payload: ICustomPayload? get() = payloadValue

	public val argsReg: IntArray get() = argsRegValue

	public fun setArgsReg(argsRegValue: IntArray) {
		this.argsRegValue = argsRegValue
	}

	public fun setRegsCount(regsCountValue: Int) {
		this.regsCountValue = regsCountValue
	}

	public fun setInsnStart(start: Int) {
		this.insnStart = start
	}

	public fun setLiteral(literalValue: Long) {
		this.literalValue = literalValue
	}

	public fun setTarget(targetValue: Int) {
		this.targetValue = targetValue
	}

	public fun setIndex(indexValue: Int) {
		this.indexValue = indexValue
	}

	public val isDecoded: Boolean get() = decoded

	public fun setDecoded(decoded: Boolean) {
		this.decoded = decoded
	}

	public fun setOffset(offsetValue: Int) {
		this.offsetValue = offsetValue
	}

	public val insnInfo: DexInsnInfo? get() = insnInfoValue

	public fun setInsnInfo(insnInfoValue: DexInsnInfo?) {
		this.insnInfoValue = insnInfoValue
	}

	public val codeData: DexCodeReader get() = codeDataValue

	public val opcodeUnit: Int get() = opcodeUnitValue

	public fun setOpcodeUnit(opcodeUnitValue: Int) {
		this.opcodeUnitValue = opcodeUnitValue
	}

	public fun setPayload(payloadValue: ICustomPayload?) {
		this.payloadValue = payloadValue
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("0x%04X".format(offsetValue))
		sb.append(": ").append(opcode)
		val info = insnInfoValue
		if (info == null) {
			sb.append("(0x%04X)".format(opcodeUnitValue))
		} else {
			val count = regsCount
			if (isDecoded) {
				sb.append(' ')
				for (i in 0 until count) {
					if (i != 0) {
						sb.append(", ")
					}
					sb.append("r").append(argsRegValue[i])
				}
			}
		}
		return sb.toString()
	}
}
