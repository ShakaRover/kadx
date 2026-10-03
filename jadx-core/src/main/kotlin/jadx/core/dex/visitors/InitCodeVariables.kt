package jadx.core.dex.visitors

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.ssa.SSATransform
import jadx.core.utils.exceptions.JadxException
import jadx.core.utils.exceptions.JadxRuntimeException
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
@JadxVisitor(
	name = "InitCodeVariables",
	desc = "Initialize code variables",
	runAfter = [SSATransform::class],
)
class InitCodeVariables : AbstractVisitor() {

	@Throws(JadxException::class)
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
					throw JadxRuntimeException("Several immutable types in one variable: $imTypes, vars: $vars")
				}
			}
		}

		private fun collectConnectedVars(phiInsnList: List<PhiInsn>, vars: MutableSet<SSAVar>) {
			if (phiInsnList.isEmpty()) {
				return
			}
			for (phiInsn in phiInsnList) {
				val resultVar = checkNotNull(phiInsn.getResult()).sVar
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
