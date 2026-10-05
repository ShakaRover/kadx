package kadx.core.dex.visitors.debuginfo

import kadx.api.plugins.input.data.AccessFlags
import kadx.api.plugins.input.data.ILocalVar
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.types.MethodParametersAttr
import kadx.core.Consts
import kadx.core.deobf.NameMapper
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.instructions.PhiInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.CodeVar
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.Named
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.dex.visitors.ssa.SSATransform
import kadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import kadx.core.dex.visitors.typeinference.TypeUpdateResult
import kadx.core.utils.BlockUtils
import kadx.core.utils.exceptions.KadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.HashSet

/**
 * 把调试信息（变量名与类型、行号）应用到寄存器上。
 *
 * **做什么**：读取方法上的 [kadx.core.dex.attributes.nodes.LocalVarsDebugInfoAttr]，
 * 为每个 SSA 变量寻找匹配的局部变量调试信息并套用其类型与名字；修正 splitter 产生的
 * `return` 指令行号；统一 phi 指令的变量名；最后处理方法参数名属性。
 *
 * **为什么**：调试信息能显著提升反编译可读性（真实变量名、准确类型），
 * 但必须与 SSA 变量精确对应，且类型不能与类型推导冲突（冲突时拒绝应用）。
 *
 * **Kotlin 转换说明**：`applyDebugInfo` 等静态方法放 companion + `@JvmStatic`；
 * stream 取最大值改为普通循环；引用比较用 `===`；`String?` 参数如实保留可空。
 */
