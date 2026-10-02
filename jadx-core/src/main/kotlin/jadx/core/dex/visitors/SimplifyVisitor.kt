package jadx.core.dex.visitors

import jadx.core.Consts
import jadx.core.codegen.TypeGen
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.ArithNode
import jadx.core.dex.instructions.ArithOp
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.FilledNewArrayNode
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InvokeType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.dex.visitors.typeinference.TypeCompareEnum
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnList
import jadx.core.utils.InsnRemover
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.ArrayList

/**
 * 指令级化简：常量、cast、字符串拼接、字段算术等。
 *
 * **做什么**：
 * - 把 `move 常量` 变成 `const`；
 * - 去掉多余的 cast（重复 cast、被外层 cast 覆盖的 cast、不需要的 cast）；
 * - 把 `new StringBuilder(...).append(...).toString()` 合并成字符串拼接指令；
 * - 简化算术（`c + (-1)` → `c - 1`、布尔 `xor`）；
 * - 把 `iput(arith(iget, x))` 转成复合赋值 `x += ...`。
 *
 * **为什么需要它**：Java 编译器生成的字节码大量使用 StringBuilder 拼接和冗余 cast，
 * 不化简会得到非常难读的反编译代码。
 */
class SimplifyVisitor : AbstractVisitor() {

	private lateinit var stringGetBytesMth: MethodInfo

	override fun init(root: RootNode) {
		stringGetBytesMth = MethodInfo.fromDetails(
			root,
			ClassInfo.fromType(root, ArgType.STRING),
			"getBytes",
			emptyList(),
			ArgType.array(ArgType.BYTE),
		)
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		var changed = false
		for (block in checkNotNull(mth.getBasicBlocks())) {
			if (simplifyBlock(mth, block)) {
				changed = true
			}
		}
		if (changed || mth.contains(AFlag.REQUEST_CODE_SHRINK)) {
			CodeShrinkVisitor.shrinkMethod(mth)
		}
	}

	private fun simplifyBlock(mth: MethodNode, block: BlockNode): Boolean {
		var changed = false
		val list = block.instructions
		var i = 0
		while (i < list.size) {
			val insn = list[i]
			val insnCount = list.size
			val modInsn = simplifyInsn(mth, insn, null)
			if (modInsn != null) {
				if (i < list.size && list[i] === insn) {
					list[i] = modInsn
				} else {
					val idx = InsnList.getIndex(list, insn)
					if (idx == -1) {
						throw JadxRuntimeException("Failed to replace insn")
					}
					list[idx] = modInsn
				}
				InsnRemover.unbindInsn(mth, insn)
				modInsn.rebindArgs()
				if (list.size < insnCount) {
					// 有指令被移除 => 重新扫描整个块
					simplifyBlock(mth, block)
					return true
				}
				changed = true
			}
			i++
		}
		return changed
	}

