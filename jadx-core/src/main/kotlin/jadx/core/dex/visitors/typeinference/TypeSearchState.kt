package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.LinkedHashMap

/**
 * 多变量类型搜索的全局状态：SSAVar → [TypeSearchVarInfo]。
 *
 * **算法意图**：为方法内每个 SSA 变量建立搜索状态，并提供按变量查询、
 * 以及按“已解析/未解析”筛选变量的能力。
 *
 * **Kotlin 转换说明**：原方法参数名为 `var`（Kotlin 关键字），重命名为 `ssaVar`；
 * 流式筛选改写为普通 `for` 循环（热点路径）。
 */
class TypeSearchState(mth: MethodNode) {

	private val varInfoMap: MutableMap<SSAVar, TypeSearchVarInfo> = LinkedHashMap(mth.getSVars().size)

	init {
		for (ssaVar in mth.getSVars()) {
			varInfoMap[ssaVar] = TypeSearchVarInfo(ssaVar)
		}
	}

	fun getVarInfo(ssaVar: SSAVar): TypeSearchVarInfo {
		val varInfo = varInfoMap[ssaVar]
		if (varInfo == null) {
			throw JadxRuntimeException("TypeSearchVarInfo not found in map for var: $ssaVar")
		}
		return varInfo
	}

	/** 取参数当前类型：寄存器参数取所在 SSA 变量的候选类型，其它参数取自身类型。 */
	fun getArgType(arg: InsnArg): ArgType {
		if (arg.isRegister) {
			val reg = arg as RegisterArg
			return getVarInfo(checkNotNull(reg.sVar)).getCurrentType()
		}
		return arg.getType()
	}

	fun getAllVars(): List<TypeSearchVarInfo> = ArrayList(varInfoMap.values)

	fun getUnresolvedVars(): List<TypeSearchVarInfo> {
		val list = ArrayList<TypeSearchVarInfo>()
		for (varInfo in varInfoMap.values) {
			if (!varInfo.isTypeResolved()) {
				list.add(varInfo)
			}
		}
		return list
	}

	fun getResolvedVars(): List<TypeSearchVarInfo> {
		val list = ArrayList<TypeSearchVarInfo>()
		for (varInfo in varInfoMap.values) {
			if (varInfo.isTypeResolved()) {
				list.add(varInfo)
			}
		}
		return list
	}
}