@KadxVisitor(
	name = "Debug Info Apply",
	desc = "Apply debug info to registers (type and names)",
	runAfter = [
		SSATransform::class,
		TypeInferenceVisitor::class,
	],
)
class DebugInfoApplyVisitor : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		try {
			if (mth.contains(AType.LOCAL_VARS_DEBUG_INFO)) {
				applyDebugInfo(mth)
				mth.remove(AType.LOCAL_VARS_DEBUG_INFO)
			}
			processMethodParametersAttribute(mth)
		} catch (e: Exception) {
			mth.addWarnComment("Failed to apply debug info", e)
		}
	}

	private fun processMethodParametersAttribute(mth: MethodNode) {
		val parametersAttr = mth.get(KadxAttrType.METHOD_PARAMETERS) ?: return
		try {
			val params = parametersAttr.list
			if (params.size != mth.methodInfo.argsCount) {
				return
			}
			var i = 0
			for (mthArg in mth.argRegs) {
				val paramInfo = params[i++]
				val name = paramInfo.name
				if (NameMapper.isValidAndPrintable(name)) {
					val codeVar = checkNotNull(mthArg.sVar).codeVar
					codeVar.name = name
					if (AccessFlags.hasFlag(paramInfo.accFlags, AccessFlags.FINAL)) {
						codeVar.isFinal = true
					}
				}
			}
		} catch (e: Exception) {
			mth.addWarnComment("Failed to process method parameters attribute: " + parametersAttr.list, e)
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DebugInfoApplyVisitor::class.java)

		private fun applyDebugInfo(mth: MethodNode) {
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.info("Apply debug info for method: {}", mth)
			}
			for (ssaVar in mth.SVars) {
				searchAndApplyVarDebugInfo(mth, ssaVar)
			}

			fixLinesForReturn(mth)
			fixNamesForPhiInsns(mth)
		}

		private fun searchAndApplyVarDebugInfo(mth: MethodNode, ssaVar: SSAVar) {
			if (applyDebugInfo(mth, ssaVar, ssaVar.assign)) {
				return
			}
			for (useArg in ssaVar.useList) {
				if (applyDebugInfo(mth, ssaVar, useArg)) {
					return
				}
			}
			searchDebugInfoByOffset(mth, ssaVar)
		}

		private fun searchDebugInfoByOffset(mth: MethodNode, ssaVar: SSAVar) {
			val debugInfoAttr = mth.get(AType.LOCAL_VARS_DEBUG_INFO) ?: return
			val useList = ssaVar.useList
			if (useList.isEmpty()) {
				return
			}
			var maxOffset = Int.MIN_VALUE
			for (useArg in useList) {
				val offset = getInsnOffsetByArg(useArg)
				if (offset > maxOffset) {
					maxOffset = offset
				}
			}
			val startOffset = getInsnOffsetByArg(ssaVar.assign)
			val endOffset = maxOffset
			val regNum = ssaVar.regNum
			for (localVar in debugInfoAttr.localVars) {
				if (localVar.regNum == regNum) {
					val startAddr = localVar.startOffset
					val endAddr = localVar.endOffset
					if (isInside(startOffset, startAddr, endAddr) || isInside(endOffset, startAddr, endAddr)) {
						if (Consts.DEBUG_TYPE_INFERENCE) {
							LOG.debug("Apply debug info by offset for: {} to {}", ssaVar, localVar)
						}
						val type = DebugInfoAttachVisitor.getVarType(mth, localVar)
						applyDebugInfo(mth, ssaVar, type, localVar.name)
						break
					}
				}
			}
		}

		private fun isInside(v: Int, start: Int, end: Int): Boolean = start <= v && v <= end

		private fun getInsnOffsetByArg(arg: InsnArg?): Int {
			if (arg != null) {
				val insn = arg.getParentInsn()
				if (insn != null) {
					return insn.getOffset()
				}
			}
			return -1
		}

		fun applyDebugInfo(mth: MethodNode, ssaVar: SSAVar, arg: RegisterArg): Boolean {
			val debugInfoAttr = arg.get(AType.REG_DEBUG_INFO) ?: return false
			return applyDebugInfo(mth, ssaVar, debugInfoAttr.regType, debugInfoAttr.name)
		}

		fun applyDebugInfo(mth: MethodNode, ssaVar: SSAVar, type: ArgType, varName: String?): Boolean {
			val result = mth.root().typeUpdate.applyDebugInfo(mth, ssaVar, type)
			if (result == TypeUpdateResult.REJECT) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Reject debug info of type: {} and name: '{}' for {}, mth: {}", type, varName, ssaVar, mth)
				}
				return false
			}
			if (NameMapper.isValidAndPrintable(varName)) {
				ssaVar.setName(varName)
			}
			return true
		}

		/** 修正 splitter 产生的 `return` 指令的调试信息。 */
		private fun fixLinesForReturn(mth: MethodNode) {
			if (mth.isVoidReturn()) {
				return
			}
			var origReturn: InsnNode? = null
			val newReturns = ArrayList<InsnNode>(mth.preExitBlocks.size)
			for (exit in mth.preExitBlocks) {
				val ret = BlockUtils.getLastInsn(exit)
				if (ret != null) {
					if (ret.contains(AFlag.ORIG_RETURN)) {
						origReturn = ret
					} else {
						newReturns.add(ret)
					}
				}
			}
			val origRet = origReturn
			if (origRet != null) {
				for (ret in newReturns) {
					val oldArg = origRet.getArg(0)
					val newArg = ret.getArg(0)
					if (oldArg.isRegister && newArg.isRegister) {
						val oldArgReg = oldArg as RegisterArg
						val newArgReg = newArg as RegisterArg
						applyDebugInfo(mth, checkNotNull(newArgReg.sVar), oldArgReg.getType(), oldArgReg.name)
					}
					ret.setSourceLine(origRet.sourceLine)
				}
			}
		}

		private fun fixNamesForPhiInsns(mth: MethodNode) {
			for (ssaVar in mth.SVars) {
				for (phiInsn in ssaVar.usedInPhi) {
					val names = HashSet<String>(1 + phiInsn.argsCount)
					addArgName(phiInsn.result, names)
					for (arg in phiInsn.getArguments()) {
						addArgName(arg, names)
					}
					if (names.size == 1) {
						setNameForInsn(phiInsn, names.iterator().next())
					} else if (names.size > 1) {
						mth.addDebugComment("Different variable names in phi insn: $names, use first")
						setNameForInsn(phiInsn, names.iterator().next())
					}
				}
			}
		}

		private fun addArgName(arg: InsnArg?, names: MutableSet<String>) {
			if (arg is Named) {
				val name = arg.name
				if (name != null) {
					names.add(name)
				}
			}
		}

		private fun setNameForInsn(phiInsn: PhiInsn, name: String) {
			checkNotNull(phiInsn.result).name = name
			for (arg in phiInsn.getArguments()) {
				if (arg is Named) {
					arg.name = name
				}
			}
		}
	}
}
