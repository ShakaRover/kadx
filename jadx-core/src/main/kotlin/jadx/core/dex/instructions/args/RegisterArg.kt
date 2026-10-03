package jadx.core.dex.instructions.args

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxRuntimeException

class RegisterArg(val regNum: Int, type: ArgType) :
	InsnArg(),
	Named {
	private var sVarRef: SSAVar? = null

	override val isRegister: Boolean get() = true

	init {
		this.type = type // initial type, not changing, can be unknown
	}

	fun getInitType(): ArgType = type

	override fun getType(): ArgType {
		val sv = sVarRef
		if (sv != null) {
			return sv.typeInfo.getType()
		}
		return ArgType.UNKNOWN
	}

	override fun setType(newType: ArgType) {
		val sv = sVarRef ?: throw JadxRuntimeException("Can't change type for register without SSA variable: $this")
		sv.setType(newType)
	}

	fun forceSetInitType(type: ArgType) {
		this.type = type
	}

	val immutableType: ArgType? get() {
		val sv = sVarRef
		if (sv != null) {
			return sv.immutableType
		}
		return if (contains(AFlag.IMMUTABLE_TYPE)) type else null
	}

	override fun isTypeImmutable(): Boolean {
		val sv = sVarRef
		if (sv != null) {
			return sv.isTypeImmutable()
		}
		return contains(AFlag.IMMUTABLE_TYPE)
	}

	val sVar: SSAVar? get() = sVarRef

	internal fun setSVar(sVar: SSAVar) {
		this.sVarRef = sVar
	}

	fun resetSSAVar() {
		sVarRef = null
	}

	override var name: String?
		get() {
			if (isSuper()) {
				return SUPER_ARG_NAME
			}
			if (isThis()) {
				return THIS_ARG_NAME
			}
			val sv = sVarRef ?: return null
			return sv.getName()
		}

		set(value) {
			val sv = sVarRef
			if (sv != null && value != null) {
				sv.setName(value)
			}
		}

	private fun isSuper(): Boolean = contains(AFlag.SUPER)

	fun setNameIfUnknown(name: String?) {
		if (this.name == null) {
			this.name = name
		}
	}

	fun isNameEquals(arg: InsnArg): Boolean {
		val n = this.name
		if (n == null || arg !is Named) {
			return false
		}
		return n == arg.name
	}

	override fun duplicate(): RegisterArg = duplicate(regNum, getInitType(), sVarRef)

	fun duplicate(initType: ArgType): RegisterArg = duplicate(regNum, initType, sVarRef)

	fun duplicateWithNewSSAVar(mth: MethodNode): RegisterArg {
		val dup = duplicate(regNum, getInitType(), null)
		mth.makeNewSVar(dup)
		return dup
	}

	fun duplicate(regNum: Int, sVar: SSAVar?): RegisterArg = duplicate(regNum, getInitType(), sVar)

	fun duplicate(regNum: Int, initType: ArgType, sVar: SSAVar?): RegisterArg {
		val dup = RegisterArg(regNum, initType)
		if (sVar != null) {
			// only 'set' here, 'assign' or 'use' will binds later
			dup.setSVar(sVar)
		}
		return copyCommonParams(dup)
	}

	val assignInsn: InsnNode? get() {
		val sv = sVarRef ?: return null
		return sv.assign.getParentInsn()
	}

	fun equalRegisterAndType(arg: RegisterArg): Boolean = regNum == arg.regNum && type == arg.type

	fun sameRegAndSVar(arg: InsnArg): Boolean {
		if (this === arg) {
			return true
		}
		if (!arg.isRegister) {
			return false
		}
		val reg = arg as RegisterArg
		return regNum == reg.regNum && sVarRef == reg.sVar
	}

	fun sameReg(arg: InsnArg): Boolean {
		if (!arg.isRegister) {
			return false
		}
		return regNum == (arg as RegisterArg).regNum
	}

	fun sameType(arg: InsnArg): Boolean = getType() == arg.getType()

	fun sameCodeVar(arg: RegisterArg): Boolean = checkNotNull(sVarRef).codeVar === checkNotNull(arg.sVar).codeVar

	fun isLinkedToOtherSsaVars(): Boolean = checkNotNull(sVarRef).codeVar.ssaVars.size > 1

	override fun hashCode(): Int = regNum

	override fun equals(obj: Any?): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is RegisterArg) {
			return false
		}
		return regNum == obj.regNum && sVarRef == obj.sVar
	}

	override fun toShortString(): String {
		val sb = StringBuilder()
		sb.append("r").append(regNum)
		val sv = sVarRef
		if (sv != null) {
			sb.append('v').append(sv.version)
		}
		return sb.toString()
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("(r").append(regNum)
		val sv = sVarRef
		if (sv != null) {
			sb.append('v').append(sv.version)
		}
		val name = this.name
		if (name != null) {
			sb.append(" '").append(name).append('\'')
		}
		val type = if (sv != null) getType() else null
		if (type != null) {
			sb.append(' ').append(type)
		}
		val initType = getInitType()
		if (type == null || (type != initType && !type.isTypeKnown())) {
			sb.append(" I:").append(initType)
		}
		if (!isAttrStorageEmpty()) {
			sb.append(' ').append(getAttributesString())
		}
		sb.append(')')
		return sb.toString()
	}

	companion object {
		const val THIS_ARG_NAME = "this"
		const val SUPER_ARG_NAME = "super"
	}
}
