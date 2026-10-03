package jadx.core.dex.instructions.args

import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.RegDebugInfoAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.typeinference.TypeInfo
import jadx.core.utils.StringUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Comparator

class SSAVar(val regNum: Int, val version: Int, initialAssign: RegisterArg) : Comparable<SSAVar> {
	var assign: RegisterArg = initialAssign
		set(value) {
			if (field !== value) {
				field.resetSSAVar()
				field = value
			}
		}
	private val useListRef = ArrayList<RegisterArg>(2)
	private var usedInPhiRef: MutableList<PhiInsn>? = null
	val typeInfo = TypeInfo()
	private var codeVarRef: CodeVar? = null // "Set in InitCodeVariables pass"

	init {
		assign.setSVar(this)
	}

	val assignInsn: InsnNode? get() = assign.getParentInsn()

	fun getUseList(): List<RegisterArg> = useListRef

	fun getUseCount(): Int = useListRef.size

	fun getImmutableType(): ArgType? {
		if (isTypeImmutable()) {
			return assign.getInitType()
		}
		return null
	}

	fun isTypeImmutable(): Boolean = assign.contains(AFlag.IMMUTABLE_TYPE)

	fun markAsImmutable(type: ArgType) {
		assign.add(AFlag.IMMUTABLE_TYPE)
		val initType = assign.getInitType()
		if (initType != type) {
			assign.forceSetInitType(type)
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.debug("Update immutable type at var {} assign with type: {} previous type: {}", toShortString(), type, initType)
			}
		}
	}

	fun setType(type: ArgType) {
		val imType = getImmutableType()
		if (imType != null && imType != type) {
			throw JadxRuntimeException("Can't change immutable type $imType to $type for $this")
		}
		updateType(type)
	}

	fun forceSetType(type: ArgType) = updateType(type)

	private fun updateType(type: ArgType) {
		typeInfo.setType(type)
		codeVarRef?.let { it.type = type }
	}

	fun use(arg: RegisterArg) {
		val argSVar = arg.sVar
		if (argSVar != null) {
			argSVar.removeUse(arg)
		}
		arg.setSVar(this)
		useListRef.add(arg)
	}

	fun removeUse(arg: RegisterArg) {
		useListRef.removeAll { it === arg }
	}

	fun addUsedInPhi(phiInsn: PhiInsn) {
		var list = usedInPhiRef
		if (list == null) {
			list = ArrayList(1)
			usedInPhiRef = list
		}
		list.add(phiInsn)
	}

	fun removeUsedInPhi(phiInsn: PhiInsn) {
		val list = usedInPhiRef ?: return
		list.removeAll { it === phiInsn }
		if (list.isEmpty()) {
			usedInPhiRef = null
		}
	}

	fun updateUsedInPhiList() {
		usedInPhiRef = null
		for (reg in useListRef) {
			val parentInsn = reg.getParentInsn()
			if (parentInsn != null && parentInsn.getType() == InsnType.PHI) {
				addUsedInPhi(parentInsn as PhiInsn)
			}
		}
	}

	fun getOnlyOneUseInPhi(): PhiInsn? {
		val l = usedInPhiRef
		if (l != null && l.size == 1) {
			return l[0]
		}
		return null
	}

	fun getUsedInPhi(): List<PhiInsn> = usedInPhiRef ?: emptyList()

	/**
	 * Concat assign PHI insn and usedInPhi
	 */
	fun getPhiList(): List<PhiInsn> {
		val assignInsn = assign.getParentInsn()
		if (assignInsn != null && assignInsn.getType() == InsnType.PHI) {
			val assignPhi = assignInsn as PhiInsn
			val l = usedInPhiRef
			if (l == null) {
				return listOf(assignPhi)
			}
			val list = ArrayList<PhiInsn>(1 + l.size)
			list.add(assignPhi)
			list.addAll(l)
			return list
		}
		val l2 = usedInPhiRef ?: return emptyList()
		return l2
	}

	fun isAssignInPhi(): Boolean {
		val a = assignInsn
		return a != null && a.getType() == InsnType.PHI
	}

	fun isUsedInPhi(): Boolean {
		val l = usedInPhiRef
		return l != null && !l.isEmpty()
	}

	fun setName(name: String?) {
		if (name != null) {
			val cv = codeVarRef ?: throw JadxRuntimeException("CodeVar not initialized for name set in SSAVar: $this")
			cv.name = name
		}
	}

	fun getName(): String? {
		val cv = codeVarRef ?: return null
		return cv.name
	}

	val codeVar: CodeVar get() = codeVarRef ?: throw JadxRuntimeException("Code variable not set in $this")

	fun setCodeVar(codeVar: CodeVar) {
		codeVarRef = codeVar
		codeVar.addSsaVar(this)
		val imType = getImmutableType()
		if (imType != null) {
			codeVar.type = imType
		}
	}

	fun resetTypeAndCodeVar() {
		if (!isTypeImmutable()) {
			updateType(ArgType.UNKNOWN)
		}
		typeInfo.getBounds().clear()
		codeVarRef = null
	}

	fun isCodeVarSet(): Boolean = codeVarRef != null

	fun getDetailedVarInfo(mth: MethodNode): String {
		val types = HashSet<ArgType>()
		var names: Set<String> = emptySet()

		val useArgs = ArrayList<RegisterArg>(1 + useListRef.size)
		useArgs.add(assign)
		useArgs.addAll(useListRef)

		if (mth.contains(AType.LOCAL_VARS_DEBUG_INFO)) {
			names = HashSet()
			for (arg in useArgs) {
				val debugInfoAttr: RegDebugInfoAttr? = arg.get(AType.REG_DEBUG_INFO)
				if (debugInfoAttr != null) {
					names.add(debugInfoAttr.name)
					types.add(debugInfoAttr.regType)
				}
			}
		}

		for (arg in useArgs) {
			val initType = arg.getInitType()
			if (initType.isTypeKnown()) {
				types.add(initType)
			}
			val type = arg.getType()
			if (type.isTypeKnown()) {
				types.add(type)
			}
		}
		val sb = StringBuilder()
		sb.append('r').append(regNum).append('v').append(version)
		if (!names.isEmpty()) {
			val orderedNames = names.sorted().joinToString(", ", "[", "]")
			sb.append(", names: ").append(orderedNames)
		}
		if (types.isNotEmpty()) {
			val orderedTypes = types.map { it.toString() }.sorted().joinToString(", ", "[", "]")
			sb.append(", types: ").append(orderedTypes)
		}
		return sb.toString()
	}

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is SSAVar) {
			return false
		}
		return regNum == o.regNum && version == o.version
	}

	override fun hashCode(): Int = 31 * regNum + version

	override fun compareTo(other: SSAVar): Int = SSA_VAR_COMPARATOR.compare(this, other)

	fun toShortString(): String = "r${regNum}v$version"

	override fun toString(): String {
		val name = getName()
		return toShortString() + (if (StringUtils.notEmpty(name)) " '$name' " else "") + ' ' + typeInfo.getType()
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(SSAVar::class.java)

		private val SSA_VAR_COMPARATOR: Comparator<SSAVar> =
			Comparator.comparingInt<SSAVar>({ it.regNum }).thenComparingInt { it.version }
	}
}
