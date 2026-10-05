package kadx.core.codegen

import kadx.api.CommentsLevel
import kadx.api.ICodeWriter
import kadx.api.metadata.annotations.InsnCodeOffset
import kadx.api.metadata.annotations.VarNode
import kadx.api.plugins.input.data.AccessFlags
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.codegen.utils.CodeGenUtils
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.instructions.SwitchInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.NamedArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.regions.SwitchRegion
import kadx.core.dex.regions.SynchronizedRegion
import kadx.core.dex.regions.TryCatchRegion
import kadx.core.dex.regions.conditions.IfRegion
import kadx.core.dex.regions.loops.ForEachLoop
import kadx.core.dex.regions.loops.ForLoop
import kadx.core.dex.regions.loops.LoopRegion
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.utils.BlockUtils
import kadx.core.utils.RegionUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.CodegenException
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.LoggerFactory

/**
 * 区域（region）代码生成器：把 CFG 结构化后的区域树渲染成 Java 的 if / loop / switch / try-catch / synchronized。
 *
 * 继承 [InsnGen] 复用指令与参数渲染；区域树的 `generate` 方法会回调本类对应的 `makeXxx`。
 *
 * **Kotlin 转换说明**：`getRegion()` 等区域访问保持方法调用；`SwitchRegion.DEFAULT_CASE_KEY`
 * 是哨兵对象，比较必须用引用相等 `===`（原 Java 的 `==`）。
 */
open class RegionGen(mgen: MethodGen) : InsnGen(mgen, false) {

	@Throws(CodegenException::class)
	fun makeRegion(code: ICodeWriter, cont: IContainer) {
		declareVars(code, cont)
		cont.generate(this, code)
	}

	private fun declareVars(code: ICodeWriter, cont: IContainer) {
		val declVars = cont.get(AType.DECLARE_VARIABLES)
		if (declVars != null) {
			for (v in declVars.vars) {
				code.startLine()
				declareVar(code, v)
				code.add(';')
				CodeGenUtils.addCodeComments(code, mth, v.anySsaVar.assign)
			}
		}
	}

	@Throws(CodegenException::class)
	private fun makeRegionIndent(code: ICodeWriter, region: IContainer) {
		code.incIndent()
		makeRegion(code, region)
		code.decIndent()
	}

	@Throws(CodegenException::class)
	fun makeSimpleBlock(block: IBlock, code: ICodeWriter) {
		if (block.contains(AFlag.DONT_GENERATE)) {
			return
		}

		for (insn in block.instructions) {
			if (!insn.contains(AFlag.DONT_GENERATE)) {
				makeInsn(insn, code)
			}
		}
		val retAttr = block.get(AType.FORCE_RETURN)
		if (retAttr != null) {
			makeInsn(retAttr.returnInsn, code)
		}
	}

	@Throws(CodegenException::class)
	fun makeIf(region: IfRegion, code: ICodeWriter, newLine: Boolean) {
		if (newLine) {
			code.startLineWithNum(region.sourceLine)
		} else {
			code.attachSourceLine(region.sourceLine)
		}
		val comment = region.contains(AFlag.COMMENT_OUT)
		if (comment) {
			code.add("// ")
		}

		code.add("if (")
		ConditionGen(this).add(code, checkNotNull(region.condition))
		code.add(") {")
		if (code.isMetadataSupported()) {
			val conditionBlocks = region.conditionBlocks
			if (!conditionBlocks.isEmpty()) {
				val blockNode = conditionBlocks[0]
				val lastInsn = BlockUtils.getLastInsn(blockNode)
				InsnCodeOffset.attach(code, lastInsn)
				CodeGenUtils.addCodeComments(code, mth, lastInsn)
			}
		}
		makeRegionIndent(code, checkNotNull(region.thenRegion))
		if (comment) {
			code.startLine("// }")
		} else {
			code.startLine('}')
		}

		val els = region.elseRegion
		if (RegionUtils.notEmpty(els)) {
			code.add(" else ")
			if (connectElseIf(code, checkNotNull(els))) {
				return
			}
			code.add('{')
			makeRegionIndent(code, checkNotNull(els))
			if (comment) {
				code.startLine("// }")
			} else {
				code.startLine('}')
			}
		}
	}

