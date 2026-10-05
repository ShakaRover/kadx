package kadx.plugins.input.dex.sections.debuginfo

import kadx.api.plugins.input.data.ILocalVar
import kadx.api.plugins.input.data.impl.DebugInfo
import kadx.plugins.input.dex.sections.DexConsts
import kadx.plugins.input.dex.sections.SectionReader

/**
 * DEX debug_info 解析器：从方法调试信息中恢复行号映射与局部变量生命周期。
 *
 * **背景**：
 * 1. debug_info 为字节码流，由一系列 opcode（advance_pc / start_local / special opcode...）组成；
 *    special opcode（>= [DBG_FIRST_SPECIAL]）同时推进 PC 与行号：`addr += op / DBG_LINE_RANGE`、
 *    `line += DBG_LINE_BASE + op % DBG_LINE_RANGE`；
 * 2. 局部变量按寄存器编号维护在 [locals] 数组中，start/end/restart opcode 驱动其生命周期区间；
 *    方法参数通过 [initMthArgs] 预先计算起始寄存器（从 regsCount 向下按类型宽度分配）；
 * 3. 解析结束时仍未 end 的变量自动延伸到代码末尾（codeSize - 1）。
 */
public class DebugInfoParser(
	private val inReader: SectionReader,
	regsCount: Int,
	private val codeSize: Int,
) {

	private val ext: SectionReader = inReader.copy()

	private val locals: Array<DexLocalVar?> = Array(regsCount) { null }

	private lateinit var resultList: MutableList<ILocalVar>
	private lateinit var linesMap: MutableMap<Int, Int>
	private var sourceFile: String? = null

	private lateinit var argTypes: List<String>
	private lateinit var argRegs: IntArray

	/**
	 * 初始化方法参数信息：从 [regsCount] 开始向下为每个参数分配寄存器。
	 * long/double（类型首字符 J/D）占 2 个寄存器，其余占 1 个。
	 */
	public fun initMthArgs(regsCount: Int, argTypes: List<String>) {
		if (argTypes.isEmpty()) {
			this.argTypes = emptyList()
			return
		}

		val argsCount = argTypes.size
		val argRegsArr = IntArray(argsCount)
		var regNum = regsCount
		for (i in argsCount - 1 downTo 0) {
			regNum -= getTypeLen(argTypes[i])
			argRegsArr[i] = regNum
		}
		this.argRegs = argRegsArr
		this.argTypes = argTypes
	}

	public companion object {
		private const val DBG_END_SEQUENCE: Int = 0x00
		private const val DBG_ADVANCE_PC: Int = 0x01
		private const val DBG_ADVANCE_LINE: Int = 0x02
		private const val DBG_START_LOCAL: Int = 0x03
		private const val DBG_START_LOCAL_EXTENDED: Int = 0x04
		private const val DBG_END_LOCAL: Int = 0x05
		private const val DBG_RESTART_LOCAL: Int = 0x06
		private const val DBG_SET_PROLOGUE_END: Int = 0x07
		private const val DBG_SET_EPILOGUE_BEGIN: Int = 0x08
		private const val DBG_SET_FILE: Int = 0x09

		// the smallest special opcode
		private const val DBG_FIRST_SPECIAL: Int = 0x0a

		// the smallest line number increment
		private const val DBG_LINE_BASE: Int = -4

		// the number of line increments represented
		private const val DBG_LINE_RANGE: Int = 15

		/** @return DEX 类型描述符占用的寄存器数：long/double 为 2，其余为 1 */
		public fun getTypeLen(type: String): Int = when (type[0]) {
			'J', 'D' -> 2
			else -> 1
		}
	}

	/**
	 * 从 [debugOff] 开始解析 debug_info 字节流，返回行号映射与局部变量列表。
	 */
	public fun process(debugOff: Int): DebugInfo {
		inReader.absPos(debugOff)

		var varsInfoFound = false
		resultList = ArrayList()
		linesMap = HashMap()

		var addr = 0
		var line = inReader.readUleb128()
		val paramsCount = inReader.readUleb128()
		val argsCount = argTypes.size

		for (i in 0 until paramsCount) {
			val nameId = inReader.readUleb128p1()
			val name = ext.getString(nameId)
			if (name != null && i < argsCount) {
				val paramVar = DexLocalVar(argRegs[i], name, argTypes[i])
				startVar(paramVar, addr)
				paramVar.markAsParameter()
				varsInfoFound = true
			}
		}
		while (true) {
			val c = inReader.readUByte()
			if (c == DBG_END_SEQUENCE) {
				break
			}
			when (c) {
				DBG_ADVANCE_PC -> {
					val addrInc = inReader.readUleb128()
					addr = addrChange(addr, addrInc)
				}

				DBG_ADVANCE_LINE -> {
					line += inReader.readSleb128()
				}

				DBG_START_LOCAL -> {
					val regNum = inReader.readUleb128()
					val nameId = inReader.readUleb128() - 1
					val type = inReader.readUleb128() - 1
					val localVar = DexLocalVar(ext, regNum, nameId, type, DexConsts.NO_INDEX)
					startVar(localVar, addr)
					varsInfoFound = true
				}

				DBG_START_LOCAL_EXTENDED -> {
					val regNum = inReader.readUleb128()
					val nameId = inReader.readUleb128p1()
					val type = inReader.readUleb128p1()
					val sign = inReader.readUleb128p1()
					val localVar = DexLocalVar(ext, regNum, nameId, type, sign)
					startVar(localVar, addr)
					varsInfoFound = true
				}

				DBG_RESTART_LOCAL -> {
					val regNum = inReader.readUleb128()
					restartVar(regNum, addr)
					varsInfoFound = true
				}

				DBG_END_LOCAL -> {
					val regNum = inReader.readUleb128()
					val localVar = locals[regNum]
					if (localVar != null) {
						endVar(localVar, addr)
					}
					varsInfoFound = true
				}

				DBG_SET_PROLOGUE_END, DBG_SET_EPILOGUE_BEGIN -> {
					// do nothing
				}

				DBG_SET_FILE -> {
					val idx = inReader.readUleb128() - 1
					sourceFile = ext.getString(idx)
				}

				else -> {
					// special opcode：同时推进 PC 与行号
					val adjustedOpCode = c - DBG_FIRST_SPECIAL
					val addrInc = adjustedOpCode / DBG_LINE_RANGE
					addr = addrChange(addr, addrInc)
					line += DBG_LINE_BASE + adjustedOpCode % DBG_LINE_RANGE
					setLine(addr, line)
				}
			}
		}
		if (varsInfoFound) {
			for (localVar in locals) {
				if (localVar != null && !localVar.isEnd) {
					endVar(localVar, codeSize - 1)
				}
			}
		}
		return DebugInfo(linesMap, resultList)
	}

	private fun addrChange(addr: Int, addrInc: Int): Int = minOf(addr + addrInc, codeSize - 1)

	private fun setLine(offset: Int, line: Int) {
		linesMap[offset] = line
	}

	/** restart_local：结束旧变量区间，以相同元数据开启新区间。 */
	private fun restartVar(regNum: Int, addr: Int) {
		val prev = locals[regNum] ?: return
		endVar(prev, addr)
		val newVar = DexLocalVar(regNum, prev.name, prev.type, prev.signature)
		startVar(newVar, addr)
	}

	private fun startVar(newVar: DexLocalVar, addr: Int) {
		val regNum = newVar.regNum
		val prev = locals[regNum]
		if (prev != null) {
			endVar(prev, addr)
		}
		newVar.start(addr)
		locals[regNum] = newVar
	}

	private fun endVar(localVar: DexLocalVar, addr: Int) {
		if (localVar.end(addr)) {
			resultList.add(localVar)
		}
	}
}
