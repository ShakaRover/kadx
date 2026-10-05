package kadx.core.dex.visitors

import kadx.api.plugins.input.data.IFieldRef
import kadx.api.plugins.input.data.annotations.AnnotationVisibility
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.AttrNode
import kadx.core.dex.attributes.nodes.DeclareVariablesAttr
import kadx.core.dex.attributes.nodes.LineAttrNode
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.instructions.ArithNode
import kadx.core.dex.instructions.ArithOp
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.instructions.mods.TernaryInsn
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.InsnContainer
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.conditions.IfCondition
import kadx.core.dex.visitors.regions.variables.ProcessVariables
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnList
import kadx.core.utils.exceptions.KadxException
import java.util.HashSet

/**
 * 为代码生成做最后的指令准备。
 *
 * **做什么**：删除无用指令、把 `move` 换成内层 wrapped 指令、给非 int 常量加显式类型、
 * 去掉多余括号、把 `a = a + 2` 变成 `a += 2`、把构造器调用移到方法开头、
 * 处理注解中的字段引用、给 `null` 加 cast。
 *
 * **注意**：本 Pass 的修改会破坏寄存器依赖，必须紧挨着 CodeGen 运行。
 */
@KadxVisitor(
	name = "PrepareForCodeGen",
	desc = "Prepare instructions for code generation pass",
	runAfter = [CodeShrinkVisitor::class, ClassModifier::class, ProcessVariables::class],
)
class PrepareForCodeGen : AbstractVisitor() {

	override fun getName(): String = "PrepareForCodeGen"

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (cls.root().getArgs().isDebugInfo) {
			setClassSourceLine(cls)
		}
		collectFieldsUsageInAnnotations(cls)
		return true
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (block in checkNotNull(mth.basicBlocks)) {
			if (block.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			removeInstructions(block)
			checkInline(block)
			removeParenthesis(block)
			modifyArith(block)
			checkConstUsage(block)
			addNullCasts(mth, block)
		}
		moveConstructorInConstructor(mth)
		collectFieldsUsageInAnnotations(mth, mth)
	}

	private fun removeInstructions(block: BlockNode) {
		val it = block.instructions.iterator()
		while (it.hasNext()) {
			val insn = it.next()
			when (insn.type) {
				InsnType.NOP, InsnType.MONITOR_ENTER, InsnType.MONITOR_EXIT, InsnType.MOVE_EXCEPTION -> it.remove()

				InsnType.CONSTRUCTOR -> {
					val co = insn as ConstructorInsn
					if (co.isSelf) {
						it.remove()
					}
				}

				InsnType.MOVE -> {
					// 删除冗余 move：结果未使用且参数名相同 (a = a;)
					val result = insn.result
					if (result != null &&
						checkNotNull(result.sVar).useCount == 0 &&
						result.isNameEquals(insn.getArg(0))
					) {
						it.remove()
					}
				}

				else -> {}
			}
		}
	}

	private fun checkInline(block: BlockNode) {
		val list = block.instructions
		for (i in list.indices) {
			val insn = list[i]
			// 把 'move' 换成其内层 wrapped 指令
			if (insn.type == InsnType.MOVE && insn.getArg(0).isInsnWrap) {
				val wrapInsn = (insn.getArg(0) as InsnWrapArg).wrapInsn
				wrapInsn.setResult(insn.result)
				wrapInsn.copyAttributesFrom(insn)
				list[i] = wrapInsn
			}
		}
	}

	/**
	 * 给非 int 常量加显式类型。
	 */
	private fun checkConstUsage(block: BlockNode) {
		for (blockInsn in block.instructions) {
			blockInsn.visitInsns(
				{ insn ->
					if (!forbidExplicitType(insn.type)) {
						for (arg in insn.getArguments()) {
							if (arg.isLiteral && arg.getType() != ArgType.INT) {
								arg.add(AFlag.EXPLICIT_PRIMITIVE_TYPE)
							}
						}
					}
				},
			)
		}
	}

	private fun forbidExplicitType(type: InsnType): Boolean = when (type) {
		InsnType.CONST,
		InsnType.CAST,
		InsnType.IF,
		InsnType.FILLED_NEW_ARRAY,
		InsnType.APUT,
		InsnType.ARITH,
		-> true

		else -> false
	}

