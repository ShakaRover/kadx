package jadx.core.codegen

import jadx.api.CommentsLevel
import jadx.api.ICodeWriter
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.api.metadata.annotations.VarNode
import jadx.api.plugins.input.data.MethodHandleType
import jadx.core.codegen.utils.CodeGenUtils
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.FieldInitInsnAttr
import jadx.core.dex.attributes.nodes.FieldReplaceAttr
import jadx.core.dex.attributes.nodes.GenericInfoAttr
import jadx.core.dex.attributes.nodes.LoopLabelAttr
import jadx.core.dex.attributes.nodes.MethodReplaceAttr
import jadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.ArithNode
import jadx.core.dex.instructions.ArithOp
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.ConstClassNode
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.FillArrayInsn
import jadx.core.dex.instructions.FilledNewArrayNode
import jadx.core.dex.instructions.GotoNode
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeCustomNode
import jadx.core.dex.instructions.InvokeCustomRawNode
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InvokeType
import jadx.core.dex.instructions.NewArrayNode
import jadx.core.dex.instructions.SwitchInsn
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.Named
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.java.JsrNode
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.RegionUtils
import jadx.core.utils.android.AndroidResourcesUtils.handleAppResField
import jadx.core.utils.exceptions.CodegenException
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.EnumSet

/**
 * 指令代码生成器：把单条 [InsnNode]（以及嵌套的包装指令）渲染成 Java 源码。
 *
 * **继承关系**：[ConditionGen]（条件表达式）与 [RegionGen]（区域结构化）都继承本类，
 * 复用 [addArg] / [makeInsn] / [useType] 等参数渲染逻辑。
 *
 * **字段可见性说明**：
 * - `mgen` / `fallback` 会被子类 [ConditionGen] 通过外部实例访问，因此声明为公开属性；
 * - `mth` / `root` 只在子类内部（`this`）访问，保持 `protected`。
 */
