package kadx.core.dex.visitors

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.instructions.PhiInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.CodeVar
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.ssa.SSATransform
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.LinkedHashSet

/**
 * 为 SSA 变量初始化“代码变量”（[CodeVar]）。
 *
 * **做什么**：把 SSA 变量按 phi 关联合并成源代码层面的变量（[CodeVar]），
 * 识别 `this`、方法参数、声明变量，并从不可变类型推导变量类型。
 *
 * **为什么**：SSA 形式便于优化，但生成 Java 代码需要“变量”概念——一个 [CodeVar] 对应
 * 源码里一个变量，多个 SSA 版本共享同一个 [CodeVar]。
 */
@KadxVisitor(
	name = "InitCodeVariables",
	desc = "Initialize code variables",
	runAfter = [SSATransform::class],
)
class InitCodeVariables : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		initCodeVars(mth)
	}

	companion object {
		/** 重置后重新初始化（类型推导迭代时会调用）。 */
		fun rerun(mth: MethodNode) {
			for (sVar in mth.SVars) {
				sVar.resetTypeAndCodeVar()
			}
			initCodeVars(mth)
		}

		private fun initCodeVars(mth: MethodNode) {
			val thisArg = mth.getThisArg()
			if (thisArg != null) {
				initCodeVar(mth, thisArg)
			}
			for (mthArg in mth.argRegs) {
				initCodeVar(mth, mthArg)
			}
			for (ssaVar in mth.SVars) {
				initCodeVar(ssaVar)
			}
		}

		fun initCodeVar(mth: MethodNode, regArg: RegisterArg) {
			var ssaVar = regArg.sVar
			if (ssaVar == null) {
				ssaVar = mth.makeNewSVar(regArg)
			}
			initCodeVar(ssaVar)
		}

		fun initCodeVar(ssaVar: SSAVar) {
			if (ssaVar.isCodeVarSet()) {
				return
			}
			val codeVar = CodeVar()
			val assignArg = ssaVar.assign
			if (assignArg.contains(AFlag.THIS)) {
				codeVar.name = RegisterArg.THIS_ARG_NAME
				codeVar.isThis = true
			}
			if (assignArg.contains(AFlag.METHOD_ARGUMENT) || assignArg.contains(AFlag.CUSTOM_DECLARE)) {
				codeVar.isDeclared = true
			}

			setCodeVar(ssaVar, codeVar)
		}

		private fun setCodeVar(ssaVar: SSAVar, codeVar: CodeVar) {
			val phiList = ssaVar.phiList
			if (phiList.isNotEmpty()) {
				val vars = LinkedHashSet<SSAVar>()
				vars.add(ssaVar)
				collectConnectedVars(phiList, vars)
				setCodeVarType(codeVar, vars)
				for (v in vars) {
					if (v.isCodeVarSet()) {
						codeVar.mergeFlagsFrom(v.codeVar)
					}
					v.setCodeVar(codeVar)
				}
			} else {
				ssaVar.setCodeVar(codeVar)
			}
		}

		private fun setCodeVarType(codeVar: CodeVar, vars: Set<SSAVar>) {
			if (vars.size > 1) {
				// 收集所有“不可变且已知”的类型，去重（保持插入顺序，便于稳定报错信息）
				val imTypes = ArrayList<ArgType>()
				for (v in vars) {
					val imType = v.immutableType ?: continue
					if (!imType.isTypeKnown()) {
						continue
					}
					if (!imTypes.contains(imType)) {
						imTypes.add(imType)
					}
				}
				val imCount = imTypes.size
				if (imCount == 1) {
					codeVar.type = imTypes[0]
				} else if (imCount > 1) {
					throw KadxRuntimeException("Several immutable types in one variable: $imTypes, vars: $vars")
				}
			}
		}

		private fun collectConnectedVars(phiInsnList: List<PhiInsn>, vars: MutableSet<SSAVar>) {
			if (phiInsnList.isEmpty()) {
				return
			}
			for (phiInsn in phiInsnList) {
				// PHI 结果缺失（重处理周期中途夭折的边缘状态）时跳过该分支，
				// 其余参数仍正常收集——上游在此 NPE 导致整个方法失败
				val resultVar = phiInsn.result?.sVar
				if (resultVar != null && vars.add(resultVar)) {
					collectConnectedVars(resultVar.phiList, vars)
				}
				for (arg in phiInsn.getArguments()) {
					val sVar = (arg as RegisterArg).sVar
					if (sVar != null && vars.add(sVar)) {
						collectConnectedVars(sVar.phiList, vars)
					}
				}
			}
		}
	}
}
