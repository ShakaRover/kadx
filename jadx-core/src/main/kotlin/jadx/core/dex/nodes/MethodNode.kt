package jadx.core.dex.nodes

import jadx.api.ICodeInfo
import jadx.api.JavaMethod
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.api.metadata.annotations.VarNode
import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IDebugInfo
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.utils.CodeUtils.getLineEndForPos
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.attributes.nodes.MethodOverrideAttr
import jadx.core.dex.attributes.nodes.MethodThrowsAttr
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.InsnDecoder
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.utils.TypeUtils
import jadx.core.dex.regions.Region
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.visitors.InitCodeVariables.Companion.initCodeVar
import jadx.core.utils.Utils.collectionMap
import jadx.core.utils.Utils.listToString
import jadx.core.utils.exceptions.DecodeException
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.Collections

class MethodNode(
	val parentClass: ClassNode,
	mthData: IMethodData,
) : NotificationAttrNode(),
	IMethodDetails,
	ILoadable,
	ICodeNode,
	Comparable<MethodNode> {
	companion object {
		private val LOG = LoggerFactory.getLogger(MethodNode::class.java)
		private val EMPTY_INSN_ARRAY = arrayOfNulls<InsnNode>(0)

		fun build(classNode: ClassNode, methodData: IMethodData): MethodNode {
			val methodNode = MethodNode(classNode, methodData)
			methodNode.addAttrs(methodData.getAttributes())
			return methodNode
		}
	}

	val mthInfo: MethodInfo = MethodInfo.fromRef(parentClass.root(), mthData.getMethodRef())
	var accFlags: AccessInfo = AccessInfo(mthData.getAccessFlags(), AccessInfo.AFType.METHOD)

	lateinit var retType: ArgType

	@get:JvmName("argTypesValue")
	lateinit var argTypes: List<ArgType>

	@get:JvmName("typeParametersValue")
	lateinit var typeParameters: List<ArgType>

	private val codeReader: ICodeReader?

	@get:JvmName("insnsCountValue")
	val insnsCount: Int
	private var noCode: Boolean

	init {
		val reader = mthData.getCodeReader()
		if (reader == null) {
			noCode = true
			codeReader = null
			insnsCount = 0
		} else {
			noCode = false
			codeReader = reader.copy()
			insnsCount = reader.getUnitsCount()
		}
		retType = mthInfo.returnType
		argTypes = mthInfo.argumentsTypes
		typeParameters = emptyList()
		unload()
	}

	private var regsCount: Int = 0
	private var argsStartReg: Int = 0
	private var loaded: Boolean = false

	private var thisArg: RegisterArg? = null
	private var argsList: List<RegisterArg>? = null
	var instructions: Array<InsnNode?>? = null
	var blocks: List<BlockNode>? = null
	private var blocksMaxCId: Int = 0
	var enterBlock: BlockNode? = null
	var exitBlock: BlockNode? = null
	private var sVars: MutableList<SSAVar> = ArrayList()
	private var exceptionHandlers: MutableList<ExceptionHandler> = ArrayList()
	private var loops: List<LoopInfo> = emptyList()
	var region: Region? = null

	private var useIn: List<MethodNode> = emptyList()
	private var unresolvedUsed: List<MethodInfo> = emptyList()
	private var methodsUsed: MutableSet<MethodNode> = HashSet()
	private var callsSelf: Boolean = false
	var javaNode: JavaMethod? = null

	override fun unload() {
		loaded = false
		thisArg = null
		argsList = null
		sVars = ArrayList()
		instructions = null
		blocks = null
		blocksMaxCId = 0
		enterBlock = null
		exitBlock = null
		region = null
		exceptionHandlers = ArrayList()
		loops = emptyList()
		unloadAttributes()
	}

	fun updateTypes(argTypes: List<ArgType>, retType: ArgType) {
		this.argTypes = argTypes
		this.retType = retType
	}

	fun updateTypeParameters(typeParameters: List<ArgType>) {
		this.typeParameters = typeParameters
	}

	override fun load() {
		if (loaded) return
		try {
			loaded = true
			if (noCode) {
				regsCount = 0
				initArguments(argTypes)
				return
			}
			regsCount = codeReader!!.getRegistersCount()
			argsStartReg = codeReader!!.getArgsStartReg()
			initArguments(argTypes)
			if (contains(AType.JADX_ERROR)) {
				instructions = EMPTY_INSN_ARRAY
			} else {
				val decoder = InsnDecoder(this)
				instructions = decoder.process(codeReader!!)
			}
		} catch (e: Exception) {
			if (!noCode) {
				unload()
				noCode = true
				load()
				noCode = false
			}
			throw DecodeException(this, "Load method exception: ${e.javaClass.simpleName}: ${e.message}", e)
		}
	}

	fun reload() {
		unload()
		try {
			load()
		} catch (e: DecodeException) {
			throw JadxRuntimeException("Failed to reload method ${javaClass.name}.${getName()}")
		}
	}

	private fun initArguments(args: List<ArgType>) {
		var pos = getArgsStartPos(args)
		val typeUtils = root().typeUtils
		if (accFlags.isStatic()) {
			thisArg = null
		} else {
			val thisClsType = typeUtils.expandTypeVariables(this, parentClass.getType())
			val arg = InsnArg.reg(pos++, thisClsType)
			arg.add(AFlag.THIS)
			arg.add(AFlag.IMMUTABLE_TYPE)
			thisArg = arg
		}
		if (args.isEmpty()) {
			argsList = emptyList()
			return
		}
		val list = ArrayList<RegisterArg>(args.size)
		var p = pos
		for (argType in args) {
			val expandedType = typeUtils.expandTypeVariables(this, argType)
			val regArg = InsnArg.reg(p, expandedType)
			regArg.add(AFlag.METHOD_ARGUMENT)
			regArg.add(AFlag.IMMUTABLE_TYPE)
			list.add(regArg)
			p += argType.getRegCount()
		}
		argsList = list
	}

	private fun getArgsStartPos(args: List<ArgType>): Int {
		if (noCode) return 0
		if (argsStartReg != -1) return argsStartReg
		var pos = regsCount
		for (arg in args) {
			pos -= arg.getRegCount()
		}
		if (!accFlags.isStatic()) {
			pos--
		}
		return pos
	}

	override fun getArgTypes(): List<ArgType> {
		if (argTypes == null) {
			throw JadxRuntimeException("Method generic types not initialized: $this")
		}
		return argTypes
	}

	fun updateArgTypes(newArgTypes: List<ArgType>, comment: String) {
		addDebugComment("$comment, original types: ${getArgTypes()}")
		argTypes = Collections.unmodifiableList(newArgTypes)
		initArguments(newArgTypes)
	}

	fun containsGenericArgs(): Boolean = mthInfo.argumentsTypes != getArgTypes()

	override fun getReturnType(): ArgType = retType

	fun updateReturnType(type: ArgType) {
		retType = type
	}

	fun isVoidReturn(): Boolean = mthInfo.returnType == ArgType.VOID

	fun collectArgNodes(): List<VarNode> {
		val codeInfo: ICodeInfo = getTopParentClass().getCode()
		val mthDefPos = getDefPosition()
		val lineEndPos = getLineEndForPos(codeInfo.codeStr, mthDefPos)
		val argsCount = mthInfo.argsCount
		val args = ArrayList<VarNode>(argsCount)
		codeInfo.codeMetadata.searchDown(mthDefPos) { pos, ann ->
			if (pos > lineEndPos) return@searchDown true
			if (ann is NodeDeclareRef) {
				val declRef = ann.node
				if (declRef is VarNode) {
					if (declRef.mth != this) return@searchDown true
					args.add(declRef)
				}
			}
			null
		}
		if (args.size != argsCount) {
			LOG.warn("Incorrect args count, expected: {}, got: {}", argsCount, args.size)
		}
		return args
	}

	fun getArgRegs(): List<RegisterArg> {
		if (argsList == null) {
			throw JadxRuntimeException("Method arg registers not loaded: $this, class status: ${parentClass.getTopParentClass().state}")
		}
		return argsList!!
	}

	fun getAllArgRegs(): List<RegisterArg> {
		val argRegs = getArgRegs()
		if (thisArg != null) {
			val list = ArrayList<RegisterArg>(argRegs.size + 1)
			list.add(thisArg!!)
			list.addAll(argRegs)
			return list
		}
		return argRegs
	}

	fun getThisArg(): RegisterArg? = thisArg

	fun skipFirstArgument() {
		add(AFlag.SKIP_FIRST_ARG)
	}

	override fun getTypeParameters(): List<ArgType> = typeParameters

	fun getName(): String = mthInfo.name

	fun getAlias(): String = mthInfo.alias

	override fun getDeclaringClass(): ClassNode? = parentClass

	fun getTopParentClass(): ClassNode = parentClass.getTopParentClass()

	fun isNoCode(): Boolean = noCode

	fun unloadInsnArr() {
		instructions = null
	}

	fun initBasicBlocks() {
		blocks = ArrayList()
	}

	fun finishBasicBlocks() {
		blocks = jadx.core.utils.Utils.lockList(blocks!!)
		loops = jadx.core.utils.Utils.lockList(loops as MutableList<LoopInfo>)
		for (block in blocks!!) {
			block.lock()
		}
	}

	// 原 Java 方法可能返回 null（块处理前），调用方（如 DebugChecks）会判空，故保留可空返回
	fun getBasicBlocks(): List<BlockNode>? = blocks

	fun setBasicBlocks(blocks: List<BlockNode>) {
		this.blocks = blocks
		updateBlockPositions()
	}

	fun updateBlockPositions() {
		BlockNode.updateBlockPositions(blocks!!)
	}

	fun getNextBlockCId(): Int = blocksMaxCId++

	fun getPreExitBlocks(): List<BlockNode> = exitBlock!!.predecessors

	fun isPreExitBlock(block: BlockNode): Boolean {
		val successors = block.successors
		if (successors.size == 1) {
			return successors[0] == exitBlock
		}
		return exitBlock!!.predecessors.contains(block)
	}

	fun resetLoops() {
		loops = ArrayList()
	}

	fun registerLoop(loop: LoopInfo) {
		if (loops.isEmpty()) {
			loops = ArrayList(5)
		}
		loop.id = loops.size
		(loops as MutableList<LoopInfo>).add(loop)
	}

	fun getLoopForBlock(block: BlockNode): LoopInfo? {
		if (loops.isEmpty()) return null
		for (loop in loops) {
			if (loop.loopBlocks.contains(block)) {
				return loop
			}
		}
		return null
	}

	fun getAllLoopsForBlock(block: BlockNode): List<LoopInfo> {
		if (loops.isEmpty()) return emptyList()
		val list = ArrayList<LoopInfo>(loops.size)
		for (loop in loops) {
			if (loop.loopBlocks.contains(block)) {
				list.add(loop)
			}
		}
		return list
	}

	fun getLoopsCount(): Int = loops.size

	fun getLoops(): Iterable<LoopInfo> = loops

	fun addExceptionHandler(handler: ExceptionHandler): ExceptionHandler {
		if (exceptionHandlers.isEmpty()) {
			exceptionHandlers = ArrayList(2)
		}
		exceptionHandlers.add(handler)
		return handler
	}

	fun clearExceptionHandlers(): Boolean = exceptionHandlers.removeIf { it.isRemoved() }

	fun getExceptionHandlers(): Iterable<ExceptionHandler> = exceptionHandlers

	fun isNoExceptionHandlers(): Boolean = exceptionHandlers.isEmpty()

	fun getExceptionHandlersCount(): Int = exceptionHandlers.size

	override fun getThrows(): List<ArgType> {
		val throwsAttr = get(AType.METHOD_THROWS)
		if (throwsAttr != null) {
			return collectionMap(throwsAttr.list, java.util.function.Function { s: String -> ArgType.`object`(s) })
		}
		val exceptionsAttr = get(JadxAttrType.EXCEPTIONS)
		if (exceptionsAttr != null) {
			return collectionMap(exceptionsAttr.list, java.util.function.Function { s: String -> ArgType.`object`(s) })
		}
		return emptyList()
	}

	fun isArgsOverloaded(): Boolean {
		val thisMthInfo = mthInfo
		for (method in parentClass.methods) {
			if (method == this) continue
			if (method.mthInfo.isOverloadedBy(thisMthInfo)) {
				return true
			}
		}
		return root().methodUtils.isMethodArgsOverloaded(parentClass.classInfo.type, thisMthInfo)
	}

	fun isConstructor(): Boolean = accFlags.isConstructor() && mthInfo.isConstructor()

	fun isDefaultConstructor(): Boolean {
		if (!isConstructor()) return false
		var defaultArgCount = 0
		if (parentClass.classInfo.isInner && !parentClass.accessFlags.isStatic()) {
			val outerCls = parentClass.parentClass
			if (argsList != null && argsList!!.isNotEmpty() && argsList!![0].getInitType() == outerCls.classInfo.type) {
				defaultArgCount = 1
			}
		}
		return argsList == null || argsList!!.size == defaultArgCount
	}

	fun getRegsCount(): Int = regsCount

	fun getArgsStartReg(): Int = argsStartReg

	fun makeSyntheticRegArg(type: ArgType): RegisterArg {
		val arg = InsnArg.reg(0, type)
		arg.add(AFlag.SYNTHETIC)
		val ssaVar = makeNewSVar(arg)
		initCodeVar(ssaVar)
		ssaVar.setType(type)
		return arg
	}

	fun makeSyntheticRegArg(type: ArgType, name: String): RegisterArg {
		val arg = makeSyntheticRegArg(type)
		arg.name = name
		return arg
	}

	fun makeNewSVar(assignArg: RegisterArg): SSAVar {
		val regNum = assignArg.regNum
		return makeNewSVar(regNum, getNextSVarVersion(regNum), assignArg)
	}

	fun makeNewSVar(regNum: Int, version: Int, assignArg: RegisterArg): SSAVar {
		val ssaVar = SSAVar(regNum, version, assignArg)
		if (sVars.isEmpty()) {
			sVars = ArrayList()
		}
		sVars.add(ssaVar)
		return ssaVar
	}

	private fun getNextSVarVersion(regNum: Int): Int {
		var v = -1
		for (sVar in sVars) {
			if (sVar.regNum == regNum) {
				v = maxOf(v, sVar.version)
			}
		}
		return v + 1
	}

	fun removeSVar(ssaVar: SSAVar) {
		sVars.remove(ssaVar)
	}

	fun getSVars(): List<SSAVar> = sVars

	override fun getRawAccessFlags(): Int = accFlags.rawValue()

	override var accessFlags: AccessInfo
		get() = accFlags
		set(value) {
			accFlags = value
		}

	override fun root(): RootNode = parentClass.root()

	override fun typeName(): String = "method"

	override fun getInputFileName(): String? = parentClass.inputFileName

	override fun getMethodInfo(): MethodInfo = mthInfo

	fun getMethodCodeOffset(): Long = if (noCode) 0 else codeReader!!.getCodeOffset().toLong()

	fun getDebugInfo(): IDebugInfo? = if (noCode) null else codeReader!!.getDebugInfo()

	fun ignoreMethod() {
		add(AFlag.DONT_GENERATE)
		noCode = true
	}

	override fun rename(newName: String) {
		val overrideAttr = get(AType.METHOD_OVERRIDE)
		if (overrideAttr != null) {
			for (relatedMth in overrideAttr.relatedMthNodes) {
				relatedMth.mthInfo.alias = newName
			}
		} else {
			mthInfo.alias = newName
		}
	}

	fun countInsns(): Long {
		if (instructions != null) {
			return instructions!!.size.toLong()
		}
		if (blocks != null) {
			var sum = 0L
			for (block in blocks!!) {
				sum += block.instructions.size.toLong()
			}
			return sum
		}
		return -1
	}

	fun getInsnsCount(): Int = insnsCount

	fun getCodeStr(): String = jadx.api.utils.CodeUtils.extractMethodCode(this, getTopParentClass().getCode())

	override fun isVarArg(): Boolean = accFlags.isVarArgs()

	fun isLoaded(): Boolean = loaded

	fun getCodeReader(): ICodeReader? = codeReader

	// 协变返回类型：保留 Java 原 API 的 List<MethodNode>
	override fun getUseIn(): List<MethodNode> = useIn

	fun setUseIn(useIn: List<MethodNode>) {
		this.useIn = useIn
		for (methodUsedIn in useIn) {
			methodUsedIn.addUsed(this)
		}
	}

	fun addUsed(used: MethodNode?) {
		if (used != null) {
			methodsUsed.add(used)
		}
	}

	fun setUsed(methodsUsed: List<MethodNode>) {
		this.methodsUsed = HashSet(methodsUsed)
	}

	fun getUsed(): Set<MethodNode> {
		removeInvalidMethodsUsed()
		return methodsUsed
	}

	fun getUnresolvedUsed(): List<MethodInfo> = unresolvedUsed

	fun setUnresolvedUsed(unresolvedUsed: List<MethodInfo>) {
		this.unresolvedUsed = unresolvedUsed
	}

	fun setCallsSelf(callsSelf: Boolean) {
		this.callsSelf = callsSelf
	}

	fun callsSelf(): Boolean = callsSelf

	private fun removeInvalidMethodsUsed() {
		methodsUsed.removeIf { !it.useIn.contains(this) }
	}

	override fun getAnnType() = ICodeAnnotation.AnnType.METHOD

	override fun hashCode(): Int = mthInfo.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is MethodNode) return false
		return mthInfo == other.mthInfo
	}

	override fun compareTo(o: MethodNode): Int = mthInfo.compareTo(o.mthInfo)

	override fun toAttrString(): String = super.toAttrString() + " (m)"

	override fun toString(): String = "$parentClass.${mthInfo.name}(${listToString(argTypes)}):$retType"
}
