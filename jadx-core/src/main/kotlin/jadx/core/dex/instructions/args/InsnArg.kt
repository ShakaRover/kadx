package jadx.core.dex.instructions.args

import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.InsnRemover
import jadx.core.utils.InsnUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Instruction argument.
 * Can be: register, literal, instruction or name
 */
abstract class InsnArg : Typed() {
	@JvmField protected var parentInsn: InsnNode? = null // "Null for method arguments"

	open val isRegister: Boolean get() = false
	open val isLiteral: Boolean get() = false
	open val isInsnWrap: Boolean get() = false
	open val isNamed: Boolean get() = false

	fun getParentInsn(): InsnNode? = parentInsn

	open fun setParentInsn(parentInsn: InsnNode?) {
		this.parentInsn = parentInsn
	}

	/** @return null if wrap failed */
	fun wrapInstruction(mth: MethodNode, insn: InsnNode): InsnArg? = wrapInstruction(mth, insn, true)

	/** @return null if wrap failed */
	fun wrapInstruction(mth: MethodNode, insn: InsnNode, unbind: Boolean): InsnArg? {
		val parent = parentInsn ?: return null
		if (parent === insn) {
			LOG.debug("Can't wrap instruction info itself: {}", insn)
			return null
		}
		val i = getArgIndex(parent, this)
		if (i == -1) {
			return null
		}
		if (insn.type == InsnType.MOVE && isRegister) {
			// preserve variable name for move insn (needed in `for-each` loop for iteration variable)
			val name = (this as RegisterArg).name
			if (name != null) {
				val arg = insn.getArg(0)
				if (arg.isRegister) {
					(arg as RegisterArg).setNameIfUnknown(name)
				} else if (arg.isInsnWrap) {
					val wrapInsn = (arg as InsnWrapArg).wrapInsn
					val registerArg = wrapInsn.getResult()
					if (registerArg != null) {
						registerArg.setNameIfUnknown(name)
					}
				}
			}
		}
		val resArg = insn.getResult()
		val arg = wrapInsnIntoArg(insn)
		val oldArg = parent.getArg(i)
		if (arg.getType() == ArgType.UNKNOWN) {
			// restore arg type if wrapped insn missing result
			arg.setType(oldArg.getType())
		}
		parent.setArg(i, arg)
		InsnRemover.unbindArgUsage(mth, oldArg)
		if (unbind) {
			InsnRemover.unbindArgUsage(mth, this)
		}
		if (resArg != null && !insn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
			// result not needed in wrapped insn
			InsnRemover.unbindResult(mth, insn)
			insn.setResult(null)
		}
		return arg
	}
	open fun isZeroLiteral(): Boolean = false

	fun isZeroConst(): Boolean {
		if (isZeroLiteral()) {
			return true
		}
		if (isInsnWrap) {
			val wrapInsn = (this as InsnWrapArg).wrapInsn
			if (wrapInsn.type == InsnType.CONST) {
				return wrapInsn.getArg(0).isZeroLiteral()
			}
		}
		return false
	}

	fun isFalse(): Boolean {
		if (isLiteral) {
			val litArg = this as LiteralArg
			return litArg.literal == 0L && litArg.getType() == ArgType.BOOLEAN
		}
		return false
	}

	fun isTrue(): Boolean {
		if (isLiteral) {
			val litArg = this as LiteralArg
			return litArg.literal == 1L && litArg.getType() == ArgType.BOOLEAN
		}
		return false
	}

	fun isThis(): Boolean = contains(AFlag.THIS)

	/**
	 * Return true for 'this' from other classes (often occur in anonymous classes)
	 */
	fun isAnyThis(): Boolean {
		if (contains(AFlag.THIS)) {
			return true
		}
		val wrappedInsn = unwrap()
		if (wrappedInsn != null && wrappedInsn.type == InsnType.IGET) {
			return wrappedInsn.getArg(0).isAnyThis()
		}
		return false
	}

	fun unwrap(): InsnNode? {
		if (isInsnWrap) {
			return (this as InsnWrapArg).wrapInsn
		}
		return null
	}

	fun isConst(): Boolean = isLiteral || (isInsnWrap && (this as InsnWrapArg).wrapInsn.isConstInsn())

	fun isSameConst(other: InsnArg): Boolean {
		if (isConst() && other.isConst()) {
			return this == other
		}
		return false
	}

