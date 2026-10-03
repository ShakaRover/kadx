package jadx.gui.device.debugger.smali

import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode

/**
 * 一个 smali 方法的调试元数据。
 *
 * **做什么**：保存方法内「代码偏移 -> 指令节点」「smali 行 -> 代码偏移」等映射，
 * 以及寄存器列表与参数寄存器起始编号，供断点定位与寄存器查看使用。
 */
class SmaliMethodNode internal constructor() {

	/** 代码偏移 -> 指令节点。 */
	private var nodes: MutableMap<Long, InsnNode>? = null

	/** 寄存器列表。 */
	private lateinit var regList: MutableList<SmaliRegister>

	/** 代码偏移 -> 在 smali 文本中的位置。 */
	private var insnPos: IntArray? = null

	/** 方法定义在 smali 文本中的位置。 */
	private var defPos: Int = 0

	/** smali 行号 -> 代码偏移。 */
	private var lineMapping: MutableMap<Int, Int> = mutableMapOf()

	/** 参数寄存器的起始编号。 */
	private var paramRegStart: Int = 0

	/** 方法寄存器总数。 */
	private var regCount: Int = 0

	/** @return 参数寄存器起始编号 */
	fun getParamRegStart(): Int = paramRegStart

	/** @return 方法寄存器总数 */
	fun getRegCount(): Int = regCount

	/**
	 * 返回「smali 行号 -> 代码偏移」的映射。
	 * 代码偏移与 [InsnNode.getOffset] 一致。
	 */
	fun getLineMapping(): Map<Int, Int> = lineMapping

	/** 初始化寄存器列表（每个寄存器记录其编号与指令总数）。 */
	fun initRegInfoList(regCount: Int, insnCount: Int) {
		regList = ArrayList(regCount)
		for (i in 0 until regCount) {
			regList.add(SmaliRegister(i, insnCount))
		}
	}

	/** 按代码偏移取 smali 文本中的位置，越界或未初始化时返回 -1。 */
	fun getInsnPos(codeOffset: Long): Int {
		val pos = insnPos
		if (pos != null && codeOffset < pos.size) {
			return pos[codeOffset.toInt()]
		}
		return -1
	}

	/** @return 方法定义位置 */
	fun getDefPos(): Int = defPos

	/** 按代码偏移取指令节点。 */
	fun getInsnNode(codeOffset: Long): InsnNode? = nodes?.get(codeOffset)

	/** @return 寄存器列表 */
	fun getRegList(): List<SmaliRegister> = regList

	internal fun setRegCount(regCount: Int) {
		this.regCount = regCount
	}

	/** 记录某 smali 行对应的代码偏移。 */
	internal fun attachLine(line: Int, codeOffset: Int) {
		lineMapping[line] = codeOffset
	}

	/**
	 * 记录指令在 smali 文本中的位置，并根据指令的读写寄存器扩展其作用域。
	 */
	internal fun setInsnInfo(codeOffset: Int, pos: Int) {
		val arr = insnPos
		if (arr != null && codeOffset < arr.size) {
			arr[codeOffset] = pos
		}
		val insn = getInsnNode(codeOffset.toLong()) ?: return
		val r = insn.result
		if (r != null) {
			regList[r.regNum].setStartOffset(codeOffset)
		}
		for (arg in insn.getArguments()) {
			if (arg is RegisterArg) {
				regList[arg.regNum].setStartOffset(codeOffset)
			}
		}
	}

	internal fun setDefPos(pos: Int) {
		defPos = pos
	}

	internal fun setParamReg(regNum: Int, name: String) {
		val r = regList[regNum]
		r.setParam(name)
	}

	internal fun setParamRegStart(paramRegStart: Int) {
		this.paramRegStart = paramRegStart
	}

	internal fun setInsnNodes(nodes: MutableMap<Long, InsnNode>, insnCount: Int) {
		this.nodes = nodes
		insnPos = IntArray(insnCount)
	}
}