	/**
	 * 把 `else { if (...) }` 连接成 `else if (...)` 链。
	 */
	@Throws(CodegenException::class)
	private fun connectElseIf(code: ICodeWriter, els: IContainer): Boolean {
		if (els.contains(AFlag.ELSE_IF_CHAIN)) {
			val elseBlock = RegionUtils.getSingleSubBlock(els)
			if (elseBlock is IfRegion) {
				declareVars(code, elseBlock)
				makeIf(elseBlock, code, false)
				return true
			}
		}
		return false
	}

	@Throws(CodegenException::class)
	fun makeLoop(region: LoopRegion, code: ICodeWriter) {
		code.startLineWithNum(region.sourceLine)
		val labelAttr = region.info.start.get(AType.LOOP_LABEL)
		if (labelAttr != null) {
			code.add(mgen.nameGen.getLoopLabel(labelAttr)).add(": ")
		}

		val condition = region.condition
		if (condition == null) {
			// infinite loop
			code.add("while (true) {")
			makeRegionIndent(code, checkNotNull(region.body))
			code.startLine('}')
			return
		}
		val condInsn = condition.firstInsn
		InsnCodeOffset.attach(code, condInsn)

		val conditionGen = ConditionGen(this)
		val type = region.type
		if (type != null) {
			if (type is ForLoop) {
				code.add("for (")
				makeInsn(type.initInsn, code, Flags.INLINE)
				code.add("; ")
				conditionGen.add(code, condition)
				code.add("; ")
				makeInsn(type.incrInsn, code, Flags.INLINE)
				code.add(") {")
				CodeGenUtils.addCodeComments(code, mth, condInsn)
				makeRegionIndent(code, checkNotNull(region.body))
				code.startLine('}')
				return
			}
			if (type is ForEachLoop) {
				code.add("for (")
				declareVar(code, type.varArg)
				code.add(" : ")
				addArg(code, type.iterableArg, false)
				code.add(") {")
				CodeGenUtils.addCodeComments(code, mth, condInsn)
				makeRegionIndent(code, checkNotNull(region.body))
				code.startLine('}')
				return
			}
			throw KadxRuntimeException("Unknown loop type: " + type.javaClass)
		}
		if (region.isConditionAtEnd) {
			code.add("do {")
			CodeGenUtils.addCodeComments(code, mth, condInsn)
			makeRegionIndent(code, checkNotNull(region.body))
			code.startLineWithNum(region.sourceLine)
			code.add("} while (")
			conditionGen.add(code, condition)
			code.add(");")
		} else {
			code.add("while (")
			conditionGen.add(code, condition)
			code.add(") {")
			CodeGenUtils.addCodeComments(code, mth, condInsn)
			makeRegionIndent(code, checkNotNull(region.body))
			code.startLine('}')
		}
	}

	@Throws(CodegenException::class)
	fun makeSynchronizedRegion(cont: SynchronizedRegion, code: ICodeWriter) {
		code.startLine("synchronized (")
		val monitorEnterInsn = cont.enterInsn
		addArg(code, monitorEnterInsn.getArg(0))
		code.add(") {")

		InsnCodeOffset.attach(code, monitorEnterInsn)
		CodeGenUtils.addCodeComments(code, mth, monitorEnterInsn)

		makeRegionIndent(code, cont.region)
		code.startLine('}')
	}

	@Throws(CodegenException::class)
	fun makeSwitch(sw: SwitchRegion, code: ICodeWriter) {
		val insn = checkNotNull(BlockUtils.getLastInsn(sw.header) as? SwitchInsn) {
			"Switch insn not found in header"
		}
		val arg = insn.getArg(0)
		code.startLine("switch (")
		addArg(code, arg, false)
		code.add(") {")
		InsnCodeOffset.attach(code, insn)
		CodeGenUtils.addCodeComments(code, mth, insn)
		code.incIndent()

		for (caseInfo in sw.cases) {
			val keys = caseInfo.keys
			val c = caseInfo.container
			for (k in keys) {
				if (k === SwitchRegion.DEFAULT_CASE_KEY) {
					code.startLine("default:")
				} else {
					code.startLine("case ")
					addCaseKey(code, arg, k)
					code.add(':')
				}
			}
			makeRegionIndent(code, c)
		}
		code.decIndent()
		code.startLine('}')
	}

