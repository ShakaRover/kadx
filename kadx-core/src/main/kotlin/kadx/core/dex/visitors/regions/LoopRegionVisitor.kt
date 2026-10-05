package kadx.core.dex.visitors.regions

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.instructions.ArithNode
import kadx.core.dex.instructions.ArithOp
import kadx.core.dex.instructions.IfOp
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.InvokeType
import kadx.core.dex.instructions.PhiInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.conditions.IfCondition
import kadx.core.dex.regions.loops.ForEachLoop
import kadx.core.dex.regions.loops.ForLoop
import kadx.core.dex.regions.loops.LoopRegion
import kadx.core.dex.regions.loops.LoopType
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.dex.visitors.regions.variables.ProcessVariables
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.InsnUtils
import kadx.core.utils.RegionUtils
import kadx.core.utils.exceptions.KadxOverflowException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 把 `while` 循环还原成更易读的 `for` 循环（索引式或 for-each 式）。
 *
 * **算法意图**：Java 的增强 for / 索引 for 在字节码里都是 `while` + 条件跳转 + 自增。
 * 本访问器识别这些模式：
 * - 索引循环：循环变量由 phi 合并，条件里与数组长度比较；
 * - 数组 for-each：`arr[i]` 取值，条件 `i < arr.length`；
 * - 可迭代对象 for-each：`iterator()` / `hasNext()` / `next()` 调用模式。
 * 识别成功后把循环类型设为 [ForLoop] 或 [ForEachLoop]，并标记原始指令不再生成。
 *
 * Kotlin 转换说明：
 * - 原 Java 的 `==` 对象引用比较（`compare.getA() != condArg` 等）改为 `===`；
 * - 静态辅助方法统一放入 companion；
 * - 热点方法保持普通 `for` 循环，不用集合算子。
 */
