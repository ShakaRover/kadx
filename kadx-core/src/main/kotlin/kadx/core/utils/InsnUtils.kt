package kadx.core.utils

import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.instructions.ConstClassNode
import kadx.core.dex.instructions.ConstStringNode
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 指令（[InsnNode]）查询与替换工具集。
 *
 * **用途**：按类型/条件查找指令、展开包装指令、常量提取、按引用比较变量等，
 * 是各 Pass 的高频辅助类。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic`。
 */
object InsnUtils {

	private val LOG: Logger = LoggerFactory.getLogger(InsnUtils::class.java)

	fun formatOffset(offset: Int): String {
		if (offset < 0) {
			return "?"
		}
		return String.format("0x%04x", offset)
	}

	fun insnTypeToString(type: InsnType): String = "$type  "

	fun indexToString(index: Any?): String {
		if (index == null) {
			return ""
		}
		if (index is String) {
			return "\"$index\""
		}
		return index.toString()
	}

	/**
	 * 查找参数上绑定的常量。
	 *
	 * @return [kadx.core.dex.instructions.args.LiteralArg]、String、ArgType，或 null
	 */
	fun getConstValueByArg(root: RootNode, arg: InsnArg): Any? {
		if (arg.isLiteral) {
			return arg
		}
		if (arg.isRegister) {
			val reg = arg as RegisterArg
			val parInsn = reg.assignInsn
			if (parInsn == null) {
				return null
			}
			if (parInsn.type == InsnType.MOVE) {
				return getConstValueByArg(root, parInsn.getArg(0))
			}
			return getConstValueByInsn(root, parInsn)
		}
		if (arg.isInsnWrap) {
			val insn = (arg as InsnWrapArg).wrapInsn
			return getConstValueByInsn(root, insn)
		}
		return null
	}

	/**
	 * 从指令中取出常量值；不是常量时返回 null。
	 */
	fun getConstValueByInsn(root: RootNode, insn: InsnNode): Any? {
		return when (insn.type) {
			InsnType.CONST -> insn.getArg(0)

			InsnType.CONST_STR -> (insn as ConstStringNode).string

			InsnType.CONST_CLASS -> (insn as ConstClassNode).clsType

			InsnType.SGET -> {
				val f = (insn as IndexInsnNode).index as FieldInfo
				val fieldNode: FieldNode? = root.resolveField(f)
				if (fieldNode == null) {
					LOG.warn("Field {} not found", f)
					return null
				}
				val constVal: EncodedValue? = fieldNode.get(KadxAttrType.CONSTANT_VALUE)
				if (constVal != null) {
					return EncodedValueUtils.convertToConstValue(constVal)
				}
				null
			}

			else -> null
		}
	}

	fun searchSingleReturnInsn(mth: MethodNode, test: (InsnNode) -> Boolean): InsnNode? {
		if (!mth.isNoCode() && mth.preExitBlocks.size == 1) {
			return searchInsn(mth, InsnType.RETURN, test)
		}
		return null
	}

	/**
	 * 在方法中查找指定类型且满足条件的指令（支持内联包装指令）。
	 */
	fun searchInsn(mth: MethodNode, insnType: InsnType, test: (InsnNode) -> Boolean): InsnNode? {
		if (mth.isNoCode()) {
			return null
		}
		val blocks = mth.basicBlocks ?: return null
		for (block in blocks) {
			for (insn in block.instructions) {
				val foundInsn = recursiveInsnCheck(insn, insnType, test)
				if (foundInsn != null) {
					return foundInsn
				}
			}
		}
		return null
	}

	fun replaceInsns(mth: MethodNode, replaceFunction: (InsnNode) -> InsnNode?) {
		val blocks = mth.basicBlocks ?: return
		for (block in blocks) {
			val insns = block.instructions
			val insnsCount = insns.size
			for (i in 0 until insnsCount) {
				val insn = insns[i]
				replaceInsnsInInsn(mth, insn, replaceFunction)
				val replace = replaceFunction(insn)
				if (replace != null) {
					BlockUtils.replaceInsn(mth, block, i, replace)
				}
			}
		}
	}

	fun replaceInsnsInInsn(mth: MethodNode, insn: InsnNode, replaceFunction: (InsnNode) -> InsnNode?) {
		val argsCount = insn.argsCount
		for (i in 0 until argsCount) {
			val arg = insn.getArg(i)
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				replaceInsnsInInsn(mth, wrapInsn, replaceFunction)
				val replace = replaceFunction(wrapInsn)
				if (replace != null) {
					InsnRemover.unbindArgUsage(mth, arg)
					insn.setArg(i, InsnArg.wrapInsnIntoArg(replace))
				}
			}
		}
	}

	fun getRegFromInsn(regs: List<RegisterArg>, insnType: InsnType): RegisterArg? {
		for (reg in regs) {
			val parentInsn = reg.getParentInsn()
			if (parentInsn != null && parentInsn.type == insnType) {
				return reg
			}
		}
		return null
	}

	private fun recursiveInsnCheck(insn: InsnNode, insnType: InsnType, test: (InsnNode) -> Boolean): InsnNode? {
		if (insn.type == insnType && test(insn)) {
			return insn
		}
		for (arg in insn.getArguments()) {
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				val foundInsn = recursiveInsnCheck(wrapInsn, insnType, test)
				if (foundInsn != null) {
					return foundInsn
				}
			}
		}
		return null
	}

	fun getSingleArg(insn: InsnNode?): InsnArg? {
		if (insn != null && insn.argsCount == 1) {
			return insn.getArg(0)
		}
		return null
	}

	fun checkInsnType(insn: InsnNode?, insnType: InsnType): InsnNode? {
		if (insn != null && insn.type == insnType) {
			return insn
		}
		return null
	}

	fun isInsnType(insn: InsnNode?, insnType: InsnType): Boolean = insn != null && insn.type == insnType

	fun getWrappedInsn(arg: InsnArg?): InsnNode? {
		if (arg != null && arg.isInsnWrap) {
			return (arg as InsnWrapArg).wrapInsn
		}
		return null
	}

	fun isWrapped(arg: InsnArg?, insnType: InsnType): Boolean {
		if (arg != null && arg.isInsnWrap) {
			val wrapInsn = (arg as InsnWrapArg).wrapInsn
			return wrapInsn.type == insnType
		}
		return false
	}

	fun dontGenerateIfNotUsed(insn: InsnNode): Boolean {
		val resArg = insn.result
		if (resArg != null) {
			val ssaVar = checkNotNull(resArg.sVar)
			for (arg in ssaVar.useList) {
				val parentInsn = arg.getParentInsn()
				if (parentInsn != null && !parentInsn.contains(AFlag.DONT_GENERATE)) {
					return false
				}
			}
		}
		insn.add(AFlag.DONT_GENERATE)
		return true
	}

	fun <T : InsnArg> containsVar(list: List<T>?, arg: RegisterArg): Boolean {
		if (list == null || list.isEmpty()) {
			return false
		}
		for (insnArg in list) {
			if (insnArg === arg || arg.sameRegAndSVar(insnArg)) {
				return true
			}
		}
		return false
	}

	fun containsVar(insn: InsnNode?, arg: RegisterArg): Boolean {
		if (insn == null) {
			return false
		}
		val result = insn.result
		if (result != null && result.sameRegAndSVar(arg)) {
			return true
		}
		if (insn.argsCount == 0) {
			return false
		}
		for (insnArg in insn.getArguments()) {
			if (containsVar(insnArg, arg)) {
				return true
			}
		}
		return false
	}

	fun containsVar(insnArg: InsnArg, arg: RegisterArg): Boolean {
		if (insnArg.isRegister) {
			return (insnArg as RegisterArg).sameRegAndSVar(arg)
		}
		if (insnArg.isInsnWrap) {
			val wrapInsn = (insnArg as InsnWrapArg).wrapInsn
			return containsVar(wrapInsn, arg)
		}
		return false
	}

	fun contains(insn: InsnNode?, flag: AFlag): Boolean = insn != null && insn.contains(flag)
}
