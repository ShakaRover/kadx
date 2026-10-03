package jadx.plugins.input.dex.sections

import jadx.api.plugins.input.data.ICatch
import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IDebugInfo
import jadx.api.plugins.input.data.ITry
import jadx.api.plugins.input.data.impl.CatchData
import jadx.api.plugins.input.data.impl.TryData
import jadx.api.plugins.input.insns.InsnData
import jadx.core.utils.exceptions.InvalidDataException
import jadx.plugins.input.dex.DexException
import jadx.plugins.input.dex.insns.DexInsnData
import jadx.plugins.input.dex.insns.DexInsnInfo
import jadx.plugins.input.dex.sections.debuginfo.DebugInfoParser
import java.util.function.Consumer
import kotlin.math.abs

/**
 * DEX 方法代码读取器：解析 code_item（寄存器数、指令序列、try/catch、debug info）。
 *
 * **背景**：
 * 1. [visitInstructions] 按 2 字节 code unit 逐条遍历指令，通过 [DexInsnInfo.get] 查表得到
 *    opcode/format；[decode] / [skip] 委托给对应 [DexInsnFormat] 完成参数解码或快速跳过；
 * 2. try/catch 信息位于指令区之后（含对齐填充），handler 列表以 ULEB128/SLEB128 编码，
 *    [getCatchHandlers] 先整体解析 handler 表再回填到各 try 区间；
 * 3. debug info 由 [DebugInfoParser] 独立解析（行号映射 + 局部变量生命周期）。
 */
public class DexCodeReader(private val inReader: SectionReader) : ICodeReader {

	/** 当前方法在 method_ids section 中的索引（用于查询参数类型）*/
	var mthId: Int = 0

	override fun copy(): DexCodeReader {
		val copy = DexCodeReader(inReader.copy())
		copy.mthId = mthId
		return copy
	}

	public fun setOffset(offset: Int) {
		inReader.offset = offset
	}

	override val registersCount: Int get() = inReader.pos(0).readUShort()

	override val argsStartReg: Int get() = -1

	override val unitsCount: Int get() = inReader.pos(12).readInt()

	override fun visitInstructions(insnConsumer: Consumer<InsnData>) {
		val insnData = DexInsnData(this, inReader.copy())
		inReader.pos(12) // 跳过 code_item 头部（registers_size/insns_size/debug_off/tries...）
		val size = inReader.readInt()
		var offset = 0 // in code units (2 byte)
		while (offset < size) {
			val insnStart = inReader.absPos
			val opcodeUnit = inReader.readUShort()
			val insnInfo = DexInsnInfo.get(opcodeUnit)
			insnData.setInsnStart(insnStart)
			insnData.setOffset(offset)
			insnData.setInsnInfo(insnInfo)
			insnData.setOpcodeUnit(opcodeUnit)
			insnData.setPayload(null)
			insnData.setDecoded(false)
			if (insnInfo != null) {
				val format = insnInfo.format
				insnData.setRegsCount(format.regsCount)
				insnData.length = format.length
			} else {
				insnData.setRegsCount(0)
				insnData.length = 1
			}

			insnConsumer.accept(insnData)

			if (!insnData.isDecoded) {
				skip(insnData)
			}
			offset += insnData.length
		}
	}

	/** 委托 [DexInsnFormat.decode] 解码指令参数（寄存器/字面量/索引）。 */
	public fun decode(insn: DexInsnData) {
		val insnInfo = checkNotNull(insn.insnInfo) { "insn info is not set" }
		val format = insnInfo.format
		format.decode(insn, insn.opcodeUnit, insn.codeData.inReader)
		insn.setDecoded(true)
	}

	/** 未解码的指令按格式跳过对应字节数。 */
	public fun skip(insn: DexInsnData) {
		val insnInfo = insn.insnInfo ?: return
		val codeReader = insn.codeData
		insnInfo.format.skip(insn, codeReader.inReader)
	}

	override val debugInfo: IDebugInfo? get() {
		val debugOff = inReader.pos(8).readInt()
		if (debugOff == 0) {
			return null
		}
		if (debugOff < 0 || debugOff > inReader.size()) {
			throw InvalidDataException("Invalid debug info offset")
		}
		val regsCount = registersCount
		val debugInfoParser = DebugInfoParser(inReader, regsCount, unitsCount)
		debugInfoParser.initMthArgs(regsCount, inReader.getMethodParamTypes(mthId))
		return debugInfoParser.process(debugOff)
	}

	private val triesCount: Int get() = inReader.pos(6).readUShort()

	private val triesOffset: Int get() {
		val triesCount = triesCount
		if (triesCount == 0) {
			return -1
		}
		val insnsCount = unitsCount
		val padding = if (insnsCount % 2 == 1) 2 else 0 // try_list 需 4 字节对齐，奇数指令补 2 字节
		return 4 * 4 + insnsCount * 2 + padding
	}

	override val tries: List<ITry> get() {
		val triesOffset = triesOffset
		if (triesOffset == -1) {
			return emptyList()
		}
		val triesCount = triesCount
		val catchHandlers = getCatchHandlers(triesOffset + 8 * triesCount, inReader.copy())
		inReader.pos(triesOffset)
		val triesList = ArrayList<ITry>(triesCount)
		for (i in 0 until triesCount) {
			val startAddr = inReader.readInt()
			val insnsCount = inReader.readUShort()
			val handlerOff = inReader.readUShort()
			val catchHandler = catchHandlers[handlerOff] ?: throw DexException(
				"Catch handler not found by byte offset: $handlerOff",
			)
			triesList.add(TryData(startAddr, startAddr + insnsCount - 1, catchHandler))
		}
		return triesList
	}

	/**
	 * 解析 catch handlers 表（位于 try_list 之后）。
	 *
	 * 每条 handler：SLEB128 size_and_type（绝对值为地址数，负值表示带 catch-all），
	 * 随后依次读取 type_idx/addr 对；catch-all 地址在末尾。
	 * @param offset handlers 表起始偏移
	 * @return key 为 handler 相对 try_list 的字节偏移，value 为 [ICatch]
	 */
	private fun getCatchHandlers(offset: Int, ext: SectionReader): Map<Int, ICatch> {
		inReader.pos(offset)
		val byteOffsetStart = inReader.absPos
		val size = inReader.readUleb128()
		val map = HashMap<Int, ICatch>(size)
		for (i in 0 until size) {
			val byteIndex = inReader.absPos - byteOffsetStart
			val sizeAndType = inReader.readSleb128()
			val handlersLen = abs(sizeAndType)
			val addr = IntArray(handlersLen)
			val types = Array<String>(handlersLen) { "" }
			for (h in 0 until handlersLen) {
				// catch handler 的类型索引必须有效
				types[h] = checkNotNull(ext.getType(inReader.readUleb128())) { "invalid type index in catch handler" }
				addr[h] = inReader.readUleb128()
			}
			val catchAllAddr = if (sizeAndType <= 0) inReader.readUleb128() else -1
			map[byteIndex] = CatchData(addr, types, catchAllAddr)
		}
		return map
	}

	override val codeOffset: Int get() = inReader.offset
}