open class InsnGen(
	val mgen: MethodGen,
	val fallback: Boolean,
) {
	protected val mth: MethodNode = mgen.methodNode

	protected val root: RootNode = mth.root()

	/** 指令渲染标志：是否只渲染表达式主体、是否需要避免加括号、是否内联。 */
	enum class Flags {
		BODY_ONLY,
		BODY_ONLY_NOWRAP,
		INLINE,
	}

	private val isFallback: Boolean get() = fallback

	/** 渲染一个参数，并在其非空时追加 `.`（用于 `obj.field`、`obj.method` 等场景）。 */
	@Throws(CodegenException::class)
	fun addArgDot(code: ICodeWriter, arg: InsnArg) {
		val len = code.getLength()
		addArg(code, arg, true)
		if (len != code.getLength()) {
			code.add('.')
		}
	}

	@Throws(CodegenException::class)
	fun addArg(code: ICodeWriter, arg: InsnArg) {
		addArg(code, arg, true)
	}

	@Throws(CodegenException::class)
	fun addArg(code: ICodeWriter, arg: InsnArg, wrap: Boolean) {
		addArg(code, arg, if (wrap) BODY_ONLY_FLAG else BODY_ONLY_NOWRAP_FLAGS)
	}

	@Throws(CodegenException::class)
	fun addArg(code: ICodeWriter, arg: InsnArg, flags: Set<Flags>) {
		if (arg.isRegister) {
			val reg = arg as RegisterArg
			if (code.isMetadataSupported()) {
				code.attachAnnotation(VarNode.getRef(mth, reg))
			}
			code.add(mgen.nameGen.useArg(reg))
		} else if (arg.isLiteral) {
			addLiteralArg(code, arg as LiteralArg, flags)
		} else if (arg.isInsnWrap) {
			addWrappedArg(code, arg as InsnWrapArg, flags)
		} else if (arg.isNamed) {
			code.add((arg as Named).name)
		} else {
			throw CodegenException("Unknown arg type $arg")
		}
	}

	private fun addLiteralArg(code: ICodeWriter, litArg: LiteralArg, flags: Set<Flags>) {
		val literalStr = lit(litArg)
		if (!flags.contains(Flags.BODY_ONLY_NOWRAP) && literalStr.startsWith("-")) {
			code.add('(').add(literalStr).add(')')
		} else {
			code.add(literalStr)
		}
	}

	@Throws(CodegenException::class)
	private fun addWrappedArg(code: ICodeWriter, arg: InsnWrapArg, flags: Set<Flags>) {
		val wrapInsn = arg.wrapInsn
		if (wrapInsn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
			code.add('(')
			makeInsn(wrapInsn, code, Flags.INLINE)
			code.add(')')
		} else {
			makeInsnBody(code, wrapInsn, flags)
		}
	}

	/** 渲染赋值语句左侧（声明或普通引用）。 */
	@Throws(CodegenException::class)
	fun assignVar(code: ICodeWriter, insn: InsnNode) {
		val arg = insn.result
		if (insn.contains(AFlag.DECLARE_VAR)) {
			declareVar(code, checkNotNull(arg))
		} else {
			addArg(code, checkNotNull(arg), false)
		}
	}

	fun declareVar(code: ICodeWriter, arg: RegisterArg) {
		declareVar(code, checkNotNull(arg.sVar).codeVar)
	}

	fun declareVar(code: ICodeWriter, codeVar: CodeVar) {
		if (codeVar.isFinal) {
			code.add("final ")
		}
		useType(code, checkNotNull(codeVar.type))
		code.add(' ')
		defVar(code, codeVar)
	}

	/** 只输出变量名（不含类型），用于参数声明等场景。 */
	private fun defVar(code: ICodeWriter, codeVar: CodeVar) {
		val varName = mgen.nameGen.assignArg(codeVar)
		if (code.isMetadataSupported()) {
			code.attachDefinition(VarNode.get(mth, codeVar))
		}
		code.add(varName)
	}

	private fun lit(arg: LiteralArg): String = TypeGen.literalToString(arg, mth, fallback)

	@Throws(CodegenException::class)
	private fun instanceField(code: ICodeWriter, field: FieldInfo, arg: InsnArg) {
		val pCls = mth.parentClass
		val fieldNode = pCls.root().resolveField(field)
		if (fieldNode != null) {
			val replace = fieldNode.get(AType.FIELD_REPLACE)
			if (replace != null) {
				when (replace.replaceType) {
					FieldReplaceAttr.ReplaceWith.CLASS_INSTANCE -> {
						useClass(code, replace.clsRef)
						code.add(".this")
					}

					FieldReplaceAttr.ReplaceWith.VAR -> addArg(code, replace.varRef)
				}
				return
			}
		}
		addArgDot(code, arg)
		if (fieldNode != null) {
			code.attachAnnotation(fieldNode)
		}
		if (fieldNode == null) {
			code.add(field.alias)
		} else {
			code.add(fieldNode.alias)
		}
	}

	@Throws(CodegenException::class)
	protected fun staticField(code: ICodeWriter, field: FieldInfo) {
		val fieldNode = root.resolveField(field)
		if (fieldNode != null &&
			fieldNode.contains(AFlag.INLINE_INSTANCE_FIELD) &&
			fieldNode.parentClass.contains(AType.ANONYMOUS_CLASS)
		) {
			val initInsnAttr = fieldNode.get(AType.FIELD_INIT_INSN)
			if (initInsnAttr != null) {
				val insn = initInsnAttr.insn
				if (insn is ConstructorInsn) {
					fieldNode.add(AFlag.DONT_GENERATE)
					inlineAnonymousConstructor(code, fieldNode.parentClass, insn)
					return
				}
			}
		}
		makeStaticFieldAccess(code, field, fieldNode, mgen.classGen)
	}

	fun useClass(code: ICodeWriter, type: ArgType) {
		mgen.classGen.useClass(code, type)
	}

	fun useClass(code: ICodeWriter, cls: ClassInfo) {
		mgen.classGen.useClass(code, cls)
	}

	protected fun useType(code: ICodeWriter, type: ArgType) {
		mgen.classGen.useType(code, type)
	}

	@Throws(CodegenException::class)
	fun makeInsn(insn: InsnNode, code: ICodeWriter) {
		makeInsn(insn, code, null)
	}

	@Throws(CodegenException::class)
	internal fun makeInsn(insn: InsnNode, code: ICodeWriter, flag: Flags?) {
		if (insn.type == InsnType.REGION_ARG) {
			return
		}
		try {
			if (flag == Flags.BODY_ONLY || flag == Flags.BODY_ONLY_NOWRAP) {
				makeInsnBody(code, insn, if (flag == Flags.BODY_ONLY) BODY_ONLY_FLAG else BODY_ONLY_NOWRAP_FLAGS)
			} else {
				if (flag != Flags.INLINE) {
					code.startLineWithNum(insn.sourceLine)
					InsnCodeOffset.attach(code, insn)
					if (insn.contains(AFlag.COMMENT_OUT)) {
						code.add("// ")
					}
				}
				val resArg = insn.result
				if (resArg != null) {
					val v = resArg.sVar
					if (v == null || v.useCount != 0 || insn.type != InsnType.CONSTRUCTOR) {
						assignVar(code, insn)
						code.add(" = ")
					}
				}
				makeInsnBody(code, insn, EMPTY_FLAGS)
				if (flag != Flags.INLINE) {
					code.add(';')
					CodeGenUtils.addCodeComments(code, mth, insn)
				}
			}
		} catch (e: Exception) {
			throw CodegenException(mth, "Error generate insn: $insn", e)
		}
	}

	@Throws(CodegenException::class)
	private fun makeInsnBody(code: ICodeWriter, insn: InsnNode, state: Set<Flags>) {
		when (insn.type) {
			InsnType.CONST_STR -> {
				val str = (insn as ConstStringNode).string
				code.add(mth.root().stringUtils.unescapeString(checkNotNull(str)))
			}

			InsnType.CONST_CLASS -> {
				val clsType = (insn as ConstClassNode).clsType
				useType(code, clsType)
				code.add(".class")
			}

			InsnType.CONST -> {
				val arg = insn.getArg(0) as LiteralArg
				code.add(lit(arg))
			}

			InsnType.MOVE -> addArg(code, insn.getArg(0), false)

			InsnType.CHECK_CAST, InsnType.CAST -> {
				val wrap = state.contains(Flags.BODY_ONLY)
				if (wrap) {
					code.add('(')
				}
				code.add('(')
				useType(code, (insn as IndexInsnNode).index as ArgType)
				code.add(") ")
				addArg(code, insn.getArg(0), true)
				if (wrap) {
					code.add(')')
				}
			}

			InsnType.ARITH -> makeArith(insn as ArithNode, code, state)

			InsnType.NEG -> oneArgInsn(code, insn, state, '-')

			InsnType.NOT -> {
				val op = if (insn.getArg(0).getType() == ArgType.BOOLEAN) '!' else '~'
				oneArgInsn(code, insn, state, op)
			}

			InsnType.RETURN -> {
				if (insn.argsCount != 0) {
					code.add("return ")
					addArg(code, insn.getArg(0), false)
				} else {
					code.add("return")
				}
			}

			InsnType.BREAK -> {
				code.add("break")
				val labelAttr = insn.get(AType.LOOP_LABEL)
				if (labelAttr != null) {
					code.add(' ').add(mgen.nameGen.getLoopLabel(labelAttr))
				}
			}

			InsnType.CONTINUE -> code.add("continue")

			InsnType.THROW -> {
				code.add("throw ")
				addArg(code, insn.getArg(0), true)
			}

			InsnType.CMP_L, InsnType.CMP_G -> {
				code.add('(')
				addArg(code, insn.getArg(0))
				code.add(" > ")
				addArg(code, insn.getArg(1))
				code.add(" ? 1 : (")
				addArg(code, insn.getArg(0))
				code.add(" == ")
				addArg(code, insn.getArg(1))
				code.add(" ? 0 : -1))")
			}

			InsnType.INSTANCE_OF -> {
				val wrap = state.contains(Flags.BODY_ONLY)
				if (wrap) {
					code.add('(')
				}
				addArg(code, insn.getArg(0))
				code.add(" instanceof ")
				useType(code, (insn as IndexInsnNode).index as ArgType)
				if (wrap) {
					code.add(')')
				}
			}

			InsnType.CONSTRUCTOR -> makeConstructor(insn as ConstructorInsn, code)

			InsnType.INVOKE -> makeInvoke(insn as InvokeNode, code)

			InsnType.NEW_ARRAY -> {
				val arrayType = (insn as NewArrayNode).arrayType
				code.add("new ")
				useType(code, arrayType.getArrayRootElement())
				val argsCount = insn.argsCount
				for (k in 0 until argsCount) {
					code.add('[')
					addArg(code, insn.getArg(k), false)
					code.add(']')
				}
				val dim = arrayType.getArrayDimension()
				for (k in argsCount until dim) {
					code.add("[]")
				}
			}

			InsnType.ARRAY_LENGTH -> {
				addArg(code, insn.getArg(0))
				code.add(".length")
			}

			InsnType.FILLED_NEW_ARRAY -> filledNewArray(insn as FilledNewArrayNode, code)

			InsnType.FILL_ARRAY -> {
				val arrayNode = insn as FillArrayInsn
				if (fallback) {
					val arrStr = arrayNode.dataToString()
					addArg(code, insn.getArg(0))
					code.add(" = {").add(arrStr.substring(1, arrStr.length - 1)).add("} // fill-array")
				} else {
					fillArray(code, arrayNode)
				}
			}

			InsnType.AGET -> {
				addArg(code, insn.getArg(0))
				code.add('[')
				addArg(code, insn.getArg(1), false)
				code.add(']')
			}

			InsnType.APUT -> {
				addArg(code, insn.getArg(0))
				code.add('[')
				addArg(code, insn.getArg(1), false)
				code.add("] = ")
				addArg(code, insn.getArg(2), false)
			}

			InsnType.IGET -> {
				val fieldInfo = (insn as IndexInsnNode).index as FieldInfo
				instanceField(code, fieldInfo, insn.getArg(0))
			}

			InsnType.IPUT -> {
				val fieldInfo = (insn as IndexInsnNode).index as FieldInfo
				instanceField(code, fieldInfo, insn.getArg(1))
				code.add(" = ")
				addArg(code, insn.getArg(0), false)
			}

			InsnType.SGET -> staticField(code, (insn as IndexInsnNode).index as FieldInfo)

			InsnType.SPUT -> {
				val field = (insn as IndexInsnNode).index as FieldInfo
				staticField(code, field)
				code.add(" = ")
				addArg(code, insn.getArg(0), false)
			}

			InsnType.STR_CONCAT -> {
				val wrap = state.contains(Flags.BODY_ONLY)
				if (wrap) {
					code.add('(')
				}
				val it = insn.getArguments().iterator()
				while (it.hasNext()) {
					addArg(code, it.next())
					if (it.hasNext()) {
						code.add(" + ")
					}
				}
				if (wrap) {
					code.add(')')
				}
			}

			InsnType.MONITOR_ENTER -> {
				if (isFallback) {
					code.add("monitor-enter(")
					addArg(code, insn.getArg(0))
					code.add(')')
				}
			}

			InsnType.MONITOR_EXIT -> {
				if (isFallback) {
					code.add("monitor-exit(")
					if (insn.argsCount == 1) {
						addArg(code, insn.getArg(0))
					}
					code.add(')')
				}
			}

			InsnType.TERNARY -> makeTernary(insn as TernaryInsn, code, state)

			InsnType.ONE_ARG -> addArg(code, insn.getArg(0), state)

			/* fallback mode instructions */
			InsnType.IF -> {
				fallbackOnlyInsn(insn)
				val ifInsn = insn as IfNode
				code.add("if (")
				addArg(code, insn.getArg(0))
				code.add(' ')
				code.add(ifInsn.getOp().symbol).add(' ')
				addArg(code, insn.getArg(1))
				code.add(") goto ").add(MethodGen.getLabelName(ifInsn))
			}

			InsnType.GOTO -> {
				fallbackOnlyInsn(insn)
				code.add("goto ").add(MethodGen.getLabelName((insn as GotoNode).getTarget()))
			}

			InsnType.MOVE_EXCEPTION -> {
				fallbackOnlyInsn(insn)
				code.add("move-exception")
			}

			InsnType.SWITCH -> {
				fallbackOnlyInsn(insn)
				val sw = insn as SwitchInsn
				code.add("switch(")
				addArg(code, insn.getArg(0))
				code.add(") {")
				code.incIndent()
				val keys = sw.getKeys()
				val size = keys.size
				val targetBlocks = sw.getTargetBlocks()
				if (targetBlocks != null) {
					for (i in 0 until size) {
						code.startLine("case ").add(keys[i].toString()).add(": goto ")
						code.add(MethodGen.getLabelName(checkNotNull(targetBlocks[i]))).add(';')
					}
					code.startLine("default: goto ")
					code.add(MethodGen.getLabelName(checkNotNull(sw.getDefTargetBlock()))).add(';')
				} else {
					val targets = sw.getTargets()
					for (i in 0 until size) {
						code.startLine("case ").add(keys[i].toString()).add(": goto ")
						code.add(MethodGen.getLabelName(targets[i])).add(';')
					}
					code.startLine("default: goto ")
					code.add(MethodGen.getLabelName(sw.defaultCaseOffset)).add(';')
				}
				code.decIndent()
				code.startLine('}')
			}

			InsnType.NEW_INSTANCE -> {
				// only fallback - make new instance in constructor invoke
				fallbackOnlyInsn(insn)
				code.add("new ").add(checkNotNull(insn.result).getInitType().toString())
			}

			InsnType.PHI -> {
				fallbackOnlyInsn(insn)
				code.add(insn.type.toString()).add('(')
				for (insnArg in insn.getArguments()) {
					addArg(code, insnArg)
					code.add(' ')
				}
				code.add(')')
			}

			InsnType.MOVE_RESULT -> {
				fallbackOnlyInsn(insn)
				code.add("move-result")
			}

			InsnType.FILL_ARRAY_DATA -> {
				fallbackOnlyInsn(insn)
				code.add("fill-array $insn")
			}

			InsnType.SWITCH_DATA -> {
				fallbackOnlyInsn(insn)
				code.add(insn.toString())
			}

			InsnType.MOVE_MULTI -> {
				fallbackOnlyInsn(insn)
				val len = insn.argsCount
				for (i in 0 until len - 1 step 2) {
					addArg(code, insn.getArg(i))
					code.add(" = ")
					addArg(code, insn.getArg(i + 1))
					code.add("; ")
				}
			}

			InsnType.JAVA_JSR -> {
				fallbackOnlyInsn(insn)
				code.add("jsr -> ").add(MethodGen.getLabelName((insn as JsrNode).getTarget()))
			}

			InsnType.JAVA_RET -> {
				fallbackOnlyInsn(insn)
				code.add("ret ")
				addArg(code, insn.getArg(0))
			}

			else -> throw CodegenException(mth, "Unknown instruction: " + insn.type)
		}
	}

	/**
	 * 通常与 new-array 指令配合：逐个填充数组元素（可被 System.arraycopy 优化替代）。
	 */
	@Throws(CodegenException::class)
	private fun fillArray(code: ICodeWriter, arrayNode: FillArrayInsn) {
		if (mth.checkCommentsLevel(CommentsLevel.INFO)) {
			code.add("// fill-array-data instruction")
		}
		code.startLine()
		val arrArg = arrayNode.getArg(0)
		val arrayType = arrArg.getType()
		val elemType: ArgType
		if (arrayType.isTypeKnown() && arrayType.isArray()) {
			elemType = checkNotNull(arrayType.getArrayElement())
		} else {
			val elementType = arrayNode.elementType // unknown type
			elemType = checkNotNull(elementType.selectFirst())
		}
		val args = arrayNode.getLiteralArgs(elemType)
		val len = args.size
		for (i in 0 until len) {
			if (i != 0) {
				code.add(';')
				code.startLine()
			}
			addArg(code, arrArg)
			code.add('[').add(i.toString()).add("] = ").add(lit(args[i]))
		}
	}

	@Throws(CodegenException::class)
	private fun oneArgInsn(code: ICodeWriter, insn: InsnNode, state: Set<Flags>, op: Char) {
		val wrap = state.contains(Flags.BODY_ONLY)
		if (wrap) {
			code.add('(')
		}
		code.add(op)
		addArg(code, insn.getArg(0))
		if (wrap) {
			code.add(')')
		}
	}

	@Throws(CodegenException::class)
	private fun fallbackOnlyInsn(insn: InsnNode) {
		if (!fallback) {
			val msg = insn.type.toString() + " instruction can be used only in fallback mode"
			val e = CodegenException(msg)
			mth.addError(msg, e)
			mth.parentClass.topParentClass.add(AFlag.RESTART_CODEGEN)
			throw e
		}
	}

	@Throws(CodegenException::class)
	private fun filledNewArray(insn: FilledNewArrayNode, code: ICodeWriter) {
		if (!insn.contains(AFlag.DECLARE_VAR)) {
			code.add("new ")
			useType(code, insn.arrayType)
		}
		code.add('{')
		val c = insn.argsCount
		var wrap = 0
		for (i in 0 until c) {
			addArg(code, insn.getArg(i), false)
			if (i + 1 < c) {
				code.add(", ")
			}
			wrap++
			if (wrap == 1000) {
				code.startLine()
				wrap = 0
			}
		}
		code.add('}')
	}

	@Throws(CodegenException::class)
	private fun makeConstructor(insn: ConstructorInsn, code: ICodeWriter) {
		val cls = mth.root().resolveClass(insn.classType)
		if (cls != null && cls.isAnonymous() && !fallback) {
			inlineAnonymousConstructor(code, cls, insn)
			return
		}
		if (insn.isSelf) {
			throw JadxRuntimeException("Constructor 'self' invoke must be removed!")
		}
		val callMth = mth.root().resolveMethod(insn.callMth)
		var refMth = callMth
		if (callMth != null) {
			val replaceAttr = callMth.get(AType.METHOD_REPLACE)
			if (replaceAttr != null) {
				refMth = replaceAttr.replaceMth
			}
		}

		if (insn.isSuper) {
			code.attachAnnotation(refMth)
			code.add("super")
		} else if (insn.isThis) {
			code.attachAnnotation(refMth)
			code.add("this")
		} else {
			val forceShortName = addOuterClassInstance(insn, code, callMth)
			code.add("new ")
			if (refMth == null || refMth.contains(AFlag.DONT_GENERATE)) {
				// use class reference if constructor method is missing (default constructor)
				code.attachAnnotation(mth.root().resolveClass(insn.callMth.declClass))
			} else {
				code.attachAnnotation(refMth)
			}
			if (forceShortName) {
				mgen.classGen.addClsShortNameForced(code, insn.classType)
			} else {
				mgen.classGen.addClsName(code, insn.classType)
			}
			val genericInfoAttr = insn.get(AType.GENERIC_INFO)
			if (genericInfoAttr != null) {
				code.add('<')
				if (genericInfoAttr.isExplicit()) {
					var first = true
					for (type in genericInfoAttr.genericTypes) {
						if (!first) {
							code.add(',')
						} else {
							first = false
						}
						mgen.classGen.useType(code, type)
					}
				}
				code.add('>')
			}
		}
		generateMethodArguments(code, insn, 0, callMth)
	}

	@Throws(CodegenException::class)
	private fun addOuterClassInstance(insn: ConstructorInsn, code: ICodeWriter, callMth: MethodNode?): Boolean {
		if (callMth == null || !callMth.contains(AFlag.SKIP_FIRST_ARG)) {
			return false
		}
		val ctrCls = checkNotNull(callMth.declaringClass)
		if (!ctrCls.isInner() || insn.argsCount == 0) {
			return false
		}
		val instArg = insn.getArg(0)
		if (instArg.isThis()) {
			return false
		}
		// instance arg should be of an outer class type
		if (instArg.getType() != checkNotNull(ctrCls.declaringClass).getType()) {
			return false
		}
		addArgDot(code, instArg)
		// can't use another dot, force short name of class
		return true
	}

	@Throws(CodegenException::class)
	private fun inlineAnonymousConstructor(code: ICodeWriter, cls: ClassNode, insn: ConstructorInsn) {
		if (!cls.checkProcessed()) {
			// 被内联的匿名类可能已完成自身 codegen 并卸载（state=NOT_LOADED，与外层类
			// 的 codegen 存在顺序竞争）。按需重新处理恢复到 PROCESS_COMPLETE——
			// 直接 ensureProcessed 会抛异常导致外层方法反编译失败
			mth.root().getProcessClasses().forceProcess(cls)
		}
		cls.ensureProcessed()
		if (this.mth.parentClass === cls) {
			cls.remove(AType.ANONYMOUS_CLASS)
			cls.remove(AFlag.DONT_GENERATE)
			mth.parentClass.topParentClass.add(AFlag.RESTART_CODEGEN)
			throw CodegenException(
				"Anonymous inner class unlimited recursion detected." +
					" Convert class to inner: " + cls.classInfo.fullName,
			)
		}
		val parent = checkNotNull(cls.get(AType.ANONYMOUS_CLASS)).baseType
		// hide empty anonymous constructors
		for (ctor in cls.methods) {
			if (ctor.contains(AFlag.ANONYMOUS_CONSTRUCTOR) &&
				RegionUtils.isEmpty(ctor.region)
			) {
				ctor.add(AFlag.DONT_GENERATE)
			}
		}
		code.attachDefinition(cls)
		code.add("new ")
		useClass(code, parent)
		val callMth = mth.root().resolveMethod(insn.callMth)
		if (callMth != null) {
			// copy var names
			val mthArgs = callMth.argRegs
			val argsCount = Math.min(insn.argsCount, mthArgs.size)
			for (i in 0 until argsCount) {
				val arg = insn.getArg(i)
				if (arg.isRegister) {
					val mthArg = mthArgs[i]
					val insnArg = arg as RegisterArg
					val argSVar = insnArg.sVar
					// 参数可能来自中途夭折的重处理周期，CodeVar 尚未初始化：
					// 跳过名字合并（仅影响输出美观），不抛异常
					if (argSVar != null && argSVar.isCodeVarSet()) {
						checkNotNull(mthArg.sVar).setCodeVar(argSVar.codeVar)
					}
				}
			}
		}
		generateMethodArguments(code, insn, 0, callMth)
		code.add(' ')

		val classGen = ClassGen(cls, mgen.classGen.parentGen)
		classGen.outerNameGen = mgen.nameGen
		classGen.addClassBody(code, true)

		mth.parentClass.addInlinedClass(cls)
	}

	@Throws(CodegenException::class)
	private fun makeInvoke(insn: InvokeNode, code: ICodeWriter) {
		val type = insn.invokeType
		if (type == InvokeType.CUSTOM) {
			makeInvokeLambda(code, insn as InvokeCustomNode)
			return
		}
		val callMth = insn.callMth
		val callMthNode = mth.root().resolveMethod(callMth)

		if (type == InvokeType.CUSTOM_RAW) {
			makeInvokeCustomRaw(insn as InvokeCustomRawNode, callMthNode, code)
			return
		}
		if (insn.isPolymorphicCall()) {
			// add missing cast
			code.add('(')
			useType(code, callMth.returnType)
			code.add(") ")
		}

		var k = 0
		when (type) {
			InvokeType.DIRECT, InvokeType.VIRTUAL, InvokeType.INTERFACE, InvokeType.POLYMORPHIC -> {
				val arg = insn.getArg(0)
				if (needInvokeArg(arg)) {
					addArgDot(code, arg)
				}
				k++
			}

			InvokeType.SUPER -> {
				callSuper(code, callMth)
				k++ // use 'super' instead 'this' in 0 arg
				code.add('.')
			}

			InvokeType.STATIC -> {
				val insnCls = mth.parentClass.classInfo
				val declClass = callMth.declClass
				if (insnCls != declClass) {
					useClass(code, declClass)
					code.add('.')
				}
			}

			else -> {}
		}
		if (callMthNode != null) {
			code.attachAnnotation(callMthNode)
		}
		if (insn.contains(AFlag.FORCE_RAW_NAME)) {
			code.add(callMth.name)
		} else {
			if (callMthNode != null) {
				code.add(callMthNode.alias)
			} else {
				code.add(callMth.alias)
			}
		}
		generateMethodArguments(code, insn, k, callMthNode)
	}

	@Throws(CodegenException::class)
	private fun makeInvokeCustomRaw(insn: InvokeCustomRawNode, callMthNode: MethodNode?, code: ICodeWriter) {
		if (isFallback) {
			code.add("call_site(")
			code.incIndent()
			for (value in checkNotNull(insn.callSiteValues)) {
				code.startLine(value.toString())
			}
			code.decIndent()
			code.startLine(").invoke")
			generateMethodArguments(code, insn, 0, callMthNode)
		} else {
			val returnType = insn.callMth.returnType
			if (!returnType.isVoid()) {
				code.add('(')
				useType(code, returnType)
				code.add(") ")
			}
			makeInvoke(insn.resolveInvoke, code)
			code.add(".dynamicInvoker().invoke")
			generateMethodArguments(code, insn, 0, callMthNode)
			code.add(" /* invoke-custom */")
		}
	}

	// FIXME: add 'this' for equals methods in scope
	private fun needInvokeArg(arg: InsnArg): Boolean {
		if (arg.isAnyThis()) {
			if (arg.isThis()) {
				return false
			}
			val clsNode = mth.root().resolveClass(arg.getType())
			if (clsNode != null && clsNode.contains(AFlag.DONT_GENERATE)) {
				return false
			}
		}
		return true
	}

	@Throws(CodegenException::class)
	private fun makeInvokeLambda(code: ICodeWriter, customNode: InvokeCustomNode) {
		if (customNode.isUseRef) {
			makeRefLambda(code, customNode)
			return
		}
		if (fallback || !customNode.isInlineInsn) {
			makeSimpleLambda(code, customNode)
			return
		}
		val callMth = checkNotNull(customNode.callInsn).get(AType.METHOD_DETAILS) as MethodNode
		makeInlinedLambdaMethod(code, customNode, callMth)
	}

	@Throws(CodegenException::class)
	private fun makeRefLambda(code: ICodeWriter, customNode: InvokeCustomNode) {
		val callInsn = customNode.callInsn
		if (callInsn is ConstructorInsn) {
			val callMth = callInsn.callMth
			useClass(code, callMth.declClass)
			code.add("::new")
			return
		}
		if (callInsn is InvokeNode) {
			val callMth = callInsn.callMth
			if (customNode.handleType == MethodHandleType.INVOKE_STATIC) {
				useClass(code, callMth.declClass)
			} else {
				addArg(code, customNode.getArg(0))
			}
			code.add("::").add(callMth.alias)
		}
	}

	private fun makeSimpleLambda(code: ICodeWriter, customNode: InvokeCustomNode) {
		try {
			val callInsn = checkNotNull(customNode.callInsn)
			val implMthInfo = checkNotNull(customNode.implMthInfo)
			val implArgsCount = implMthInfo.argsCount
			if (implArgsCount == 0) {
				code.add("()")
			} else {
				code.add('(')
				val callArgsCount = callInsn.argsCount
				val startArg = callArgsCount - implArgsCount
				if (customNode.handleType != MethodHandleType.INVOKE_STATIC &&
					customNode.argsCount > 0 &&
					customNode.getArg(0).isThis()
				) {
					callInsn.getArg(0).add(AFlag.THIS)
				}
				if (startArg >= 0) {
					for (i in startArg until callArgsCount) {
						if (i != startArg) {
							code.add(", ")
						}
						addArg(code, callInsn.getArg(i))
					}
				} else {
					code.add("/* ERROR: $startArg */")
				}
				code.add(')')
			}
			code.add(" -> {")
			if (fallback) {
				code.add(" // ").add(implMthInfo.toString())
			}
			code.incIndent()
			code.startLine()
			if (!implMthInfo.returnType.isVoid()) {
				code.add("return ")
			}
			makeInsn(callInsn, code, Flags.INLINE)
			code.add(";")

			code.decIndent()
			code.startLine('}')
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to generate 'invoke-custom' instruction: " + e.message, e)
		}
	}

	@Throws(CodegenException::class)
	private fun makeInlinedLambdaMethod(code: ICodeWriter, customNode: InvokeCustomNode, callMth: MethodNode) {
		val callMthGen = MethodGen(mgen.classGen, callMth)
		val nameGen = callMthGen.nameGen
		nameGen.inheritUsedNames(this.mgen.nameGen)

		val implArgs = checkNotNull(customNode.implMthInfo).argumentsTypes
		val callArgs = callMth.argRegs
		if (implArgs.isEmpty()) {
			code.add("()")
		} else {
			val callArgsCount = callArgs.size
			val startArg = callArgsCount - implArgs.size
			if (callArgsCount - startArg > 1) {
				code.add('(')
			}
			for (i in startArg until callArgsCount) {
				if (i != startArg) {
					code.add(", ")
				}
				val argCodeVar = checkNotNull(callArgs[i].sVar).codeVar
				defVar(code, argCodeVar)
			}
			if (callArgsCount - startArg > 1) {
				code.add(')')
			}
		}
		// force set external arg names into call method args
		val extArgsCount = customNode.argsCount
		val startArg = if (customNode.handleType == MethodHandleType.INVOKE_STATIC) 0 else 1 // skip 'this' arg
		var callArg = 0
		for (i in startArg until extArgsCount) {
			val arg = customNode.getArg(i)
			if (arg.isRegister) {
				val extArg = arg as RegisterArg
				val callRegArg = callArgs[callArg++]
				checkNotNull(callRegArg.sVar).setCodeVar(checkNotNull(extArg.sVar).codeVar)
			} else {
				throw JadxRuntimeException("Unexpected argument type in lambda call: " + arg.javaClass.simpleName)
			}
		}
		code.add(" -> {")
		code.incIndent()
		callMthGen.addInstructions(code)

		code.decIndent()
		code.startLine('}')
	}

	private fun callSuper(code: ICodeWriter, callMth: MethodInfo) {
		val superCallCls = getClassForSuperCall(callMth)
		if (superCallCls == null) {
			// unknown class, add comment to keep that info
			code.add("super/*").add(callMth.declClass.fullName).add("*/")
			return
		}
		val curClass = mth.parentClass.classInfo
		if (superCallCls == curClass) {
			code.add("super")
			return
		}
		// use custom class
		useClass(code, superCallCls)
		code.add(".super")
	}

	/**
	 * 在当前类及其所有父类中查找调用的声明类（用于内联合成调用的 `super` 处理）。
	 */
	private fun getClassForSuperCall(callMth: MethodInfo): ClassInfo? {
		val declClsType = callMth.declClass.type
		var parentNode = mth.parentClass
		while (true) {
			val parentCls = parentNode.classInfo
			if (ArgType.isInstanceOf(root, parentCls.type, declClsType)) {
				return parentCls
			}
			val nextParent = parentNode.parentClass
			if (nextParent === parentNode) {
				// no parent, class not found
				return null
			}
			parentNode = nextParent
		}
	}

	@Throws(CodegenException::class)
	internal fun generateMethodArguments(
		code: ICodeWriter,
		insn: BaseInvokeNode,
		startArgNum: Int,
		mthNode: MethodNode?,
	) {
		var k = startArgNum
		if (mthNode != null && mthNode.contains(AFlag.SKIP_FIRST_ARG)) {
			k++
		}
		val argsCount = insn.argsCount
		code.add('(')
		val skipAttr = mthNode?.get(AType.SKIP_MTH_ARGS)
		var firstArg = true
		if (k < argsCount) {
			for (i in k until argsCount) {
				val arg = insn.getArg(i)
				if (arg.contains(AFlag.SKIP_ARG) || (skipAttr != null && skipAttr.isSkip(i - startArgNum))) {
					continue
				}
				if (firstArg) {
					firstArg = false
				} else {
					code.add(", ")
				}
				if (i == argsCount - 1 && processVarArg(code, insn, arg)) {
					continue
				}
				addArg(code, arg, false)
			}
		}
		code.add(')')
	}

	/**
	 * 展开可变参数（来自 filled-new-array）。
	 */
	@Throws(CodegenException::class)
	private fun processVarArg(code: ICodeWriter, invokeInsn: BaseInvokeNode, lastArg: InsnArg): Boolean {
		if (!invokeInsn.contains(AFlag.VARARG_CALL)) {
			return false
		}
		if (!lastArg.getType().isArray() || !lastArg.isInsnWrap) {
			return false
		}
		val insn = (lastArg as InsnWrapArg).wrapInsn
		if (insn.type != InsnType.FILLED_NEW_ARRAY) {
			return false
		}
		val count = insn.argsCount
		for (i in 0 until count) {
			val elemArg = insn.getArg(i)
			addArg(code, elemArg, false)
			if (i < count - 1) {
				code.add(", ")
			}
		}
		return true
	}

	@Throws(CodegenException::class)
	private fun makeTernary(insn: TernaryInsn, code: ICodeWriter, state: Set<Flags>) {
		val wrap = state.contains(Flags.BODY_ONLY)
		if (wrap) {
			code.add('(')
		}
		val first = insn.getArg(0)
		val second = insn.getArg(1)
		val condGen = ConditionGen(this)
		if (first.isTrue() && second.isFalse()) {
			condGen.add(code, insn.condition)
		} else {
			condGen.wrap(code, insn.condition)
			code.add(" ? ")
			addArg(code, first, false)
			code.add(" : ")
			addArg(code, second, false)
		}
		if (wrap) {
			code.add(')')
		}
	}

	@Throws(CodegenException::class)
	private fun makeArith(insn: ArithNode, code: ICodeWriter, state: Set<Flags>) {
		if (insn.contains(AFlag.ARITH_ONEARG)) {
			makeArithOneArg(insn, code)
			return
		}
		// wrap insn in brackets for save correct operation order
		val wrap = state.contains(Flags.BODY_ONLY) && !insn.contains(AFlag.DONT_WRAP)
		if (wrap) {
			code.add('(')
		}
		addArg(code, insn.getArg(0))
		code.add(' ')
		code.add(insn.op.symbol)
		code.add(' ')
		addArg(code, insn.getArg(1))
		if (wrap) {
			code.add(')')
		}
	}

	@Throws(CodegenException::class)
	private fun makeArithOneArg(insn: ArithNode, code: ICodeWriter) {
		val op = insn.op
		val resArg = insn.getArg(0)
		val arg = insn.getArg(1)

		// "++" or "--"
		if (arg.isLiteral && (op == ArithOp.ADD || op == ArithOp.SUB)) {
			val lit = arg as LiteralArg
			if (lit.literal == 1L && lit.isInteger()) {
				addArg(code, resArg, false)
				val opSymbol = op.symbol
				code.add(opSymbol).add(opSymbol)
				return
			}
		}

		// +=, -=, ...
		addArg(code, resArg, false)
		code.add(' ').add(op.symbol).add("= ")
		addArg(code, arg, false)
	}

	companion object {
		fun makeStaticFieldAccess(code: ICodeWriter, field: FieldInfo, clsGen: ClassGen) {
			val fieldNode = clsGen.classNode.root().resolveField(field)
			makeStaticFieldAccess(code, field, fieldNode, clsGen)
		}

		private fun makeStaticFieldAccess(
			code: ICodeWriter,
			field: FieldInfo,
			fieldNode: FieldNode?,
			clsGen: ClassGen,
		) {
			val declClass = field.declClass
			val fieldFromThisClass = clsGen.classNode.classInfo == declClass
			if (!fieldFromThisClass || !clsGen.isBodyGenStarted) {
				// Android specific resources class handler
				if (!handleAppResField(code, clsGen, declClass)) {
					clsGen.useClass(code, declClass)
				}
				code.add('.')
			}
			if (fieldNode != null) {
				code.attachAnnotation(fieldNode)
			}
			if (fieldNode == null) {
				code.add(field.alias)
			} else {
				code.add(fieldNode.alias)
			}
		}

		private val EMPTY_FLAGS: Set<Flags> = EnumSet.noneOf(Flags::class.java)
		private val BODY_ONLY_FLAG: Set<Flags> = EnumSet.of(Flags.BODY_ONLY)
		private val BODY_ONLY_NOWRAP_FLAGS: Set<Flags> = EnumSet.of(Flags.BODY_ONLY_NOWRAP)
	}
}