	private fun removeParenthesis(block: BlockNode) {
		for (insn in block.instructions) {
			removeParenthesis(insn)
		}
	}

	/**
	 * 删除 arith '+' 或 '-' 中 wrapped 指令的括号
	 * ('(a + b) +c' => 'a + b + c')
	 */
	private fun removeParenthesis(insn: InsnNode) {
		if (insn.type == InsnType.ARITH) {
			val arith = insn as ArithNode
			val op = arith.op
			if (op == ArithOp.ADD || op == ArithOp.MUL || op == ArithOp.AND || op == ArithOp.OR) {
				for (i in 0 until 2) {
					val arg = arith.getArg(i)
					if (arg.isInsnWrap) {
						val wrapInsn = (arg as InsnWrapArg).wrapInsn
						if (wrapInsn.type == InsnType.ARITH && (wrapInsn as ArithNode).op == op) {
							wrapInsn.add(AFlag.DONT_WRAP)
						}
						removeParenthesis(wrapInsn)
					}
				}
			}
		} else {
			if (insn.type == InsnType.TERNARY) {
				removeParenthesis((insn as TernaryInsn).condition)
			}
			for (arg in insn.getArguments()) {
				if (arg.isInsnWrap) {
					val wrapInsn = (arg as InsnWrapArg).wrapInsn
					removeParenthesis(wrapInsn)
				}
			}
		}
	}

	private fun removeParenthesis(cond: IfCondition) {
		val mode = cond.mode
		for (c in cond.args) {
			if (c.mode == mode) {
				c.add(AFlag.DONT_WRAP)
			}
		}
	}

	/**
	 * 把算术运算替换为短形式
	 * ('a = a + 2' => 'a += 2')
	 */
	private fun modifyArith(block: BlockNode) {
		for (insn in block.instructions) {
			if (insn.type == InsnType.ARITH &&
				!insn.contains(AFlag.ARITH_ONEARG) &&
				!insn.contains(AFlag.DECLARE_VAR)
			) {
				val res = insn.result
				val arg = insn.getArg(0)
				var replace = false
				if (res == arg) {
					replace = true
				} else if (arg.isRegister) {
					val regArg = arg as RegisterArg
					replace = res != null && res.sameCodeVar(regArg)
				}
				if (replace) {
					insn.setResult(null)
					insn.add(AFlag.ARITH_ONEARG)
				}
			}
		}
	}

	/**
	 * 检查构造器中的 'super' / 'this' 调用是否为第一条指令。
	 * 否则移到开头并给出警告。
	 */
	private fun moveConstructorInConstructor(mth: MethodNode) {
		if (!mth.isConstructor()) {
			return
		}
		val ctrInsn = searchConstructorCall(mth)
		if (ctrInsn == null || ctrInsn.contains(AFlag.DONT_GENERATE)) {
			return
		}
		val firstInsn = BlockUtils.isFirstInsn(mth, ctrInsn)
		val declVarsAttr: DeclareVariablesAttr? = checkNotNull(mth.region).get(AType.DECLARE_VARIABLES)
		if (firstInsn && declVarsAttr == null) {
			// 不需要移动
			return
		}
		val callType = ctrInsn.callType.toString().lowercase()
		val blockByInsn = BlockUtils.getBlockByInsn(mth, ctrInsn)
		if (blockByInsn == null) {
			mth.addWarn("Failed to move $callType instruction to top")
			return
		}

		if (!firstInsn) {
			val regArgs = HashSet<RegisterArg>()
			ctrInsn.getRegisterArgs(regArgs)
			regArgs.remove(mth.getThisArg())
			for (arg in mth.argRegs) {
				regArgs.remove(arg)
			}
			if (regArgs.isNotEmpty()) {
				mth.addWarnComment("Illegal instructions before constructor call")
				return
			}
			mth.addWarnComment("'$callType' call moved to the top of the method (can break code semantics)")
		}

		// 确认移动
		InsnList.remove(blockByInsn, ctrInsn)
		(checkNotNull(mth.region).subBlocks as MutableList<IContainer>).add(0, InsnContainer(ctrInsn))
	}

