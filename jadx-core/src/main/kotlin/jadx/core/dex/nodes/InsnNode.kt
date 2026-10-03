package jadx.core.dex.nodes

import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.AttrNode
import jadx.core.dex.attributes.nodes.LineAttrNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.utils.InsnRemover.Companion.unbindArgUsage
import jadx.core.utils.InsnUtils.formatOffset
import jadx.core.utils.Utils.listToString
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.InsnUtils.containsVar as insnContainsVar

open class InsnNode(
	// Java 子类直接访问 protected 字段 insnType/offset，故用 @JvmField 暴露字段（不生成 getter/setter）
	@JvmField protected val insnType: InsnType,
	argsCount: Int = 0,
) : LineAttrNode() {
	@get:JvmName("resultValue")
	@set:JvmName("setResultValue")
	var result: RegisterArg? = null

	// 参数列表。显式 getter getArguments() 返回 Iterable，故属性生成的 getter 改名为 argumentsValue 以避免 JVM 同名冲突
	@get:JvmName("argumentsValue")
	val arguments: MutableList<InsnArg> = if (argsCount == 0) ArrayList() else ArrayList(argsCount)

	@JvmField
	protected var offset: Int = -1

	constructor(type: InsnType, args: List<InsnArg>) : this(type, 0) {
		this.arguments.addAll(args)
		for (arg in args) {
			attachArg(arg)
		}
	}

	companion object {
		@JvmStatic
		fun wrapArg(arg: InsnArg): InsnNode {
			val insn = InsnNode(InsnType.ONE_ARG, 1)
			insn.addArg(arg)
			return insn
		}

		// 不使用 reified，保留与 Java 静态泛型方法一致的签名，Java 调用方可直接调用 InsnNode.duplicateArg(...)
		@JvmStatic
		fun <T : InsnArg> duplicateArg(arg: T?): T? {
			if (arg == null) return null
			@Suppress("UNCHECKED_CAST")
			return arg.duplicate() as T
		}
	}

	fun setResult(res: RegisterArg?) {
		result = res
		if (res != null) {
			res.setParentInsn(this)
			val ssaVar = res.sVar
			if (ssaVar != null) {
				ssaVar.assign = res
			}
		}
	}

	open fun addArg(arg: InsnArg) {
		arguments.add(arg)
		attachArg(arg)
	}

	open fun setArg(n: Int, arg: InsnArg) {
		arguments[n] = arg
		attachArg(arg)
	}

	protected fun attachArg(arg: InsnArg) {
		arg.setParentInsn(this)
		if (arg.isRegister) {
			val reg = arg as RegisterArg
			val ssaVar = reg.sVar
			if (ssaVar != null) {
				ssaVar.use(reg)
			}
		}
	}

	fun getType(): InsnType = insnType

	fun getResult(): RegisterArg? = result

	fun getArguments(): Iterable<InsnArg> = arguments

	fun getArgList(): List<InsnArg> = arguments

	fun getArgsCount(): Int = arguments.size

	open fun getArg(n: Int): InsnArg = arguments[n]

	fun containsArg(arg: InsnArg): Boolean {
		for (a in arguments) {
			if (a === arg) return true
		}
		return false
	}

	fun containsVar(arg: RegisterArg): Boolean = insnContainsVar(arguments, arg)

	open fun replaceArg(from: InsnArg, to: InsnArg): Boolean {
		for (i in arguments.indices) {
			val arg = arguments[i]
			if (arg === from) {
				unbindArgUsage(null, arg)
				setArg(i, to)
				return true
			}
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				if (wrapInsn.replaceArg(from, to)) return true
			}
		}
		return false
	}

	protected open fun removeArg(arg: InsnArg): Boolean {
		val index = getArgIndex(arg)
		if (index == -1) return false
		removeArg(index)
		return true
	}

	open fun removeArg(index: Int): InsnArg {
		val arg = arguments[index]
		arguments.removeAt(index)
		unbindArgUsage(null, arg)
		return arg
	}

	fun getArgIndex(arg: InsnArg): Int {
		for (i in arguments.indices) {
			if (arg === arguments[i]) return i
		}
		return -1
	}

	protected fun addReg(insn: InsnData, i: Int, type: ArgType) {
		addArg(InsnArg.reg(insn, i, type))
	}

	protected fun addReg(regNum: Int, type: ArgType) {
		addArg(InsnArg.reg(regNum, type))
	}

	protected fun addLit(literal: Long, type: ArgType) {
		addArg(InsnArg.lit(literal, type))
	}

	protected fun addLit(insn: InsnData, type: ArgType) {
		addArg(InsnArg.lit(insn, type))
	}

	fun getOffset(): Int = offset

	fun setOffset(offset: Int) {
		this.offset = offset
	}

	open fun getRegisterArgs(collection: MutableCollection<RegisterArg>) {
		for (arg in arguments) {
			if (arg.isRegister) {
				collection.add(arg as RegisterArg)
			} else if (arg.isInsnWrap) {
				(arg as InsnWrapArg).wrapInsn.getRegisterArgs(collection)
			}
		}
	}

	fun isConstInsn(): Boolean = when (insnType) {
		InsnType.CONST, InsnType.CONST_STR, InsnType.CONST_CLASS -> true
		else -> false
	}

	fun isExitEdgeInsn(): Boolean = when (insnType) {
		InsnType.RETURN, InsnType.THROW, InsnType.CONTINUE, InsnType.BREAK -> true
		else -> false
	}

	fun canRemoveResult(): Boolean = when (insnType) {
		InsnType.INVOKE, InsnType.CONSTRUCTOR -> true
		else -> false
	}

	fun canReorder(): Boolean {
		if (contains(AFlag.DONT_GENERATE)) {
			return insnType != InsnType.MONITOR_EXIT
		}
		for (arg in arguments) {
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				if (!wrapInsn.canReorder()) return false
			}
		}
		return when (insnType) {
			InsnType.CONST, InsnType.CONST_STR, InsnType.CONST_CLASS,
			InsnType.CAST, InsnType.MOVE, InsnType.ARITH, InsnType.NEG,
			InsnType.CMP_L, InsnType.CMP_G, InsnType.CHECK_CAST,
			InsnType.INSTANCE_OF, InsnType.FILL_ARRAY, InsnType.FILLED_NEW_ARRAY,
			InsnType.NEW_ARRAY, InsnType.STR_CONCAT,
			-> true

			else -> false
		}
	}

	fun containsWrappedInsn(): Boolean {
		for (arg in arguments) {
			if (arg.isInsnWrap) return true
		}
		return false
	}

	/**
	 * 访问本指令及内联包装指令（不短路，返回 Unit）。
	 *
	 * 注意：需要“遇到非 null 返回值即停止”的语义时，请调用 [visitInsns] 的泛型重载，
	 * 并用显式类型参数（如 `visitInsns<Boolean> { ... }`）区分两个重载。
	 */
	open fun visitInsns(visitor: (InsnNode) -> Unit) {
		visitor(this)
		for (arg in arguments) {
			if (arg.isInsnWrap) {
				(arg as InsnWrapArg).wrapInsn.visitInsns(visitor)
			}
		}
	}

	/**
	 * 访问本指令及内联包装指令；回调返回非 null 时立即返回该值（短路）。
	 */
	fun <R> visitInsns(visitor: (InsnNode) -> R?): R? {
		val result = visitor(this)
		if (result != null) return result
		for (arg in arguments) {
			if (arg.isInsnWrap) {
				val innerInsn = (arg as InsnWrapArg).wrapInsn
				val res = innerInsn.visitInsns(visitor)
				if (res != null) return res
			}
		}
		return null
	}

	/**
	 * 访问本指令的全部参数（含内联包装指令，不短路，返回 Unit）。
	 *
	 * 需要短路语义时请使用 [visitArgs] 的泛型重载并传入显式类型参数。
	 */
	fun visitArgs(visitor: (InsnArg) -> Unit) {
		for (arg in arguments) {
			if (arg.isInsnWrap) {
				(arg as InsnWrapArg).wrapInsn.visitArgs(visitor)
			} else {
				visitor(arg)
			}
		}
	}

	/**
	 * 访问本指令的全部参数；回调返回非 null 时立即返回该值（短路）。
	 */
	fun <R> visitArgs(visitor: (InsnArg) -> R?): R? {
		for (arg in arguments) {
			val result = if (arg.isInsnWrap) {
				(arg as InsnWrapArg).wrapInsn.visitArgs(visitor)
			} else {
				visitor(arg)
			}
			if (result != null) return result
		}
		return null
	}

	open fun isSame(other: InsnNode): Boolean {
		if (this === other) return true
		if (insnType != other.insnType) return false
		if (arguments.size != other.arguments.size) return false
		for (i in arguments.indices) {
			val arg = arguments[i]
			val otherArg = other.arguments[i]
			if (arg.isInsnWrap) {
				if (!otherArg.isInsnWrap) return false
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				val otherWrapInsn = (otherArg as InsnWrapArg).wrapInsn
				if (!wrapInsn.isSame(otherWrapInsn)) return false
			}
		}
		return true
	}

	fun isDeepEquals(other: InsnNode): Boolean {
		if (this === other) return true
		return isSame(other) && result == other.result && arguments == other.arguments
	}

	protected fun <T : InsnNode> copyCommonParams(copy: T): T {
		if (copy.getArgsCount() == 0) {
			for (arg in arguments) {
				copy.addArg(arg.duplicate())
			}
		}
		copy.copyAttributesFrom(this as AttrNode)
		copy.copyLines(this)
		copy.setOffset(offset)
		return copy
	}

	override fun copyAttributesFrom(attrNode: AttrNode) {
		super.copyAttributesFrom(attrNode as InsnNode)
		addSourceLineFrom(attrNode as InsnNode)
	}

	open fun copy(): InsnNode {
		if (javaClass != InsnNode::class.java) {
			throw JadxRuntimeException("Copy method not implemented in insn class ${javaClass.simpleName}")
		}
		return copyCommonParams(InsnNode(insnType, getArgsCount()))
	}

	fun <T : InsnNode> copyWithoutResult(): T {
		@Suppress("UNCHECKED_CAST")
		return copy() as T
	}

	fun copyWithoutSsa(): InsnNode {
		val copy = copyWithoutResult<InsnNode>()
		if (result != null) {
			if (result!!.sVar == null) {
				copy.setResult(result!!.duplicate())
			} else {
				throw JadxRuntimeException("Can't copy if SSA var is set")
			}
		}
		return copy
	}

	fun copy(newReturnArg: RegisterArg): InsnNode {
		val copy = copy()
		copy.setResult(newReturnArg)
		return copy
	}

	fun copyWithNewSsaVar(mth: MethodNode): InsnNode {
		val result = result ?: throw JadxRuntimeException("Result in null")
		val regNum = result.regNum
		val resDupArg = result.duplicate(regNum, null)
		mth.makeNewSVar(resDupArg)
		return copy(resDupArg)
	}

	open fun rebindArgs() {
		val resArg = result
		if (resArg != null) {
			val ssaVar = resArg.sVar ?: throw JadxRuntimeException("No SSA var for result arg: $resArg from ${resArg.getParentInsn()}")
			ssaVar.assign = resArg
		}
		for (arg in arguments) {
			if (arg is RegisterArg) {
				val ssaVar = arg.sVar!!
				ssaVar.use(arg)
				ssaVar.updateUsedInPhiList()
			} else if (arg is InsnWrapArg) {
				arg.wrapInsn.rebindArgs()
			}
		}
	}

	fun canThrowException(): Boolean = when (insnType) {
		InsnType.RETURN, InsnType.IF, InsnType.GOTO, InsnType.MOVE,
		InsnType.MOVE_EXCEPTION, InsnType.NEG, InsnType.CONST,
		InsnType.CONST_STR, InsnType.CONST_CLASS, InsnType.CMP_L,
		InsnType.CMP_G, InsnType.NOP,
		-> false

		else -> true
	}

	fun inheritMetadata(sourceInsn: InsnNode) {
		if (insnType == InsnType.RETURN) {
			copyLines(sourceInsn)
			if (contains(AFlag.SYNTHETIC)) {
				setOffset(sourceInsn.offset)
				rewriteAttributeFrom(sourceInsn, AType.CODE_COMMENTS)
			} else {
				copyAttributeFrom(sourceInsn, AType.CODE_COMMENTS)
			}
		} else {
			copyAttributeFrom(sourceInsn, AType.CODE_COMMENTS)
			addSourceLineFrom(sourceInsn)
		}
	}

	protected fun appendArgs(sb: StringBuilder): Boolean {
		if (arguments.isEmpty()) return false
		val argsStr = listToString(arguments)
		if (argsStr.length < 120) {
			sb.append(argsStr)
			return false
		}
		val separator = "\n  "
		sb.append(separator).append(listToString(arguments, separator))
		sb.append('\n')
		return true
	}

	protected fun attributesString(): String {
		val sb = StringBuilder()
		appendAttributes(sb)
		return sb.toString()
	}

	protected fun appendAttributes(sb: StringBuilder) {
		if (!isAttrStorageEmpty()) {
			sb.append(' ').append(getAttributesString())
		}
		if (getSourceLine() != 0) {
			sb.append(" (LINE:${getSourceLine()})")
		}
	}

	protected open fun baseString(): String {
		val sb = StringBuilder()
		if (offset != -1) {
			sb.append(formatOffset(offset)).append(": ")
		}
		sb.append(insnType).append(' ')
		if (result != null) {
			sb.append(result).append(" = ")
		}
		appendArgs(sb)
		return sb.toString()
	}

	override fun toString(): String = baseString() + attributesString()
}
