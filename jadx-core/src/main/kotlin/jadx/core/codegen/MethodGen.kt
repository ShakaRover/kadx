package jadx.core.codegen

import jadx.api.CommentsLevel
import jadx.api.DecompilationMode
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.args.IntegerFormat
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.api.metadata.annotations.VarNode
import jadx.api.plugins.input.data.AccessFlags
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import jadx.core.Consts
import jadx.core.Jadx
import jadx.core.codegen.utils.CodeGenUtils
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.JadxError
import jadx.core.dex.attributes.nodes.JumpInfo
import jadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.visitors.DepthTraversal
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.CodegenException
import jadx.core.utils.exceptions.JadxOverflowException
import org.slf4j.LoggerFactory

/**
 * 方法代码生成器：渲染方法定义（签名、注解、参数）与方法体（区域或 fallback 指令序列）。
 *
 * **反编译模式**：[DecompilationMode] 决定方法体如何生成——AUTO 优先区域重构，
 * RESTRUCTURE 强制区域重构，SIMPLE 按基本块顺序输出，FALLBACK 直接 dump 指令。
 *
 * **Kotlin 转换说明**：静态方法（`addFallbackInsns`、`getFallbackMethodGen`、`getLabelName`）
 * 放入 `companion object`，供 Kotlin（DotGraphUtils / CheckRegions）以 `MethodGen.xxx(...)` 调用。
 */
class MethodGen(classGen: ClassGen, mth: MethodNode) {
	private val mth: MethodNode = mth
	val classGen: ClassGen = classGen
	private val annotationGen: AnnotationGen = classGen.annotationGen
	val nameGen: NameGen = NameGen(mth, classGen)

	val methodNode: MethodNode get() = mth

	/** 渲染方法定义（修饰符、泛型、返回类型、方法名、参数、throws、注解默认值）。 */
	fun addDefinition(code: ICodeWriter): Boolean {
		if (mth.getMethodInfo().isClassInit()) {
			code.startLine()
			code.attachDefinition(mth)
			code.add("static")
			return true
		}
		if (mth.contains(AFlag.ANONYMOUS_CONSTRUCTOR)) {
			// don't add method name and arguments
			code.startLine()
			code.attachDefinition(mth)
			return false
		}
		if (Consts.DEBUG_USAGE) {
			ClassGen.addMthUsageInfo(code, mth)
		}
		addOverrideAnnotation(code, mth)
		annotationGen.addForMethod(code, mth)

		val clsAccFlags = mth.parentClass.accessFlags
		var ai = mth.accessFlags
		// don't add 'abstract' and 'public' to methods in interface
		if (clsAccFlags.isInterface()) {
			ai = ai.remove(AccessFlags.ABSTRACT)
			ai = ai.remove(AccessFlags.PUBLIC)
		}
		// don't add 'public' for annotations
		if (clsAccFlags.isAnnotation()) {
			ai = ai.remove(AccessFlags.PUBLIC)
		}
		if (mth.getMethodInfo().hasAlias() && !ai.isConstructor()) {
			CodeGenUtils.addRenamedComment(code, mth, mth.name)
		}
		if (mth.contains(AFlag.INCONSISTENT_CODE) && mth.checkCommentsLevel(CommentsLevel.ERROR)) {
			code.startLine("/*")
			code.incIndent()
			code.startLine("Code decompiled incorrectly, please refer to instructions dump.")
			if (!mth.root().getArgs().isShowInconsistentCode) {
				if (code.isMetadataSupported()) {
					code.startLine("To view partially-correct code enable 'Show inconsistent code' option in preferences")
				} else {
					code.startLine("To view partially-correct add '--show-bad-code' argument")
				}
			}
			code.decIndent()
			code.startLine("*/")
		}

		code.startLineWithNum(mth.getSourceLine())
		code.add(ai.makeString(mth.checkCommentsLevel(CommentsLevel.INFO)))
		if (clsAccFlags.isInterface() && !mth.isNoCode() && !mth.accessFlags.isStatic()) {
			// add 'default' for method with code in interface
			code.add("default ")
		}

		if (classGen.addGenericTypeParameters(code, mth.getTypeParameters(), false)) {
			code.add(' ')
		}
		if (ai.isConstructor()) {
			code.attachDefinition(mth)
			code.add(classGen.classNode.shortName) // constructor
		} else {
			classGen.useType(code, mth.getReturnType())
			code.add(' ')
			val defMth = methodForDefinition
			code.attachDefinition(defMth)
			code.add(defMth.alias)
		}
		code.add('(')
		addMethodArguments(code)
		code.add(')')

		annotationGen.addThrows(mth, code)

		// add default value for annotation class
		if (mth.parentClass.accessFlags.isAnnotation()) {
			val def = annotationGen.getAnnotationDefaultValue(mth)
			if (def != null) {
				code.add(" default ")
				annotationGen.encodeValue(mth.root(), code, def)
			}
		}
		return true
	}

