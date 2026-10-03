package jadx.core.dex.visitors.debuginfo

import jadx.api.plugins.input.data.IDebugInfo
import jadx.api.plugins.input.data.ILocalVar
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.LocalVarsDebugInfoAttr
import jadx.core.dex.attributes.nodes.RegDebugInfoAttr
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.parser.SignatureParser
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.blocks.BlockSplitter
import jadx.core.dex.visitors.ssa.SSATransform
import jadx.core.utils.exceptions.InvalidDataException
import jadx.core.utils.exceptions.JadxException
import java.util.ArrayList
import java.util.HashMap

/**
 * 解析并挂载调试信息（变量名/类型、指令行号）。
 *
 * **做什么**：读取方法的 [IDebugInfo]，把源码行号映射写入对应指令，
 * 把局部变量表里的名字/类型挂到寄存器上（[RegDebugInfoAttr]），
 * 并把整张局部变量表保存为 [LocalVarsDebugInfoAttr] 供后续 Pass 使用。
 *
 * **为什么**：调试信息是反编译可读性的关键来源；但要先做合理性校验
 * （[verifyDebugLines]），避免被篡改或压缩过的行号误导。
 *
 * **Kotlin 转换说明**：`getVarType`/`checkSignature` 是静态方法，放 companion；
 * stream 统计改为普通循环 + HashMap；`insnArr` 元素可空，如实使用 `InsnNode?`。
 */
@JadxVisitor(
	name = "Debug Info Parser",
	desc = "Attach debug information (variable names and types, instruction lines)",
	runBefore = [
		BlockSplitter::class,
		SSATransform::class,
	],
)
class DebugInfoAttachVisitor : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		try {
			val debugInfo = mth.debugInfo
			if (debugInfo != null) {
				processDebugInfo(mth, debugInfo)
			}
		} catch (e: InvalidDataException) {
			mth.addWarnComment(e.message ?: "")
		} catch (e: Exception) {
			mth.addWarnComment("Failed to parse debug info", e)
		}
	}

	private fun processDebugInfo(mth: MethodNode, debugInfo: IDebugInfo) {
		val insnArr = checkNotNull(mth.instructions)
		attachSourceLines(mth, debugInfo.sourceLineMapping, insnArr)
		attachDebugInfo(mth, debugInfo.localVars, insnArr)
		setMethodSourceLine(mth, insnArr)
	}

	private fun attachSourceLines(mth: MethodNode, lineMapping: Map<Int, Int>, insnArr: Array<InsnNode?>) {
		if (lineMapping.isEmpty()) {
			return
		}
		for ((key, value) in lineMapping) {
			try {
				val insn = insnArr[key]
				if (insn != null) {
					insn.setSourceLine(value)
				}
			} catch (e: Exception) {
				mth.addWarnComment("Error attach source line", e)
				return
			}
		}
		val ignoreReason = verifyDebugLines(lineMapping)
		if (ignoreReason != null) {
			mth.addDebugComment("Don't trust debug lines info. $ignoreReason")
		} else {
			mth.add(AFlag.USE_LINES_HINTS)
		}
	}

	private fun verifyDebugLines(lineMapping: Map<Int, Int>): String? {
		// 找方法内最小行号
		var minLine = Int.MAX_VALUE
		for (v in lineMapping.values) {
			if (v < minLine) {
				minLine = v
			}
		}
		if (minLine < 3) {
			return "Lines numbers was adjusted: min line is $minLine"
		}

		// 统计重复行；3 是允许的最大重复次数
		// （带索引的 for 循环可能出现 3 条同行的指令）
		val lineCount = HashMap<Int, Int>()
		for (l in lineMapping.values) {
			lineCount[l] = (lineCount[l] ?: 0) + 1
		}
		val repeatingLines = ArrayList<Map.Entry<Int, Int>>()
		for (p in lineCount.entries) {
			if (p.value > 3) {
				repeatingLines.add(p)
			}
		}
		if (repeatingLines.isNotEmpty()) {
			return "Repeating lines: $repeatingLines"
		}
		return null
	}

	private fun attachDebugInfo(mth: MethodNode, localVars: List<ILocalVar>, insnArr: Array<InsnNode?>) {
		if (localVars.isEmpty()) {
			return
		}
		for (v in localVars) {
			val regNum = v.regNum
			var start = v.startOffset
			val end = v.endOffset

			val type = getVarType(mth, v)
			val debugInfoAttr = RegDebugInfoAttr(type, v.name)
			if (start <= 0) {
				// 附加到方法参数
				val thisArg = mth.getThisArg()
				if (thisArg != null) {
					attachDebugInfo(thisArg, debugInfoAttr, regNum)
				}
				for (arg in mth.argRegs) {
					attachDebugInfo(arg, debugInfoAttr, regNum)
				}
				start = 0
			}
			for (i in start..end) {
				val insn = insnArr[i]
				if (insn == null) {
					continue
				}
				var count = 0
				for (arg in insn.getArguments()) {
					count += attachDebugInfo(arg, debugInfoAttr, regNum)
				}
				if (count != 0) {
					// 已附加到参数，不再附加到结果
					continue
				}
				attachDebugInfo(insn.result, debugInfoAttr, regNum)
			}
		}

		mth.addAttr(LocalVarsDebugInfoAttr(localVars))
	}

	private fun attachDebugInfo(arg: InsnArg?, debugInfoAttr: RegDebugInfoAttr, regNum: Int): Int {
		if (arg is RegisterArg) {
			if (regNum == arg.regNum) {
				arg.addAttr(debugInfoAttr)
				return 1
			}
		}
		return 0
	}

	/** 设置方法源码行号（取第一条指令的行号减一）。 */
	private fun setMethodSourceLine(mth: MethodNode, insnArr: Array<InsnNode?>) {
		for (insn in insnArr) {
			if (insn != null) {
				val line = insn.getSourceLine()
				if (line != 0) {
					mth.setSourceLine(line - 1)
					return
				}
			}
		}
	}

	companion object {
		/** 从局部变量调试信息推导变量类型（泛型签名优先）。 */
		fun getVarType(mth: MethodNode, v: ILocalVar): ArgType {
			val type = ArgType.parse(v.type)
			val sign = v.signature ?: return type
			try {
				val gType = checkNotNull(SignatureParser(sign).consumeType())
				val expandedType = mth.root().getTypeUtils().expandTypeVariables(mth, gType)
				if (checkSignature(mth, type, expandedType)) {
					return expandedType
				}
			} catch (e: Exception) {
				mth.addWarnComment("Can't parse signature for local variable: $sign", e)
			}
			return type
		}

		private fun checkSignature(mth: MethodNode, type: ArgType, gType: ArgType): Boolean {
			val apply: Boolean
			val el = gType.getArrayRootElement()
			if (el.isGeneric()) {
				if (type.getArrayRootElement().getObject() != el.getObject()) {
					mth.addWarnComment("Generic types in debug info not equals: $type != $gType")
				}
				apply = true
			} else {
				apply = el.isGenericType()
			}
			return apply
		}
	}
}
