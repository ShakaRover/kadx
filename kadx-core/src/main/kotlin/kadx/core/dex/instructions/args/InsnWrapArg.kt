package kadx.core.dex.instructions.args

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.instructions.ConstStringNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.exceptions.KadxRuntimeException

/** Use [InsnArg.Companion.wrapInsnIntoArg] instead this constructor */
class InsnWrapArg internal constructor(insn: InsnNode) : InsnArg() {
	val wrapInsn: InsnNode = insn

	init {
		val result = insn.result
		type = if (result != null) result.getType() else ArgType.UNKNOWN
	}

	override val isInsnWrap: Boolean get() = true

	fun unWrapWithCopy(): InsnNode {
		val copy = wrapInsn.copyWithoutResult<InsnNode>()
		copy.remove(AFlag.WRAPPED)
		return copy
	}

	override fun setParentInsn(parentInsn: InsnNode?) {
		if (parentInsn === wrapInsn) {
			throw KadxRuntimeException("Can't wrap instruction info itself: $parentInsn")
		}
		this.parentInsn = parentInsn
	}

	override fun duplicate(): InsnArg {
		val wrapInsnCopy = wrapInsn.copyWithoutResult<InsnNode>()
		val result = wrapInsn.result
		if (result != null && wrapInsn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
			// keep same SSA var in result arg, this will break previous version, mark it for removal
			wrapInsnCopy.setResult(result.duplicate())
			wrapInsn.add(AFlag.DONT_GENERATE)
		}
		val copy = InsnWrapArg(wrapInsnCopy)
		copy.setType(type)
		return copyCommonParams(copy)
	}

	override fun hashCode(): Int = wrapInsn.hashCode()

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is InsnWrapArg) {
			return false
		}
		val thisInsn = wrapInsn
		val thatInsn = o.wrapInsn
		if (!thisInsn.isSame(thatInsn)) {
			return false
		}
		for (i in 0 until thisInsn.argsCount) {
			if (thisInsn.getArg(i) != thatInsn.getArg(i)) {
				return false
			}
		}
		return true
	}

	override fun toShortString(): String {
		if (wrapInsn.type == InsnType.CONST_STR) {
			return "(\"${(wrapInsn as ConstStringNode).string}\")"
		}
		return "(wrap $type:${wrapInsn.type})"
	}

	override fun toString(): String {
		if (wrapInsn.type == InsnType.CONST_STR) {
			return "(\"${(wrapInsn as ConstStringNode).string}\")"
		}
		return "(wrap $type:$wrapInsn)"
	}
}