	private fun searchConstructorCall(mth: MethodNode): ConstructorInsn? {
		for (block in checkNotNull(mth.basicBlocks)) {
			for (insn in block.instructions) {
				if (insn.type == InsnType.CONSTRUCTOR) {
					val ctrInsn = insn as ConstructorInsn
					if (ctrInsn.isSuper || ctrInsn.isThis) {
						return ctrInsn
					}
					return null
				}
			}
		}
		return null
	}

	/**
	 * 使用顶层方法的最小源码行号。
	 */
	private fun setClassSourceLine(cls: ClassNode) {
		for (innerClass in cls.innerClasses) {
			setClassSourceLine(innerClass)
		}
		var minLine = 0
		for (mth in cls.methods) {
			minLine = updateMinLine(minLine, mth)
		}
		for (innerClass in cls.innerClasses) {
			minLine = updateMinLine(minLine, innerClass)
		}
		for (field in cls.fields) {
			minLine = updateMinLine(minLine, field)
		}
		if (minLine != 0) {
			cls.setSourceLine(minLine - 1)
		}
	}

	private fun updateMinLine(current: Int, node: LineAttrNode): Int {
		if (node.contains(AFlag.DONT_GENERATE)) {
			return current
		}
		val line = node.sourceLine
		if (line == 0) {
			return current
		}
		return if (current == 0 || line < current) line else current
	}

	private fun collectFieldsUsageInAnnotations(cls: ClassNode) {
		var useMth = cls.defaultConstructor
		if (useMth == null && cls.methods.isNotEmpty()) {
			useMth = cls.methods[0]
		}
		if (useMth == null) {
			return
		}
		collectFieldsUsageInAnnotations(useMth, cls)
		val finalUseMth = useMth
		for (f in cls.fields) {
			collectFieldsUsageInAnnotations(finalUseMth, f)
		}
	}

	private fun collectFieldsUsageInAnnotations(mth: MethodNode, attrNode: AttrNode) {
		val annotationsList = attrNode.get(KadxAttrType.ANNOTATION_LIST) ?: return
		for (annotation in annotationsList.all) {
			if (annotation.visibility == AnnotationVisibility.SYSTEM) {
				continue
			}
			for ((_, value) in annotation.values) {
				checkEncodedValue(mth, value)
			}
		}
	}

	private fun checkEncodedValue(mth: MethodNode, encodedValue: EncodedValue) {
		when (encodedValue.type) {
			kadx.api.plugins.input.data.annotations.EncodedType.ENCODED_FIELD -> {
				val fieldData = encodedValue.value
				val fieldInfo: FieldInfo = if (fieldData is IFieldRef) {
					FieldInfo.fromRef(mth.root(), fieldData)
				} else {
					fieldData as FieldInfo
				}
				val fieldNode = mth.root().resolveField(fieldInfo)
				if (fieldNode != null) {
					fieldNode.addUseIn(mth)
				}
			}

			kadx.api.plugins.input.data.annotations.EncodedType.ENCODED_ANNOTATION -> {
				val annotation = encodedValue.value as IAnnotation
				for ((_, v) in annotation.values) {
					checkEncodedValue(mth, v)
				}
			}

			kadx.api.plugins.input.data.annotations.EncodedType.ENCODED_ARRAY -> {
				@Suppress("UNCHECKED_CAST")
				val valueList = encodedValue.value as List<EncodedValue>
				for (v in valueList) {
					checkEncodedValue(mth, v)
				}
			}

			else -> {}
		}
	}

	private fun addNullCasts(mth: MethodNode, block: BlockNode) {
		for (insn in block.instructions) {
			when (insn.type) {
				InsnType.INVOKE -> verifyNullCast(mth, (insn as InvokeNode).getInstanceArg())
				InsnType.ARRAY_LENGTH -> verifyNullCast(mth, insn.getArg(0))
				else -> {}
			}
		}
	}

	private fun verifyNullCast(mth: MethodNode, arg: InsnArg?) {
		if (arg != null && arg.isZeroConst()) {
			val castType = arg.getType()
			val castInsn = IndexInsnNode(InsnType.CAST, castType, 1)
			castInsn.addArg(InsnArg.lit(0, castType))
			arg.wrapInstruction(mth, castInsn)
		}
	}
}
