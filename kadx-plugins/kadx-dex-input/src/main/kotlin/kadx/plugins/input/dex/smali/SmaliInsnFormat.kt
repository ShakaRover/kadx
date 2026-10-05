package kadx.plugins.input.dex.smali

import kadx.api.plugins.input.insns.InsnData
import kadx.plugins.input.dex.insns.DexOpcodes
import java.util.HashMap

/**
 * 操作码 → smali 格式化器注册表（单例）。
 *
 **背景**：[SmaliPrinter] 遍历指令时按原始操作码查表分发到具体 [InsnFormatter]；
 * 未注册的指令输出 `# OPCODE (?0xXXXX)` 占位行。当前只覆盖部分常用指令（TODO: complete list）。
 *
 **Kotlin 转换说明**：原 Java 的 synchronized getInstance() 懒加载单例 → Kotlin `object`
 * （首次访问时初始化，线程安全）；lambda 注册依赖 [InsnFormatter] 是 fun interface。
 */
public object SmaliInsnFormat {

	private val formatters: Map<Int, InsnFormatter> = registerFormatters()

	private fun registerFormatters(): Map<Int, InsnFormatter> {
		val map = HashMap<Int, InsnFormatter>()
		// 显式 InsnFormatter {} 构造：map 索引赋值不传播期望类型，SAM 转换无法推断
		map[DexOpcodes.NOP] = InsnFormatter { fi -> fi.codeWriter.add("nop") }
		map[DexOpcodes.SGET_OBJECT] = staticFieldInsn("sget-object")
		map[DexOpcodes.SPUT_BOOLEAN] = staticFieldInsn("sput-boolean")
		map[DexOpcodes.CONST] = constInsn("const")
		map[DexOpcodes.CONST_HIGH16] = constInsn("const/high16")
		map[DexOpcodes.CONST_STRING] = stringInsn("const-string")
		map[DexOpcodes.INVOKE_VIRTUAL] = invokeInsn("invoke-virtual")
		map[DexOpcodes.INVOKE_DIRECT] = invokeInsn("invoke-direct")
		map[DexOpcodes.INVOKE_SUPER] = invokeInsn("invoke-super")
		map[DexOpcodes.INVOKE_STATIC] = invokeInsn("invoke-static")
		map[DexOpcodes.MOVE_RESULT] = oneArgsInsn("move-result")
		map[DexOpcodes.RETURN_VOID] = noArgsInsn("return-void")
		map[DexOpcodes.GOTO] = gotoInsn("goto")
		map[DexOpcodes.GOTO_16] = gotoInsn("goto-16")
		map[DexOpcodes.MOVE] = simpleInsn("move")
		// TODO: complete list
		return map
	}

	private fun simpleInsn(name: String): InsnFormatter = { fi ->
		val code = fi.codeWriter
		code.add(name)
		val insn = fi.insn
		val regsCount = insn.regsCount
		for (i in 0 until regsCount) {
			if (i == 0) {
				code.add(' ')
			} else {
				code.add(", ")
			}
			code.add(regAt(fi, i))
		}
	}

	private fun gotoInsn(name: String): InsnFormatter = { fi -> fi.codeWriter.add(name).add(" :goto").add(fi.insn.target.toString(16)) }

	private fun staticFieldInsn(name: String): InsnFormatter = { fi -> fi.codeWriter.add(name).add(' ').add(regAt(fi, 0)).add(", ").add(field(fi)) }

	private fun constInsn(name: String): InsnFormatter = { fi -> fi.codeWriter.add(name).add(' ').add(regAt(fi, 0)).add(", ").add(literal(fi)) }

	private fun stringInsn(name: String): InsnFormatter = { fi -> fi.codeWriter.add(name).add(' ').add(regAt(fi, 0)).add(", ").add(str(fi)) }

	private fun invokeInsn(name: String): InsnFormatter = { fi ->
		val code = fi.codeWriter
		code.add(name).add(' ')
		regsList(code, fi.insn)
		code.add(", ").add(method(fi))
	}

	private fun oneArgsInsn(name: String): InsnFormatter = { fi -> fi.codeWriter.add(name).add(' ').add(regAt(fi, 0)) }

	private fun noArgsInsn(name: String): InsnFormatter = { fi -> fi.codeWriter.add(name) }

	private fun literal(fi: InsnFormatterInfo): String = "0x" + fi.insn.literal.toString(16)

	private fun str(fi: InsnFormatterInfo): String = "\"${fi.insn.indexAsString}\""

	private fun field(fi: InsnFormatterInfo): String = fi.insn.indexAsField.toString()

	private fun method(fi: InsnFormatterInfo): String = fi.insn.indexAsMethod.toString()

	private fun regsList(code: SmaliCodeWriter, insn: InsnData) {
		val argsCount = insn.regsCount
		code.add('{')
		for (i in 0 until argsCount) {
			if (i != 0) {
				code.add(", ")
			}
			code.add("v").add(insn.getReg(i))
		}
		code.add('}')
	}

	private fun regAt(fi: InsnFormatterInfo, argNum: Int): String = "v" + fi.insn.getReg(argNum)

	/**
	 * 格式化单条指令：解码后按原始操作码查表分发，未注册时输出占位注释行。
	 */
	public fun format(formatInfo: InsnFormatterInfo) {
		val insn = formatInfo.insn
		insn.decode()
		val rawOpcodeUnit = insn.rawOpcodeUnit
		val opcode = rawOpcodeUnit and 0xFF
		val insnFormatter = formatters[opcode]
		if (insnFormatter != null) {
			insnFormatter.format(formatInfo)
		} else {
			formatInfo.codeWriter.add("# ").add(insn.opcode).add(" (?0x").add(rawOpcodeUnit.toString(16)).add(')')
		}
	}

	/**
	 * 独立格式化单条指令（新建输出器），返回完整 smali 文本。
	 */
	public fun format(insn: InsnData): String {
		val formatInfo = InsnFormatterInfo(SmaliCodeWriter(), insn)
		format(formatInfo)
		return formatInfo.codeWriter.code
	}
}