	private fun simplifyArgs(mth: MethodNode, insn: InsnNode) {
		var changed = false
		for (arg in insn.getArguments()) {
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				val replaceInsn = simplifyInsn(mth, wrapInsn, insn)
				if (replaceInsn != null) {
					arg.wrapInstruction(mth, replaceInsn)
					changed = true
				}
			}
		}
		if (changed) {
			insn.rebindArgs()
			mth.add(AFlag.REQUEST_CODE_SHRINK)
		}
	}

	private fun simplifyInsn(mth: MethodNode, insn: InsnNode, parentInsn: InsnNode?): InsnNode? {
		if (insn.contains(AFlag.DONT_GENERATE)) {
			return null
		}
		simplifyArgs(mth, insn)
		when (insn.getType()) {
			InsnType.ARITH -> return simplifyArith(insn as ArithNode)

			InsnType.IF -> simplifyIf(mth, insn as IfNode)

			InsnType.TERNARY -> simplifyTernary(mth, insn as TernaryInsn)

			InsnType.INVOKE -> return convertInvoke(mth, insn as InvokeNode)

			InsnType.IPUT, InsnType.SPUT -> return convertFieldArith(mth, insn)

			InsnType.CAST, InsnType.CHECK_CAST -> return processCast(mth, insn as IndexInsnNode, parentInsn)

			InsnType.MOVE -> {
				val firstArg = insn.getArg(0)
				if (firstArg.isLiteral) {
					val constInsn = InsnNode(InsnType.CONST, 1)
					constInsn.setResult(insn.getResult())
					constInsn.addArg(firstArg)
					constInsn.copyAttributesFrom(insn)
					return constInsn
				}
			}

			InsnType.CONSTRUCTOR -> return simplifyStringConstructor(mth, insn as ConstructorInsn)

			else -> {}
		}
		return null
	}

	private fun simplifyStringConstructor(mth: MethodNode, insn: ConstructorInsn): InsnNode? {
		if (insn.callMth.declClass.type == ArgType.STRING &&
			insn.getArgsCount() != 0 &&
			insn.getArg(0).isInsnWrap
		) {
			val arrInsn = (insn.getArg(0) as InsnWrapArg).wrapInsn
			if (arrInsn.getType() == InsnType.FILLED_NEW_ARRAY && arrInsn.getArgsCount() != 0) {
				val elemType = (arrInsn as FilledNewArrayNode).elemType
				if (elemType == ArgType.BYTE || elemType == ArgType.CHAR) {
					var printable = 0
					val arr = ByteArray(arrInsn.getArgsCount())
					for (i in arr.indices) {
						val arrArg = arrInsn.getArg(i)
						if (!arrArg.isLiteral) {
							return null
						}
						arr[i] = (arrArg as LiteralArg).literal.toByte()
						if (NameMapper.isPrintableChar(arr[i].toInt().toChar())) {
							printable++
						}
					}
					if (printable >= arr.size - printable) {
						val constStr = ConstStringNode(String(arr))
						if (insn.getArgsCount() == 1) {
							constStr.setResult(insn.getResult())
							constStr.copyAttributesFrom(insn)
							InsnRemover.unbindArgUsage(mth, insn.getArg(0))
							return constStr
						} else {
							val `in` = InvokeNode(stringGetBytesMth, InvokeType.VIRTUAL, 1)
							`in`.addArg(InsnArg.wrapArg(constStr))
							val bytesArg = InsnArg.wrapArg(`in`)
							bytesArg.setType(stringGetBytesMth.returnType)
							insn.setArg(0, bytesArg)
							return null
						}
					}
				}
			}
		}
		return null
	}

	private fun processCast(mth: MethodNode, castInsn: IndexInsnNode, parentInsn: InsnNode?): InsnNode? {
		if (castInsn.contains(AFlag.EXPLICIT_CAST)) {
			return null
		}
		val castArg = castInsn.getArg(0)
		var argType = castArg.getType()

		// 若 wrapped INVOKE 返回不同类型，不要移除 CHECK_CAST
		if (castArg.isInsnWrap) {
			val wrapInsn = (castArg as InsnWrapArg).wrapInsn
			if (wrapInsn.getType() == InsnType.INVOKE) {
				argType = (wrapInsn as InvokeNode).callMth.returnType
			}
		}

		val castToType = castInsn.index as ArgType
		if (isArithWideUpCast(parentInsn, argType, castToType)) {
			return null
		}
		if (!ArgType.isCastNeeded(mth.root(), argType, castToType) ||
			isCastDuplicate(castInsn) ||
			shadowedByOuterCast(mth.root(), castToType, parentInsn)
		) {
			val insnNode = InsnNode(InsnType.MOVE, 1)
			insnNode.setOffset(castInsn.getOffset())
			insnNode.setResult(InsnNode.duplicateArg(castInsn.getResult()))
			insnNode.addArg(castArg.duplicate())
			return insnNode
		}
		return null
	}

	/**
	 * 算术指令中要保留到宽类型的 cast，因为参数类型决定字节码用哪条指令。
	 * 例：`(long) i << 32`，若去掉 `long` cast 会用 int 移位指令，结果错误。
	 */
	private fun isArithWideUpCast(parentInsn: InsnNode?, argType: ArgType, castToType: ArgType): Boolean {
		if (parentInsn != null && parentInsn.getType() == InsnType.ARITH &&
			argType.isPrimitive() && castToType.isPrimitive()
		) {
			return castToType.getRegCount() > argType.getRegCount()
		}
		return false
	}

	private fun isCastDuplicate(castInsn: IndexInsnNode): Boolean {
		val arg = castInsn.getArg(0)
		if (arg.isRegister) {
			val sVar = (arg as RegisterArg).sVar
			if (sVar != null && sVar.getUseCount() == 1 && !sVar.isUsedInPhi()) {
				val assignInsn = sVar.assign.getParentInsn()
				if (assignInsn != null && assignInsn.getType() == InsnType.CHECK_CAST) {
					val assignCastType = (assignInsn as IndexInsnNode).index
					return assignCastType == castInsn.index
				}
			}
		}
		return false
	}

	private fun shadowedByOuterCast(root: RootNode, castType: ArgType, parentInsn: InsnNode?): Boolean {
		if (parentInsn != null && parentInsn.getType() == InsnType.CAST) {
			val parentCastType = (parentInsn as IndexInsnNode).index as ArgType
			val result = root.getTypeCompare().compareTypes(parentCastType, castType)
			return result.isNarrow()
		}
		return false
	}

	/**
	 * 简化 if 条件中的 `cmp` 指令。
	 */
	private fun simplifyIf(mth: MethodNode, insn: IfNode) {
		val f = insn.getArg(0)
		if (f.isInsnWrap) {
			val wi = (f as InsnWrapArg).wrapInsn
			if (wi.getType() == InsnType.CMP_L || wi.getType() == InsnType.CMP_G) {
				if (insn.getArg(1).isZeroLiteral()) {
					insn.changeCondition(insn.getOp(), wi.getArg(0).duplicate(), wi.getArg(1).duplicate())
					InsnRemover.unbindInsn(mth, wi)
				} else {
					LOG.warn("TODO: cmp {}", insn)
				}
			}
		}
	}

	/**
	 * 简化三元运算中的条件。
	 */
	private fun simplifyTernary(mth: MethodNode, insn: TernaryInsn) {
		val condition = insn.getCondition()
		if (condition.isCompare()) {
			simplifyIf(mth, checkNotNull(condition.getCompare()).getInsn())
		} else {
			insn.simplifyCondition()
		}
	}

	/**
	 * 简化 StringBuilder#append() 调用链 + StringBuilder 构造器。
	 * 这些链通常是 Java 编译器为字符串拼接（如 `"text " + 1 + " text"`）自动生成的。
	 */
	private fun convertInvoke(mth: MethodNode, insn: InvokeNode): InsnNode? {
		val callMth = insn.callMth

		if (callMth.declClass.fullName == Consts.CLASS_STRING_BUILDER &&
			callMth.shortId == Consts.MTH_TOSTRING_SIGNATURE
		) {
			val instanceArg = insn.getArg(0)
			if (instanceArg.isInsnWrap) {
				// 把 'new StringBuilder(xxx).append(yyy).append(zzz).toString()' 转成 STRING_CONCAT 指令
				val callChain = flattenInsnChainUntil(insn, InsnType.CONSTRUCTOR)
				return convertStringBuilderChain(mth, insn, callChain)
			}
			if (instanceArg.isRegister) {
				// 把 'StringBuilder sb = new StringBuilder(xxx); sb.append(yyy); String str = sb.toString();' 转成拼接
				val useChain = collectUseChain(mth, insn, instanceArg as RegisterArg)
				return convertStringBuilderChain(mth, insn, useChain)
			}
		}
		return null
	}

	private fun collectUseChain(mth: MethodNode, insn: InvokeNode, instanceArg: RegisterArg): List<InsnNode> {
		val sVar = checkNotNull(instanceArg.sVar)
		if (sVar.isUsedInPhi() || sVar.getUseCount() == 0) {
			return emptyList()
		}
		val useChain = ArrayList<InsnNode>(sVar.getUseCount() + 1)
		val assignInsn = sVar.assign.getParentInsn() ?: return emptyList()
		useChain.add(assignInsn)
		for (reg in sVar.getUseList()) {
			val parentInsn = reg.getParentInsn() ?: return emptyList()
			useChain.add(parentInsn)
		}
		val toStrIdx = InsnList.getIndex(useChain, insn)
		if (useChain.size - 1 != toStrIdx) {
			return emptyList()
		}
		useChain.removeAt(toStrIdx)

		// 所有指令必须在同一个块且顺序连续
		val assignBlock = BlockUtils.getBlockByInsn(mth, assignInsn) ?: return emptyList()
		val blockInsns = assignBlock.instructions
		val assignIdx = InsnList.getIndex(blockInsns, assignInsn)
		val chainSize = useChain.size
		val lastInsn = blockInsns.size - assignIdx
		if (lastInsn < chainSize) {
			return emptyList()
		}
		for (i in 1 until chainSize) {
			if (blockInsns[assignIdx + i] !== useChain[i]) {
				return emptyList()
			}
		}
		return useChain
	}

	private fun convertStringBuilderChain(mth: MethodNode, toStrInsn: InvokeNode, chain: List<InsnNode>): InsnNode? {
		try {
			val chainSize = chain.size
			if (chainSize < 2) {
				return null
			}
			val args = ArrayList<InsnArg>(chainSize)
			val firstInsn = chain[0]
			if (firstInsn.getType() != InsnType.CONSTRUCTOR) {
				return null
			}
			val constrInsn = firstInsn as ConstructorInsn
			if (constrInsn.getArgsCount() == 1) {
				val argType = constrInsn.callMth.argumentsTypes[0]
				if (!argType.isObject()) {
					return null
				}
				args.add(constrInsn.getArg(0))
			}
			for (i in 1 until chainSize) {
				val chainInsn = chain[i]
				val arg = getArgFromAppend(chainInsn) ?: return null
				args.add(arg)
			}

			var stringArgFound = false
			for (arg in args) {
				if (arg.getType() == ArgType.STRING) {
					stringArgFound = true
					break
				}
			}
			if (!stringArgFound) {
				val argStr = Utils.listToString(args) { it.toShortString() }
				mth.addDebugComment("TODO: convert one arg to string using `String.valueOf()`, args: $argStr")
				return null
			}

			// 所有检查通过
			val dupArgs = Utils.collectionMap(args) { it.duplicate() }
			val simplifiedArgs = concatConstArgs(dupArgs)
			val concatInsn = InsnNode(InsnType.STR_CONCAT, simplifiedArgs)
			concatInsn.add(AFlag.SYNTHETIC)
			if (toStrInsn.getResult() == null && !toStrInsn.contains(AFlag.WRAPPED)) {
				// 不赋值给变量的字符串拼接会导致编译错误
				concatInsn.setResult(mth.makeSyntheticRegArg(ArgType.STRING))
			} else {
				concatInsn.setResult(toStrInsn.getResult())
			}
			concatInsn.copyAttributesFrom(toStrInsn)
			removeStringBuilderInsns(mth, toStrInsn, chain)
			return concatInsn
		} catch (e: Exception) {
			mth.addWarnComment("String concatenation convert failed", e)
		}
		return null
	}

	private fun isConstConcatNeeded(args: List<InsnArg>): Boolean {
		var prevConst = false
		for (arg in args) {
			val curConst = arg.isConst()
			if (curConst && prevConst) {
				// 发现两个连续常量
				return true
			}
			prevConst = curConst
		}
		return false
	}

	private fun concatConstArgs(args: List<InsnArg>): List<InsnArg> {
		if (!isConstConcatNeeded(args)) {
			return args
		}
		val size = args.size
		val newArgs = ArrayList<InsnArg>(size)
		val concatList = ArrayList<String>(size)
		for (i in 0 until size) {
			val arg = args[i]
			val constStr = getConstString(arg)
			if (constStr != null) {
				concatList.add(constStr)
			} else {
				if (concatList.isNotEmpty()) {
					newArgs.add(getConcatArg(concatList, args, i))
					concatList.clear()
				}
				newArgs.add(arg)
			}
		}
		if (concatList.isNotEmpty()) {
			newArgs.add(getConcatArg(concatList, args, size))
		}
		return newArgs
	}

	private fun getConcatArg(concatList: List<String>, args: List<InsnArg>, idx: Int): InsnArg {
		if (concatList.size == 1) {
			return args[idx - 1]
		}
		val str = Utils.concatStrings(concatList)
		return InsnArg.wrapArg(ConstStringNode(str))
	}

	private fun getConstString(arg: InsnArg): String? {
		if (arg.isLiteral) {
			return TypeGen.literalToRawString(arg as LiteralArg)
		}
		if (arg.isInsnWrap) {
			val wrapInsn = (arg as InsnWrapArg).wrapInsn
			if (wrapInsn is ConstStringNode) {
				return wrapInsn.getString()
			}
		}
		return null
	}

	/**
	 * 移除并解绑所有 StringBuilder 相关指令。
	 */
	private fun removeStringBuilderInsns(mth: MethodNode, toStrInsn: InvokeNode, chain: List<InsnNode>) {
		InsnRemover.unbindAllArgs(mth, toStrInsn)
		for (insnNode in chain) {
			InsnRemover.unbindAllArgs(mth, insnNode)
		}
		val insnRemover = InsnRemover(mth)
		for (insnNode in chain) {
			if (insnNode !== toStrInsn) {
				insnRemover.addAndUnbind(insnNode)
			}
		}
		insnRemover.perform()
	}

	private fun flattenInsnChainUntil(insn: InsnNode, insnType: InsnType): List<InsnNode> {
		val chain = ArrayList<InsnNode>()
		var arg = insn.getArg(0)
		while (arg.isInsnWrap) {
			val wrapInsn = (arg as InsnWrapArg).wrapInsn
			chain.add(wrapInsn)
			if (wrapInsn.getType() == insnType || wrapInsn.getArgsCount() == 0) {
				break
			}
			arg = wrapInsn.getArg(0)
		}
		chain.reverse()
		return chain
	}

	private fun getArgFromAppend(chainInsn: InsnNode): InsnArg? {
		if (chainInsn.getType() == InsnType.INVOKE && chainInsn.getArgsCount() == 2) {
			val callMth = (chainInsn as InvokeNode).callMth
			if (callMth.declClass.fullName == Consts.CLASS_STRING_BUILDER &&
				callMth.name == "append"
			) {
				return chainInsn.getArg(1)
			}
		}
		return null
	}

	private fun simplifyArith(arith: ArithNode): InsnNode? {
		if (arith.getArgsCount() != 2) {
			return null
		}
		var litArg: LiteralArg? = null
		val secondArg = arith.getArg(1)
		if (secondArg.isInsnWrap) {
			val wr = (secondArg as InsnWrapArg).wrapInsn
			if (wr.getType() == InsnType.CONST) {
				val arg = wr.getArg(0)
				if (arg.isLiteral) {
					litArg = arg as LiteralArg
				}
			}
		} else if (secondArg.isLiteral) {
			litArg = secondArg as LiteralArg
		}
		if (litArg == null) {
			return null
		}
		when (arith.op) {
			ArithOp.ADD -> {
				// 把 'c + (-1)' 修正为 'c - (1)'
				if (litArg.isNegative()) {
					val negLitArg = litArg.negate()
					if (negLitArg != null) {
						val resArg = InsnNode.duplicateArg(arith.getResult())
						val newInsn = ArithNode(ArithOp.SUB, resArg, arith.getArg(0).duplicate(), negLitArg)
						newInsn.copyAttributesFrom(arith)
						newInsn.setOffset(arith.getOffset())
						return newInsn
					}
				}
			}

			ArithOp.XOR -> {
				// 简化布尔 xor
				val firstArg = arith.getArg(0)
				val lit = litArg.literal
				if (firstArg.getType() == ArgType.BOOLEAN && (lit == 0L || lit == 1L)) {
					val newInsn = InsnNode(if (lit == 0L) InsnType.MOVE else InsnType.NOT, 1)
					newInsn.setResult(InsnNode.duplicateArg(arith.getResult()))
					newInsn.addArg(firstArg.duplicate())
					newInsn.copyAttributesFrom(arith)
					newInsn.setOffset(arith.getOffset())
					return newInsn
				}
			}

			else -> {}
		}
		return null
	}

	/**
	 * 把字段算术操作转成算术指令
	 * (IPUT (ARITH (IGET, lit)) -> ARITH ((IGET)) <op>= lit))
	 */
	private fun convertFieldArith(mth: MethodNode, insn: InsnNode): ArithNode? {
		val arg = insn.getArg(0)
		if (!arg.isInsnWrap) {
			return null
		}
		val wrap = (arg as InsnWrapArg).wrapInsn
		val wrapType = wrap.getType()
		if ((wrapType != InsnType.ARITH && wrapType != InsnType.STR_CONCAT) || !wrap.getArg(0).isInsnWrap) {
			return null
		}
		val getWrap = wrap.getArg(0)
		val get = (getWrap as InsnWrapArg).wrapInsn
		val getType = get.getType()
		if (getType != InsnType.IGET && getType != InsnType.SGET) {
			return null
		}
		val field = (insn as IndexInsnNode).index as FieldInfo
		val innerField = (get as IndexInsnNode).index as FieldInfo
		if (field != innerField) {
			return null
		}
		try {
			if (getType == InsnType.IGET && insn.getType() == InsnType.IPUT) {
				val reg = get.getArg(0)
				val putReg = insn.getArg(1)
				if (reg != putReg) {
					return null
				}
			}
			val fArg = getWrap.duplicate()
			InsnRemover.unbindInsn(mth, get)
			if (insn.getType() == InsnType.IPUT) {
				InsnRemover.unbindArgUsage(mth, insn.getArg(1))
			}
			if (wrapType == InsnType.ARITH) {
				val ar = wrap as ArithNode
				val newInsn = ArithNode.oneArgOp(ar.op, fArg, ar.getArg(1).duplicate())
				newInsn.copyAttributesFrom(insn)
				newInsn.setOffset(insn.getOffset())
				return newInsn
			}
			val argsCount = wrap.getArgsCount()
			val concat = InsnNode(InsnType.STR_CONCAT, argsCount - 1)
			for (i in 1 until argsCount) {
				concat.addArg(wrap.getArg(i).duplicate())
			}
			val concatArg = InsnArg.wrapArg(concat)
			concatArg.setType(ArgType.STRING)
			val newInsn = ArithNode.oneArgOp(ArithOp.ADD, fArg, concatArg)
			newInsn.copyAttributesFrom(wrap)
			newInsn.setOffset(wrap.getOffset())
			return newInsn
		} catch (e: Exception) {
			LOG.debug("Can't convert field arith insn: {}, mth: {}", insn, mth, e)
		}
		return null
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(SimplifyVisitor::class.java)
	}
}