	private val methodForDefinition: MethodNode
		get() {
			val replaceAttr = mth.get(AType.METHOD_REPLACE)
			if (replaceAttr != null) {
				return replaceAttr.replaceMth
			}
			return mth
		}

	private fun addOverrideAnnotation(code: ICodeWriter, mth: MethodNode) {
		val overrideAttr = mth.get(AType.METHOD_OVERRIDE)
		if (overrideAttr == null) {
			return
		}
		if (!overrideAttr.baseMethods.contains(mth)) {
			code.startLine("@Override")
			if (mth.checkCommentsLevel(CommentsLevel.INFO)) {
				code.add(" // ")
				code.add(
					Utils.listToString(overrideAttr.overrideList, ", ") { md ->
						md.getMethodInfo().declClass.aliasFullName
					},
				)
			}
		}
		if (Consts.DEBUG) {
			code.startLine("// related by override: ")
			code.add(Utils.listToString(overrideAttr.relatedMthNodes, ", ") { m -> m.parentClass.fullName })
		}
	}

	private fun addMethodArguments(code: ICodeWriter) {
		val args = mth.argRegs
		val paramsAnnotation = mth.get(JadxAttrType.ANNOTATION_MTH_PARAMETERS)
		var argNum = -1
		val lastArgNum = args.size - 1
		var first = true
		for (mthArg in args) {
			argNum++
			if (SkipMethodArgsAttr.isSkip(mth, argNum)) {
				continue
			}
			if (first) {
				first = false
			} else {
				code.add(", ")
			}
			val ssaVar = mthArg.sVar
			val codeVar: CodeVar
			if (ssaVar == null) {
				// abstract or interface methods
				codeVar = CodeVar.fromMthArg(mthArg, classGen.isFallbackMode)
			} else {
				codeVar = ssaVar.codeVar
			}

			// add argument annotation
			if (paramsAnnotation != null) {
				annotationGen.addForParameter(code, paramsAnnotation, argNum)
			}
			if (codeVar.isFinal) {
				code.add("final ")
			}
			val argType: ArgType
			val varType = codeVar.type
			if (varType == null || varType == ArgType.UNKNOWN) {
				// occur on decompilation errors
				argType = mthArg.getInitType()
			} else {
				argType = varType
			}
			if (argNum == lastArgNum && mth.accessFlags.isVarArgs()) {
				// change last array argument to varargs
				if (argType.isArray()) {
					val elType = argType.getArrayElement()
					classGen.useType(code, checkNotNull(elType))
					code.add("...")
				} else {
					mth.addWarnComment("Last argument in varargs method is not array: $codeVar")
					classGen.useType(code, argType)
				}
			} else {
				classGen.useType(code, argType)
			}
			code.add(' ')
			val varName = nameGen.assignArg(codeVar)
			// ssaVar is null only in fallback mode
			if (code.isMetadataSupported() && ssaVar != null) {
				code.attachDefinition(VarNode.get(mth, codeVar))
			}
			code.add(varName)
		}
	}

	@Throws(CodegenException::class)
	fun addInstructions(code: ICodeWriter) {
		val args: JadxArgs = mth.root().getArgs()
		val modeOverrideAttr = mth.topParentClass.get(AType.DECOMPILE_MODE_OVERRIDE)
		val mode: DecompilationMode
		if (modeOverrideAttr != null) {
			mode = modeOverrideAttr.mode
		} else {
			mode = args.decompilationMode
		}
		when (mode) {
			DecompilationMode.AUTO -> {
				if (classGen.isFallbackMode || mth.region == null) {
					// TODO: try simple mode first
					dumpInstructions(code)
				} else {
					addRegionInsns(code)
				}
			}

			DecompilationMode.RESTRUCTURE -> addRegionInsns(code)

			DecompilationMode.SIMPLE -> addSimpleMethodCode(code)

			DecompilationMode.FALLBACK -> addFallbackMethodCode(code, FallbackOption.FALLBACK_MODE)
		}
	}

	@Throws(CodegenException::class)
	fun addRegionInsns(code: ICodeWriter) {
		try {
			val regionGen = RegionGen(this)
			regionGen.makeRegion(code, checkNotNull(mth.region))
		} catch (e: StackOverflowError) {
			mth.addError("Method code generation error", JadxOverflowException("StackOverflow"))
			CodeGenUtils.addErrors(code, mth)
			dumpInstructions(code)
		} catch (e: BootstrapMethodError) {
			mth.addError("Method code generation error", JadxOverflowException("StackOverflow"))
			CodeGenUtils.addErrors(code, mth)
			dumpInstructions(code)
		} catch (e: Exception) {
			if (mth.parentClass.topParentClass.contains(AFlag.RESTART_CODEGEN)) {
				throw e
			}
			mth.addError("Method code generation error", e)
			CodeGenUtils.addErrors(code, mth)
			dumpInstructions(code)
		}
	}