	@Throws(CodegenException::class)
	private fun addCaseKey(code: ICodeWriter, arg: InsnArg, k: Any) {
		if (k is FieldNode) {
			useField(code, k.getFieldInfo(), k)
		} else if (k is FieldInfo) {
			useField(code, k, null)
		} else if (k is Int) {
			code.add(TypeGen.literalToString(k.toLong(), arg.getType(), mth, fallback))
		} else if (k is String) {
			code.add('"').add(k).add('"')
		} else {
			throw KadxRuntimeException("Unexpected key in switch: " + k.javaClass)
		}
	}

	@Throws(CodegenException::class)
	private fun useField(code: ICodeWriter, fldInfo: FieldInfo, fld: FieldNode?) {
		val isEnum: Boolean
		if (fld != null) {
			isEnum = fld.parentClass.isEnum()
		} else {
			val clsDetails = checkNotNull(root.getClsp()).getClsDetails(fldInfo.declClass.type)
			isEnum = clsDetails != null && clsDetails.hasAccFlag(AccessFlags.ENUM)
		}
		if (isEnum) {
			if (fld != null) {
				code.attachAnnotation(fld)
			}
			code.add(fldInfo.alias)
			return
		}
		staticField(code, fldInfo)
		if (fld != null && mth.checkCommentsLevel(CommentsLevel.INFO)) {
			// print original value, sometimes replaced with incorrect field
			val constVal = fld.get(KadxAttrType.CONSTANT_VALUE)
			if (constVal != null && constVal.value != null) {
				code.add(" /* ").add(constVal.value.toString()).add(" */")
			}
		}
	}

	@Throws(CodegenException::class)
	fun makeTryCatch(region: TryCatchRegion, code: ICodeWriter) {
		code.startLine("try {")

		val insn = BlockUtils.getFirstInsn(Utils.first(checkNotNull(region.tryCatchBlock).getBlocks()))
		InsnCodeOffset.attach(code, insn)
		CodeGenUtils.addCodeComments(code, mth, insn)

		makeRegionIndent(code, region.tryRegion)
		// TODO: move search of 'allHandler' to 'TryCatchRegion'
		var allHandler: ExceptionHandler? = null
		for (entry in region.catchRegions.entries) {
			val handler = entry.key
			if (handler.isCatchAll()) {
				if (allHandler != null) {
					LOG.warn("Several 'all' handlers in try/catch block in {}", mth)
				}
				allHandler = handler
			} else {
				makeCatchBlock(code, handler)
			}
		}
		if (allHandler != null) {
			makeCatchBlock(code, allHandler)
		}
		val finallyRegion = region.finallyRegion
		if (finallyRegion != null) {
			code.startLine("} finally {")
			makeRegionIndent(code, finallyRegion)
		}
		code.startLine('}')
	}

	@Throws(CodegenException::class)
	private fun makeCatchBlock(code: ICodeWriter, handler: ExceptionHandler) {
		val region = handler.getHandlerRegion()
		if (region == null) {
			return
		}
		code.startLine("} catch (")
		if (handler.isCatchAll()) {
			useClass(code, ArgType.THROWABLE)
		} else {
			val it = handler.catchTypes.iterator()
			if (it.hasNext()) {
				useClass(code, it.next())
			}
			while (it.hasNext()) {
				code.add(" | ")
				useClass(code, it.next())
			}
		}
		code.add(' ')
		val arg = handler.getArg()
		if (arg == null) {
			code.add("unknown") // throwing exception is too late at this point
		} else if (arg is RegisterArg) {
			val ssaVar = arg.sVar
			if (code.isMetadataSupported()) {
				code.attachDefinition(VarNode.get(mth, checkNotNull(ssaVar)))
			}
			code.add(mgen.nameGen.assignArg(checkNotNull(ssaVar).codeVar))
		} else if (arg is NamedArg) {
			code.add(mgen.nameGen.assignNamedArg(arg))
		} else {
			throw KadxRuntimeException("Unexpected arg type in catch block: $arg, class: " + arg.javaClass.simpleName)
		}
		code.add(") {")

		InsnCodeOffset.attach(code, handler.handlerOffset)
		CodeGenUtils.addCodeComments(code, mth, handler.getHandlerBlock())

		makeRegionIndent(code, region)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(RegionGen::class.java)
	}
}
