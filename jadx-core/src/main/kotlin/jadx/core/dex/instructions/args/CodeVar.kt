package jadx.core.dex.instructions.args

import jadx.api.metadata.annotations.VarNode

class CodeVar {
	var name: String? = null
	var type: ArgType? = null // before type inference can be null and set only for immutable types
	var ssaVars: List<SSAVar> = emptyList()
	var isFinal = false
	var isThis = false
	var isDeclared = false
	var cachedVarNode: VarNode? = null // set and used at codegen stage

	fun addSsaVar(ssaVar: SSAVar) {
		if (ssaVars.isEmpty()) {
			ssaVars = ArrayList(3)
		}
		val list = ssaVars as MutableList<SSAVar>
		if (!list.contains(ssaVar)) {
			list.add(ssaVar)
		}
	}

	fun getAnySsaVar(): SSAVar {
		if (ssaVars.isEmpty()) {
			throw IllegalStateException("CodeVar without SSA variables attached: $this")
		}
		return ssaVars[0]
	}

	/**
	 * Merge flags with OR operator
	 */
	fun mergeFlagsFrom(other: CodeVar) {
		if (other.isDeclared) {
			isDeclared = true
		}
		if (other.isThis) {
			isThis = true
		}
		if (other.isFinal) {
			isFinal = true
		}
	}

	override fun toString(): String = (if (isFinal) "final " else "") + type + ' ' + name

	companion object {
		@JvmStatic
		fun fromMthArg(mthArg: RegisterArg, linkRegister: Boolean): CodeVar {
			val cv = CodeVar()
			cv.type = mthArg.getInitType()
			cv.name = mthArg.name
			cv.isThis = mthArg.isThis()
			cv.isDeclared = true
			cv.isThis = mthArg.isThis()
			if (linkRegister) {
				cv.ssaVars = listOf(SSAVar(mthArg.regNum, 0, mthArg))
			}
			return cv
		}
	}
}