	private fun addSimpleMethodCode(code: ICodeWriter) {
		if (mth.basicBlocks == null) {
			code.startLine("// Blocks not ready for simple mode, using fallback")
			addFallbackMethodCode(code, FallbackOption.FALLBACK_MODE)
			return
		}
		val args: JadxArgs = mth.root().getArgs()
		val tmpCode = args.codeWriterProvider.apply(args)
		try {
			tmpCode.setIndent(code.getIndent())
			generateSimpleCode(tmpCode)
			code.add(tmpCode)
		} catch (e: Exception) {
			mth.addError("Simple mode code generation failed", e)
			CodeGenUtils.addError(code, "Simple mode code generation failed", e)
			dumpInstructions(code)
		}
	}

	@Throws(CodegenException::class)
	private fun generateSimpleCode(code: ICodeWriter) {
		val helper = SimpleModeHelper(mth)
		val blocks = helper.prepareBlocks()
		val insnGen = InsnGen(this, true)
		for (block in blocks) {
			if (block.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			if (helper.isNeedStartLabel(block)) {
				code.decIndent()
				code.startLine(getLabelName(block)).add(':')
				code.incIndent()
			}
			for (insn in block.getInstructions()) {
				if (!insn.contains(AFlag.DONT_GENERATE)) {
					if (insn.getResult() != null) {
						val codeVar = checkNotNull(checkNotNull(insn.getResult()).sVar).codeVar
						if (!codeVar.isDeclared) {
							insn.add(AFlag.DECLARE_VAR)
							codeVar.isDeclared = true
						}
					}
					InsnCodeOffset.attach(code, insn)
					insnGen.makeInsn(insn, code)
					addCatchComment(code, insn, false)
					CodeGenUtils.addCodeComments(code, mth, insn)
				}
			}
			if (helper.isNeedEndGoto(block)) {
				code.startLine("goto ").add(getLabelName(block.getSuccessors()[0]))
			}
		}
	}

	fun dumpInstructions(code: ICodeWriter) {
		if (mth.checkCommentsLevel(CommentsLevel.ERROR)) {
			code.startLine("/*")
			addFallbackMethodCode(code, FallbackOption.COMMENTED_DUMP)
			code.startLine("*/")
		}
		code.startLine("throw new UnsupportedOperationException(\"Method not decompiled: ")
			.add(mth.parentClass.classInfo.aliasFullName)
			.add('.')
			.add(mth.alias)
			.add('(')
			.add(Utils.listToString(mth.getMethodInfo().argumentsTypes))
			.add("):")
			.add(mth.getMethodInfo().returnType.toString())
			.add("\");")
	}

	fun addFallbackMethodCode(code: ICodeWriter, fallbackOption: FallbackOption) {
		if (fallbackOption == FallbackOption.COMMENTED_DUMP && mth.getCommentsLevel() != CommentsLevel.DEBUG) {
			val insnCountEstimate = mth.getInsnsCount()
			if (insnCountEstimate > 200) {
				code.incIndent()
				code.startLine("Method dump skipped, instruction units count: $insnCountEstimate")
				if (code.isMetadataSupported()) {
					code.startLine("To view this dump change 'Code comments level' option to 'DEBUG'")
				} else {
					code.startLine("To view this dump add '--comments-level debug' option")
				}
				code.decIndent()
				return
			}
		}
		if (fallbackOption != FallbackOption.FALLBACK_MODE) {
			val errors = mth.getAll(AType.JADX_ERROR) // preserve error before unload
			try {
				// load original instructions
				mth.unload()
				mth.load()
				for (visitor in Jadx.fallbackPassesList) {
					DepthTraversal.visit(visitor, mth)
				}
			} catch (e: Exception) {
				LOG.error("Error reload instructions in fallback mode:", e)
				code.startLine("// Can't load method instructions: " + e.message)
				return
			} finally {
				mth.addAttr(AType.JADX_ERROR, errors)
			}
		}
		val insnArr = mth.instructions
		if (insnArr == null) {
			code.startLine("// Can't load method instructions.")
			return
		}
		code.incIndent()
		val thisArg = mth.getThisArg()
		if (thisArg != null) {
			code.startLine(nameGen.useArg(thisArg)).add(" = this;")
		}
		addFallbackInsns(code, mth, insnArr, fallbackOption)
		code.decIndent()
	}

	enum class FallbackOption {
		FALLBACK_MODE,
		BLOCK_DUMP,
		COMMENTED_DUMP,
	}

	private fun dumpInsn(
		code: ICodeWriter,
		insnGen: InsnGen,
		option: FallbackOption,
		startIndent: Int,
		prevInsn: InsnNode?,
		insn: InsnNode,
	): Boolean {
		if (insn.contains(AType.JADX_ERROR)) {
			for (error in insn.getAll(AType.JADX_ERROR)) {
				code.startLine("// ").add(error.error)
			}
			return true
		}
		if (option != FallbackOption.BLOCK_DUMP && needLabel(insn, prevInsn)) {
			code.decIndent()
			code.startLine(getLabelName(insn.getOffset()) + ':')
			code.incIndent()
		}
		if (insn.type == InsnType.NOP) {
			return true
		}
		try {
			val escapeComment = isCommentEscapeNeeded(insn, option)
			if (escapeComment) {
				code.decIndent()
				code.startLine("*/")
				code.startLine("//  ")
			} else {
				code.startLineWithNum(insn.getSourceLine())
			}
			InsnCodeOffset.attach(code, insn)
			val resArg = insn.getResult()
			if (resArg != null) {
				val varType = resArg.getInitType()
				if (varType.isTypeKnown()) {
					code.add(varType.toString()).add(' ')
				}
			}
			insnGen.makeInsn(insn, code, InsnGen.Flags.INLINE)
			if (escapeComment) {
				code.startLine("/*")
				code.incIndent()
			}
			addCatchComment(code, insn, true)
			CodeGenUtils.addCodeComments(code, mth, insn)
		} catch (e: Exception) {
			LOG.debug("Error generate fallback instruction: ", e.cause)
			code.setIndent(startIndent)
			code.startLine("// error: $insn")
		}
		return false
	}

	private fun addCatchComment(code: ICodeWriter, insn: InsnNode, raw: Boolean) {
		val catchAttr = insn.get(AType.EXC_CATCH)
		if (catchAttr == null) {
			return
		}
		code.add("     // Catch:")
		for (handler in catchAttr.handlers) {
			code.add(' ')
			classGen.useClass(code, handler.argType)
			code.add(" -> ")
			if (raw) {
				code.add(getLabelName(handler.handlerOffset))
			} else {
				code.add(getLabelName(checkNotNull(handler.getHandlerBlock())))
			}
		}
	}

	private fun isCommentEscapeNeeded(insn: InsnNode, option: FallbackOption): Boolean {
		if (option == FallbackOption.COMMENTED_DUMP) {
			if (insn.type == InsnType.CONST_STR) {
				val str = (insn as ConstStringNode).string
				return checkNotNull(str).contains("*/")
			}
		}
		return false
	}

	private fun needLabel(insn: InsnNode, prevInsn: InsnNode?): Boolean {
		if (insn.contains(AType.EXC_HANDLER)) {
			return true
		}
		if (insn.contains(AType.JUMP)) {
			// don't add label for ifs else branch
			if (prevInsn != null && prevInsn.type == InsnType.IF) {
				val jumps = insn.getAll(AType.JUMP)
				if (jumps.size == 1) {
					val jump = jumps[0]
					if (jump.src == prevInsn.getOffset() && jump.dest == insn.getOffset()) {
						val target = (prevInsn as IfNode).getTarget()
						return insn.getOffset() == target
					}
				}
			}
			return true
		}
		return false
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodGen::class.java)

		/**
		 * 返回 fallback 变体的方法代码生成器（强制 fallback 模式）。
		 */
		fun getFallbackMethodGen(mth: MethodNode): MethodGen {
			val clsGen = ClassGen(mth.parentClass, null, false, true, true, IntegerFormat.AUTO)
			return MethodGen(clsGen, mth)
		}

		fun addFallbackInsns(code: ICodeWriter, mth: MethodNode, insnArr: Array<InsnNode?>, option: FallbackOption) {
			val startIndent = code.getIndent()
			val methodGen = getFallbackMethodGen(mth)
			val insnGen = InsnGen(methodGen, true)
			var prevInsn: InsnNode? = null
			for (insn in insnArr) {
				if (insn == null) {
					continue
				}
				methodGen.dumpInsn(code, insnGen, option, startIndent, prevInsn, insn)
				prevInsn = insn
			}
		}

		fun getLabelName(block: BlockNode): String = String.format("L%d", block.cid)

		fun getLabelName(insn: IfNode): String {
			val thenBlock = insn.getThenBlock()
			if (thenBlock != null) {
				return getLabelName(thenBlock)
			}
			return getLabelName(insn.getTarget())
		}

		fun getLabelName(offset: Int): String {
			if (offset < 0) {
				return String.format("LB_%x", -offset)
			}
			return String.format("L%x", offset)
		}
	}
}
