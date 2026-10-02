package jadx.core.utils

import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.ConstClassNode
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Function
import java.util.function.Predicate

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

	@JvmStatic
	fun formatOffset(offset: Int): String {
		if (offset < 0) {
			return "?"
		}
		return String.format("0x%04x", offset)
	}

	@JvmStatic
	fun insnTypeToString(type: InsnType): String = "$type  "

	@JvmStatic
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
	 * @return [jadx.core.dex.instructions.args.LiteralArg]、String、ArgType，或 null
	 */
	@JvmStatic
	fun getConstValueByArg(root: RootNode, arg: InsnArg): Any? {
		if (arg.isLiteral) {
			return arg
		}
		if (arg.isRegister) {
			val reg = arg as RegisterArg
			val parInsn = reg.getAssignInsn()
			if (parInsn == null) {
				return null
			}
			if (parInsn.getType() == InsnType.MOVE) {
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
	@JvmStatic
	fun getConstValueByInsn(root: RootNode, insn: InsnNode): Any? {
		return when (insn.getType()) {
			InsnType.CONST -> insn.getArg(0)

			InsnType.CONST_STR -> (insn as ConstStringNode).getString()

			InsnType.CONST_CLASS -> (insn as ConstClassNode).clsType

			InsnType.SGET -> {
				val f = (insn as IndexInsnNode).index as FieldInfo
				val fieldNode: FieldNode? = root.resolveField(f)
				if (fieldNode == null) {
					LOG.warn("Field {} not found", f)
					return null
				}
				val constVal: EncodedValue? = fieldNode.get(JadxAttrType.CONSTANT_VALUE)
				if (constVal != null) {
					return EncodedValueUtils.convertToConstValue(constVal)
				}
				null
			}

			else -> null
		}
	}

	@JvmStatic
	fun searchSingleReturnInsn(mth: MethodNode, test: Predicate<InsnNode>): InsnNode? {
		if (!mth.isNoCode() && mth.getPreExitBlocks().size == 1) {
			return searchInsn(mth, InsnType.RETURN, test)
		}
		return null
	}

	/**
	 * 在方法中查找指定类型且满足条件的指令（支持内联包装指令）。
	 */
	@JvmStatic
	fun searchInsn(mth: MethodNode, insnType: InsnType, test: Predicate<InsnNode>): InsnNode? {
		if (mth.isNoCode()) {
			return null
		}
		val blocks = mth.getBasicBlocks() ?: return null
		for (block in blocks) {
			for (insn in block.getInstructions()) {
				val foundInsn = recursiveInsnCheck(insn, insnType, test)
				if (foundInsn != null) {
					return foundInsn
				}
			}
		}
		return null
	}

	@JvmStatic
	fun replaceInsns(mth: MethodNode, replaceFunction: Function<InsnNode, InsnNode?>) {
		val blocks = mth.getBasicBlocks() ?: return
		for (block in blocks) {
			val insns = block.getInstructions()
			val insnsCount = insns.size
			for (i in 0 until insnsCount) {
				val insn = insns[i]
				replaceInsnsInInsn(mth, insn, replaceFunction)
				val replace = replaceFunction.apply(insn)
				if (replace != null) {
					BlockUtils.replaceInsn(mth, block, i, replace)
				}
			}
		}
	}

	@JvmStatic
	fun replaceInsnsInInsn(mth: MethodNode, insn: InsnNode, replaceFunction: Function<InsnNode, InsnNode?>) {
		val argsCount = insn.getArgsCount()
		for (i in 0 until argsCount) {
			val arg = insn.getArg(i)
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				replaceInsnsInInsn(mth, wrapInsn, replaceFunction)
				val replace = replaceFunction.apply(wrapInsn)
				if (replace != null) {
					InsnRemover.unbindArgUsage(mth, arg)
					insn.setArg(i, InsnArg.wrapInsnIntoArg(replace))
				}
			}
		}
	}

	@JvmStatic
	fun getRegFromInsn(regs: List<RegisterArg>, insnType: InsnType): RegisterArg? {
		for (reg in regs) {
			val parentInsn = reg.getParentInsn()
			if (parentInsn != null && parentInsn.getType() == insnType) {
				return reg
			}
		}
		return null
	}

	private fun recursiveInsnCheck(insn: InsnNode, insnType: InsnType, test: Predicate<InsnNode>): InsnNode? {
		if (insn.getType() == insnType && test.test(insn)) {
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

	@JvmStatic
	fun getSingleArg(insn: InsnNode?): InsnArg? {
		if (insn != null && insn.getArgsCount() == 1) {
			return insn.getArg(0)
		}
		return null
	}

	@JvmStatic
	fun checkInsnType(insn: InsnNode?, insnType: InsnType): InsnNode? {
		if (insn != null && insn.getType() == insnType) {
			return insn
		}
		return null
	}

	@JvmStatic
	fun isInsnType(insn: InsnNode?, insnType: InsnType): Boolean = insn != null && insn.getType() == insnType

	@JvmStatic
	fun getWrappedInsn(arg: InsnArg?): InsnNode? {
		if (arg != null && arg.isInsnWrap) {
			return (arg as InsnWrapArg).wrapInsn
		}
		return null
	}

	@JvmStatic
	fun isWrapped(arg: InsnArg?, insnType: InsnType): Boolean {
		if (arg != null && arg.isInsnWrap) {
			val wrapInsn = (arg as InsnWrapArg).wrapInsn
			return wrapInsn.getType() == insnType
		}
		return false
	}

	@JvmStatic
	fun dontGenerateIfNotUsed(insn: InsnNode): Boolean {
		val resArg = insn.getResult()
		if (resArg != null) {
			val ssaVar = checkNotNull(resArg.sVar)
			for (arg in ssaVar.getUseList()) {
				val parentInsn = arg.getParentInsn()
				if (parentInsn != null && !parentInsn.contains(AFlag.DONT_GENERATE)) {
					return false
				}
			}
		}
		insn.add(AFlag.DONT_GENERATE)
		return true
	}

	@JvmStatic
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

	@JvmStatic
	fun containsVar(insn: InsnNode?, arg: RegisterArg): Boolean {
		if (insn == null) {
			return false
		}
		val result = insn.getResult()
		if (result != null && result.sameRegAndSVar(arg)) {
			return true
		}
		if (insn.getArgsCount() == 0) {
			return false
		}
		for (insnArg in insn.getArguments()) {
			if (containsVar(insnArg, arg)) {
				return true
			}
		}
		return false
	}

	@JvmStatic
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

	@JvmStatic
	fun contains(insn: InsnNode?, flag: AFlag): Boolean = insn != null && insn.contains(flag)
}