	fun isSameVar(arg: RegisterArg?): Boolean {
		if (arg == null) {
			return false
		}
		if (isRegister) {
			return (this as RegisterArg).sameRegAndSVar(arg)
		}
		return false
	}

	fun isSameVar(ssaVar: SSAVar?): Boolean {
		if (ssaVar == null) {
			return false
		}
		if (isRegister) {
			val thisSsaVar = (this as RegisterArg).sVar
			return thisSsaVar == ssaVar
		}
		return false
	}

	fun isSameCodeVar(arg: RegisterArg?): Boolean {
		if (arg == null) {
			return false
		}
		if (isRegister) {
			return (this as RegisterArg).sameCodeVar(arg)
		}
		return false
	}

	fun isUseVar(arg: RegisterArg): Boolean = InsnUtils.containsVar(this, arg)

	protected fun <T : InsnArg> copyCommonParams(copy: T): T {
		copy.copyAttributesFrom(this)
		copy.setParentInsn(parentInsn)
		return copy
	}

	abstract fun duplicate(): InsnArg

	open fun toShortString(): String = toString()

	companion object {
		private val LOG = LoggerFactory.getLogger(InsnArg::class.java)

		fun reg(regNum: Int, type: ArgType): RegisterArg = RegisterArg(regNum, type)

		fun reg(insn: InsnData, argNum: Int, type: ArgType): RegisterArg = reg(insn.getReg(argNum), type)

		fun typeImmutableIfKnownReg(insn: InsnData, argNum: Int, type: ArgType): RegisterArg {
			if (type.isTypeKnown()) {
				return typeImmutableReg(insn.getReg(argNum), type)
			}
			return reg(insn.getReg(argNum), type)
		}

		fun typeImmutableReg(insn: InsnData, argNum: Int, type: ArgType): RegisterArg = typeImmutableReg(insn.getReg(argNum), type)

		fun typeImmutableReg(regNum: Int, type: ArgType): RegisterArg = reg(regNum, type, true)

		fun reg(regNum: Int, type: ArgType, typeImmutable: Boolean): RegisterArg {
			val reg = RegisterArg(regNum, type)
			if (typeImmutable) {
				reg.add(AFlag.IMMUTABLE_TYPE)
			}
			return reg
		}

		fun lit(literal: Long, type: ArgType): LiteralArg = LiteralArg.makeWithFixedType(literal, type)

		fun lit(insn: InsnData, type: ArgType): LiteralArg = lit(insn.literal, type)

		private fun wrap(insn: InsnNode): InsnWrapArg {
			insn.add(AFlag.WRAPPED)
			return InsnWrapArg(insn)
		}

		fun wrapInsnIntoArg(insn: InsnNode): InsnArg {
			val type = insn.type
			if (type == InsnType.CONST || type == InsnType.MOVE) {
				if (insn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
					val resArg = insn.getResult()
					val arg = wrap(insn)
					if (resArg != null) {
						arg.setType(resArg.getType())
					}
					return arg
				} else {
					val arg = insn.getArg(0)
					insn.add(AFlag.DONT_GENERATE)
					return arg
				}
			}
			return wrapArg(insn)
		}

		private fun getArgIndex(parent: InsnNode, arg: InsnArg): Int {
			val count = parent.argsCount
			for (i in 0 until count) {
				if (parent.getArg(i) === arg) {
					return i
				}
			}
			return -1
		}

		/**
		 * Prefer [wrapInsnIntoArg].
		 *
		 * This method don't support MOVE and CONST insns!
		 */
		fun wrapArg(insn: InsnNode): InsnArg {
			val resArg = insn.getResult()
			val arg = wrap(insn)
			when (insn.type) {
				InsnType.CONST, InsnType.MOVE -> throw JadxRuntimeException("Don't wrap MOVE or CONST insns: $insn")

				InsnType.CONST_STR -> {
					arg.setType(ArgType.STRING)
					if (resArg != null) {
						resArg.setType(ArgType.STRING)
					}
				}

				InsnType.CONST_CLASS -> {
					arg.setType(ArgType.CLASS)
					if (resArg != null) {
						resArg.setType(ArgType.CLASS)
					}
				}

				else -> {
					if (resArg != null) {
						arg.setType(resArg.getType())
					}
				}
			}
			return arg
		}
	}
}
