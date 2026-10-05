package kadx.core.dex.visitors

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.MethodInlineAttr
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.BaseInvokeNode
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.IMethodDetails
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.ListUtils
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.ArrayList

/**
 * 内联方法（对 [MarkMethodsForInline] 标记过的方法执行实际内联）。
 *
 * **做什么**：遍历方法中的 invoke 指令，若被调用方法已被标记可内联，则复制其指令、
 * 把调用实参重映射到被调方法的形参寄存器、替换调用点，并更新使用信息（useIn）。
 *
 * **为什么在类型推导之后、ModVisitor 之前**：内联后指令结构变化，需要类型推导的结果来保证正确。
 */
@KadxVisitor(
	name = "InlineMethods",
	desc = "Inline methods (previously marked in MarkMethodsForInline)",
	runAfter = [TypeInferenceVisitor::class],
	runBefore = [ModVisitor::class],
)
class InlineMethods : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (block in checkNotNull(mth.basicBlocks)) {
			for (insn in block.instructions) {
				if (insn.type == InsnType.INVOKE) {
					processInvokeInsn(mth, block, insn as InvokeNode)
				}
			}
		}
	}

	private fun processInvokeInsn(mth: MethodNode, block: BlockNode, insn: InvokeNode) {
		val callMthDetails = insn.get(AType.METHOD_DETAILS)
		if (callMthDetails !is MethodNode) {
			return
		}
		val callMth = callMthDetails
		try {
			var mia = MarkMethodsForInline.process(callMth)
			if (mia == null) {
				// 方法尚未加载 => 强制处理
				mth.addDebugComment("Class process forced to load method for inline: $callMth")
				mth.root().getProcessClasses().forceProcess(callMth.parentClass)
				// 再次检查
				mia = MarkMethodsForInline.process(callMth)
				if (mia == null) {
					mth.addWarnComment("Failed to check method for inline after forced process$callMth")
					return
				}
			}
			if (mia.notNeeded()) {
				return
			}
			inlineMethod(mth, callMth, mia, block, insn)
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to process method for inline: $callMth", e)
		}
	}

	private fun inlineMethod(
		mth: MethodNode,
		callMth: MethodNode,
		mia: MethodInlineAttr,
		block: BlockNode,
		insn: InvokeNode,
	) {
		val inlCopy = checkNotNull(mia.insn).copyWithoutResult<InsnNode>()
		if (replaceRegs(mth, callMth, mia, insn, inlCopy)) {
			val methodDetailsAttr = inlCopy.get(AType.METHOD_DETAILS)
			// replaceInsn 会一并替换属性，需确保保留 METHOD_DETAILS
			if (BlockUtils.replaceInsn(mth, block, insn, inlCopy)) {
				if (methodDetailsAttr != null) {
					inlCopy.addAttr(methodDetailsAttr)
				}
				updateUsageInfo(mth, callMth, mia.insn)
				return
			}
		}
		mth.addWarnComment("Failed to inline method: $callMth")
		// 撤销对 insn 的修改
		InsnRemover.unbindInsn(mth, inlCopy)
		insn.rebindArgs()
	}

	private fun replaceRegs(
		mth: MethodNode,
		callMth: MethodNode,
		mia: MethodInlineAttr,
		insn: InvokeNode,
		inlCopy: InsnNode,
	): Boolean {
		try {
			if (callMth.methodInfo.argumentsTypes.isNotEmpty()) {
				// 重映射参数
				val regs = arrayOfNulls<InsnArg>(callMth.getRegsCount())
				val regNums = checkNotNull(mia.argsRegNums)
				for (i in regNums.indices) {
					val arg = insn.getArg(i)
					regs[regNums[i]] = arg
				}
				// 替换参数
				val inlArgs = ArrayList<RegisterArg>()
				inlCopy.getRegisterArgs(inlArgs)
				for (r in inlArgs) {
					val regNum = r.regNum
					if (regNum >= regs.size) {
						mth.addWarnComment("Unknown register number '$r' in method call: $callMth")
						return false
					}
					val repl = regs[regNum]
					if (repl == null) {
						mth.addWarnComment("Not passed register '$r' in method call: $callMth")
						return false
					}
					if (!inlCopy.replaceArg(r, repl.duplicate())) {
						mth.addWarnComment("Failed to replace arg $r for method inline: $callMth")
						return false
					}
				}
			}
			val resultArg = insn.result
			if (resultArg != null) {
				inlCopy.setResult(resultArg.duplicate())
			} else if (isAssignNeeded(mia.insn, insn, callMth)) {
				// 添加伪结果以生成正确的 java 表达式（见测试 TestGetterInlineNegative）
				inlCopy.setResult(mth.makeSyntheticRegArg(callMth.returnType, "unused"))
			}
			return true
		} catch (e: Exception) {
			mth.addWarnComment("Method inline failed with exception", e)
			return false
		}
	}

	private fun isAssignNeeded(inlineInsn: InsnNode?, parentInsn: InvokeNode, callMthNode: MethodNode): Boolean {
		if (parentInsn.result != null) {
			return false
		}
		if (parentInsn.contains(AFlag.WRAPPED)) {
			return false
		}
		if (inlineInsn != null && inlineInsn.type == InsnType.IPUT) {
			return false
		}
		return !callMthNode.isVoidReturn()
	}

	private fun updateUsageInfo(mth: MethodNode, inlinedMth: MethodNode, insn: InsnNode?) {
		val newUseIn = ArrayList(inlinedMth.useIn)
		newUseIn.remove(mth)
		inlinedMth.setUseIn(newUseIn)
		insn?.visitInsns(
			{ innerInsn ->
				// TODO: 与 UsageInfoVisitor 共享代码
				when (innerInsn.type) {
					InsnType.INVOKE, InsnType.CONSTRUCTOR -> {
						val callMth: MethodInfo = (innerInsn as BaseInvokeNode).callMth
						val callMthNode = mth.root().resolveMethod(callMth)
						if (callMthNode != null) {
							callMthNode.setUseIn(ListUtils.safeReplace(ArrayList(callMthNode.useIn), inlinedMth, mth))
							replaceClsUsage(mth, inlinedMth, callMthNode.parentClass)
						}
					}

					InsnType.IGET, InsnType.IPUT, InsnType.SPUT, InsnType.SGET -> {
						val fieldInfo = (innerInsn as IndexInsnNode).index as FieldInfo
						val fieldNode: FieldNode? = mth.root().resolveField(fieldInfo)
						if (fieldNode != null) {
							fieldNode.setUseIn(ListUtils.safeReplace(ArrayList(fieldNode.useIn), inlinedMth, mth))
							replaceClsUsage(mth, inlinedMth, fieldNode.parentClass)
						}
					}

					else -> {}
				}
			},
		)
	}

	private fun replaceClsUsage(mth: MethodNode, inlinedMth: MethodNode, parentClass: ClassNode) {
		parentClass.useInMth = ListUtils.safeReplace(parentClass.useInMth, inlinedMth, mth)
		parentClass.useInValue = ListUtils.safeReplace(parentClass.useIn, inlinedMth.parentClass, mth.parentClass)
	}
}