@KadxVisitor(
	name = "LoopRegionVisitor",
	desc = "Convert 'while' loops to 'for' loops (indexed or for-each)",
	runBefore = [ProcessVariables::class],
)
class LoopRegionVisitor :
	AbstractVisitor(),
	IRegionVisitor {

	override fun visit(mth: MethodNode) {
		DepthRegionTraversal.traverse(mth, this)
		IfRegionVisitor.processIfRequested(mth)
	}

	override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
		if (region is LoopRegion) {
			if (processLoopRegion(mth, region)) {
				// 指令被移除后，重新优化 if 块
				mth.add(AFlag.REQUEST_IF_REGION_OPTIMIZE)
			}
		}
		return true
	}

	override fun leaveRegion(mth: MethodNode, region: IRegion) {
	}

	override fun processBlock(mth: MethodNode, container: IBlock) {
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(LoopRegionVisitor::class.java)

		private fun processLoopRegion(mth: MethodNode, loopRegion: LoopRegion): Boolean {
			if (loopRegion.isConditionAtEnd) {
				return false
			}
			val condition = loopRegion.condition ?: return false
			if (checkForIndexedLoop(mth, loopRegion, condition)) {
				return true
			}
			return checkIterableForEach(mth, loopRegion, condition)
		}

		/** 识别“索引式 for 循环”：循环变量在 phi 中合并，末尾自增。 */
		private fun checkForIndexedLoop(mth: MethodNode, loopRegion: LoopRegion, condition: IfCondition): Boolean {
			val loopEndBlock = loopRegion.info.end
			val incrInsn = BlockUtils.getLastInsn(BlockUtils.skipSyntheticPredecessor(loopEndBlock)) ?: return false
			val incrArg = incrInsn.result ?: return false
			val incrSVar = incrArg.sVar ?: return false
			if (!incrSVar.isUsedInPhi()) {
				return false
			}
			val phiInsnList = incrSVar.usedInPhi
			if (phiInsnList.size != 1) {
				return false
			}
			val phiInsn = phiInsnList[0]
			if (phiInsn.argsCount != 2 ||
				!phiInsn.containsVar(incrArg) ||
				incrSVar.useCount != 1
			) {
				return false
			}
			val arg = phiInsn.result ?: return false
			val condArgs = condition.registerArgs
			if (!condArgs.contains(arg) || checkNotNull(arg.sVar).isUsedInPhi()) {
				return false
			}
			val initArg = phiInsn.getArg(0)
			val initInsn = initArg.assignInsn
			if (initInsn == null ||
				initInsn.contains(AFlag.DONT_GENERATE) ||
				checkNotNull(initArg.sVar).useCount != 1
			) {
				return false
			}
			if (!usedOnlyInLoop(mth, loopRegion, arg)) {
				return false
			}
			// 若自增指令的参数在循环中被重新赋值，则不能构造 for 循环
			val args = ArrayList<RegisterArg>()
			incrInsn.getRegisterArgs(args)
			for (iArg in args) {
				try {
					if (assignOnlyInLoop(mth, loopRegion, iArg)) {
						return false
					}
				} catch (error: StackOverflowError) {
					// 深递归（超大方法）无法确认自增参数是否仅在循环内赋值：
					// 放弃构造 for 循环，退化为 while 渲染——上游在此抛出导致方法失败
					mth.addWarnComment("assignOnlyInLoop deep recursion, skip for-loop construction")
					return false
				}
			}

			// 所有检查通过
			initInsn.add(AFlag.DONT_GENERATE)
			incrInsn.add(AFlag.DONT_GENERATE)

			val arrForEach = checkArrayForEach(mth, loopRegion, initInsn, incrInsn, condition)
			loopRegion.setType(arrForEach ?: ForLoop(initInsn, incrInsn))
			return true
		}

		/** 在索引循环基础上进一步识别“数组 for-each”：`for (T x : arr)` */
		private fun checkArrayForEach(
			mth: MethodNode,
			loopRegion: LoopRegion,
			initInsn: InsnNode,
			incrInsn: InsnNode,
			condition: IfCondition,
		): LoopType? {
			if (incrInsn !is ArithNode) {
				return null
			}
			if (incrInsn.op != ArithOp.ADD) {
				return null
			}
			val lit = incrInsn.getArg(1)
			if (!lit.isLiteral || (lit as LiteralArg).literal != 1L) {
				return null
			}
			if (initInsn.type != InsnType.CONST ||
				!initInsn.getArg(0).isLiteral ||
				(initInsn.getArg(0) as LiteralArg).literal != 0L
			) {
				return null
			}

			var condArg = incrInsn.getArg(0)
			if (!condArg.isRegister) {
				return null
			}
			val sVar = checkNotNull((condArg as RegisterArg).sVar)
			val args = sVar.useList
			if (args.size != 3) {
				return null
			}
			condArg = InsnUtils.getRegFromInsn(args, InsnType.IF) ?: return null
			val arrIndex = InsnUtils.getRegFromInsn(args, InsnType.AGET) ?: return null
			val arrGetInsn = arrIndex.getParentInsn()
			if (arrGetInsn == null || arrGetInsn.containsWrappedInsn()) {
				return null
			}
			if (!condition.isCompare()) {
				return null
			}
			val compare = checkNotNull(condition.compare)
			if (compare.op != IfOp.LT || compare.a !== condArg) {
				return null
			}
			val len: InsnNode
			val bCondArg = compare.b
			if (bCondArg.isInsnWrap) {
				len = (bCondArg as InsnWrapArg).wrapInsn
			} else if (bCondArg.isRegister) {
				len = (bCondArg as RegisterArg).assignInsn ?: return null
			} else {
				return null
			}
			if (len.type != InsnType.ARRAY_LENGTH) {
				return null
			}
			val arrayArg = len.getArg(0)
			if (arrayArg != arrGetInsn.getArg(0)) {
				return null
			}
			var iterVar: RegisterArg? = arrGetInsn.result
			if (iterVar != null) {
				if (!usedOnlyInLoop(mth, loopRegion, iterVar)) {
					return null
				}
			} else {
				if (!arrGetInsn.contains(AFlag.WRAPPED)) {
					return null
				}
				// 创建新变量并替换被内联的指令
				val wrapArg = BlockUtils.searchWrappedInsnParent(mth, arrGetInsn)
				if (wrapArg == null || wrapArg.getParentInsn() == null) {
					mth.addWarnComment("checkArrayForEach: Wrapped insn not found: " + arrGetInsn)
					return null
				}
				iterVar = mth.makeSyntheticRegArg(wrapArg.getType())
				val parentInsn = checkNotNull(wrapArg.getParentInsn())
				parentInsn.replaceArg(wrapArg, iterVar.duplicate())
				parentInsn.rebindArgs()
			}

			// 确认是数组 for-each
			checkNotNull(incrInsn.result).add(AFlag.DONT_GENERATE)
			condArg.add(AFlag.DONT_GENERATE)
			bCondArg.add(AFlag.DONT_GENERATE)
			arrGetInsn.add(AFlag.DONT_GENERATE)
			compare.insn.add(AFlag.DONT_GENERATE)

			val forEachLoop = ForEachLoop(checkNotNull(iterVar), len.getArg(0))
			forEachLoop.injectFakeInsns(loopRegion)
			if (InsnUtils.dontGenerateIfNotUsed(len)) {
				InsnRemover.remove(mth, len)
			}
			CodeShrinkVisitor.shrinkMethod(mth)
			return forEachLoop
		}

		/** 识别“可迭代对象 for-each”：`iterator()` / `hasNext()` / `next()` 调用模式 */
		private fun checkIterableForEach(mth: MethodNode, loopRegion: LoopRegion, condition: IfCondition): Boolean {
			val condArgs = condition.registerArgs
			if (condArgs.size != 1) {
				return false
			}
			val iteratorArg = condArgs[0]
			val sVar = iteratorArg.sVar
			if (sVar == null || sVar.isUsedInPhi()) {
				return false
			}
			val itUseList = sVar.useList
			val assignInsn = iteratorArg.assignInsn
			if (itUseList.size != 2) {
				return false
			}
			if (!checkInvoke(assignInsn, null, "iterator()Ljava/util/Iterator;")) {
				return false
			}
			val iterableArg = checkNotNull(assignInsn).getArg(0)
			val hasNextCall = itUseList[0].getParentInsn()
			val nextCall = itUseList[1].getParentInsn()
			if (!checkInvoke(hasNextCall, "java.util.Iterator", "hasNext()Z") ||
				!checkInvoke(nextCall, "java.util.Iterator", "next()Ljava/lang/Object;")
			) {
				return false
			}
			val toSkip = ArrayList<InsnNode>()
			var iterVar: RegisterArg? = null
			if (checkNotNull(nextCall).contains(AFlag.WRAPPED)) {
				val wrapArg = BlockUtils.searchWrappedInsnParent(mth, nextCall)
				if (wrapArg != null && wrapArg.getParentInsn() != null) {
					val parentInsn = checkNotNull(wrapArg.getParentInsn())
					val block = BlockUtils.getBlockByInsn(mth, parentInsn) ?: return false
					if (!RegionUtils.isRegionContainsBlock(loopRegion, block)) {
						return false
					}
					if (parentInsn.type == InsnType.CHECK_CAST) {
						val res = parentInsn.result ?: return false
						if (!fixIterableType(mth, iterableArg, res)) {
							return false
						}
						iterVar = res
						val castArg = BlockUtils.searchWrappedInsnParent(mth, parentInsn)
						if (castArg != null && castArg.getParentInsn() != null) {
							checkNotNull(castArg.getParentInsn()).replaceArg(castArg, res)
						} else {
							// cast 未被内联
							toSkip.add(parentInsn)
						}
					} else {
						val res = nextCall.result ?: return false
						res.remove(AFlag.REMOVE) // 从被内联的指令中恢复变量
						nextCall.add(AFlag.DONT_GENERATE)
						if (!fixIterableType(mth, iterableArg, res)) {
							return false
						}
						parentInsn.replaceArg(wrapArg, res)
						iterVar = res
					}
				} else {
					LOG.warn(" checkIterableForEach: Wrapped insn not found: {}, mth: {}", nextCall, mth)
					return false
				}
			} else {
				val res = checkNotNull(nextCall).result ?: return false
				if (!usedOnlyInLoop(mth, loopRegion, res)) {
					return false
				}
				if (!assignOnlyInLoop(mth, loopRegion, res)) {
					return false
				}
				toSkip.add(nextCall)
				iterVar = res
			}

			checkNotNull(assignInsn).add(AFlag.DONT_GENERATE)
			checkNotNull(checkNotNull(assignInsn).result).add(AFlag.DONT_GENERATE)

			for (insnNode in toSkip) {
				insnNode.setResult(null)
				insnNode.add(AFlag.DONT_GENERATE)
			}
			for (itArg in itUseList) {
				itArg.add(AFlag.DONT_GENERATE)
			}
			val forEachLoop = ForEachLoop(checkNotNull(iterVar), iterableArg)
			forEachLoop.injectFakeInsns(loopRegion)
			loopRegion.setType(forEachLoop)
			return true
		}

		/** 修正 iterable 的泛型类型，使其与循环变量类型一致 */
		private fun fixIterableType(mth: MethodNode, iterableArg: InsnArg, iterVar: RegisterArg): Boolean {
			val iterableType = iterableArg.getType()
			val varType = iterVar.getType()
			if (iterableType.isGeneric()) {
				val genericTypes = iterableType.getGenericTypes()
				if (genericTypes == null || genericTypes.size != 1) {
					return false
				}
				val gType = genericTypes[0]
				if (gType == varType) {
					return true
				}
				if (gType.isGenericType()) {
					iterVar.setType(gType)
					return true
				}
				if (ArgType.isInstanceOf(mth.root(), gType, varType)) {
					return true
				}
				val wildcardType = gType.getWildcardType()
				if (wildcardType != null &&
					gType.getWildcardBound() == ArgType.WildcardBound.EXTENDS &&
					ArgType.isInstanceOf(mth.root(), wildcardType, varType)
				) {
					return true
				}
				LOG.warn("Generic type differs: '{}' and '{}' in {}", gType, varType, mth)
				return false
			}
			if (!iterableArg.isRegister || !iterableType.isObject()) {
				return true
			}
			val genericType = ArgType.generic(iterableType.getObject(), varType)
			if (iterableArg.isRegister) {
				val immutableType = (iterableArg as RegisterArg).immutableType
				if (immutableType != null && immutableType != genericType) {
					// 类型不可变；只允许对 Object 变量遍历非泛型集合
					return varType == ArgType.OBJECT
				}
			}
			iterableArg.setType(genericType)
			return true
		}

		/** 判断指令是否为指定接口/虚方法调用 */
		private fun checkInvoke(insn: InsnNode?, declClsFullName: String?, mthId: String): Boolean {
			if (insn == null) {
				return false
			}
			if (insn.type == InsnType.INVOKE) {
				val inv = insn as InvokeNode
				val callMth = inv.callMth
				if ((inv.invokeType == InvokeType.INTERFACE || inv.invokeType == InvokeType.VIRTUAL) &&
					callMth.shortId == mthId
				) {
					if (declClsFullName == null) {
						return true
					}
					return callMth.declClass.fullName == declClsFullName
				}
			}
			return false
		}

		/** 判断变量的赋值是否都发生在循环内部 */
		private fun assignOnlyInLoop(mth: MethodNode, loopRegion: LoopRegion, arg: RegisterArg): Boolean {
			val assignInsn = arg.assignInsn ?: return true
			if (!argInLoop(mth, loopRegion, checkNotNull(assignInsn.result))) {
				return false
			}
			if (assignInsn is PhiInsn) {
				for (phiArg in assignInsn.getArguments()) {
					if (!assignOnlyInLoop(mth, loopRegion, phiArg as RegisterArg)) {
						return false
					}
				}
			}
			return true
		}

		/** 判断变量的使用是否都发生在循环内部 */
		private fun usedOnlyInLoop(mth: MethodNode, loopRegion: LoopRegion, arg: RegisterArg): Boolean {
			val useList = checkNotNull(arg.sVar).useList
			for (useArg in useList) {
				if (!argInLoop(mth, loopRegion, useArg)) {
					return false
				}
			}
			return true
		}

		private fun argInLoop(mth: MethodNode, loopRegion: LoopRegion, arg: RegisterArg): Boolean {
			val parentInsn = arg.getParentInsn() ?: return false
			val block = BlockUtils.getBlockByInsn(mth, parentInsn)
			if (block == null) {
				LOG.debug(" LoopRegionVisitor: instruction not found: {}, mth: {}", parentInsn, mth)
				return false
			}
			return RegionUtils.isRegionContainsBlock(loopRegion, block)
		}
	}
}
