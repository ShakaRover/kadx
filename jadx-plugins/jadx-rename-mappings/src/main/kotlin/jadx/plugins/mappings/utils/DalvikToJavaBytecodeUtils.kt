package jadx.plugins.mappings.utils

import jadx.api.metadata.annotations.VarNode
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.MethodNode

/**
 * Dalvik 寄存器索引 ↔ Java 字节码局部变量表索引的换算工具。
 *
 * **背景**：映射文件里记录的是 Java 字节码视角的 lvIndex/lvtIndex，而 jadx 内部用
 * SSAVar/寄存器号；这里提供两套换算（lv = 寄存器差值，lvt = 参数+变量的顺序位置）。
 */
public object DalvikToJavaBytecodeUtils {

	// ****************************
	// Local variable index
	// ****************************

	// Method args

	public fun getMethodArgLvIndex(methodArg: VarNode): Int? {
		val mth = methodArg.getMth()
		val lvIndex = getMethodArgLvIndexViaSsaVars(methodArg.getReg(), mth)
		if (lvIndex != null) {
			return lvIndex
		}
		var result: Int? = null
		val args = mth.collectArgNodes()
		for (arg in args) {
			result = arg.getReg() - args[0].getReg() + if (mth.accessFlags.isStatic()) 0 else 1
			if (arg == methodArg) {
				break
			}
		}
		return result
	}

	public fun getMethodArgLvIndex(methodArgSsaVar: SSAVar, mth: MethodNode): Int? = getMethodArgLvIndexViaSsaVars(methodArgSsaVar.regNum, mth)

	private fun getMethodArgLvIndexViaSsaVars(regNum: Int, mth: MethodNode): Int? {
		val ssaVars = mth.SVars
		if (ssaVars.isNotEmpty()) {
			return regNum - ssaVars[0].regNum
		}
		return null
	}

	// Method vars

	public fun getMethodVarLvIndex(methodVar: VarNode): Int {
		val mth = methodVar.getMth()
		val lvIndex = getMethodVarLvIndexViaSsaVars(methodVar.getReg(), mth)
		if (lvIndex != null) {
			return lvIndex
		}
		var lastArgLvIndex = if (mth.accessFlags.isStatic()) -1 else 0
		val args = mth.collectArgNodes()
		if (args.isNotEmpty()) {
			lastArgLvIndex = checkNotNull(getMethodArgLvIndex(args[args.size - 1]))
		}
		return lastArgLvIndex + methodVar.getReg() + if (mth.accessFlags.isStatic()) 0 else 1
	}

	public fun getMethodVarLvIndex(methodVarSsaVar: SSAVar, mth: MethodNode): Int? = getMethodVarLvIndexViaSsaVars(methodVarSsaVar.regNum, mth)

	private fun getMethodVarLvIndexViaSsaVars(regNum: Int, mth: MethodNode): Int? {
		val ssaVars = mth.SVars
		if (ssaVars.isEmpty()) {
			return null
		}
		var lastArgLvIndex = if (mth.accessFlags.isStatic()) -1 else 0
		val args = mth.argRegs
		if (args.isNotEmpty()) {
			val lastArgSv = checkNotNull(args[args.size - 1].sVar) { "SSA var not set for method arg" }
			lastArgLvIndex = checkNotNull(getMethodArgLvIndexViaSsaVars(lastArgSv.regNum, mth))
		}
		return lastArgLvIndex + regNum + if (mth.accessFlags.isStatic()) 0 else 1
	}

	// ****************************
	// Local variable table index
	// ****************************

	// Method args

	public fun getMethodArgLvtIndex(methodArg: VarNode): Int? {
		val mth = methodArg.getMth()
		var lvtIndex = if (mth.accessFlags.isStatic()) 0 else 1
		for (arg in mth.collectArgNodes()) {
			if (arg == methodArg) {
				return lvtIndex
			}
			lvtIndex++
		}
		return null
	}

	public fun getMethodArgLvtIndex(methodArgSsaVar: SSAVar, mth: MethodNode): Int? {
		val ssaVars = mth.SVars
		if (ssaVars.isEmpty()) {
			return null
		}
		var lvtIndex = if (mth.accessFlags.isStatic()) 0 else 1
		for (arg in mth.argRegs) {
			if (arg.sVar == methodArgSsaVar) {
				return lvtIndex
			}
			lvtIndex++
		}
		return null
	}

	// Method vars

	// TODO: public static Integer getMethodVarLvtIndex(VarNode methodVar) {}

	public fun getMethodVarLvtIndex(methodVarSsaVar: SSAVar, mth: MethodNode): Int? {
		val ssaVars = ArrayList(mth.SVars)
		if (ssaVars.isEmpty()) {
			return null
		}
		var lvtIndex = getMethodArgLvtIndex(methodVarSsaVar, mth)
		if (lvtIndex != null) {
			return lvtIndex
		}

		lvtIndex = if (mth.accessFlags.isStatic()) 0 else 1
		lvtIndex += mth.argTypes.size

		// 与原 Java 一致：此处再次调用（结果必为 null），解包时抛 NPE——保留原行为
		lvtIndex = (getMethodArgLvtIndex(methodVarSsaVar, mth) ?: throw NullPointerException("method arg lvt index is null")) + 1
		ssaVars.subList(0, ssaVars.indexOf(methodVarSsaVar) + 1).clear()

		var lastRegNum = -1
		for (ssaVar in ssaVars) {
			if (ssaVar.regNum == lastRegNum) {
				// Not present in bytecode
				// System.out.println("Duplicate RegNum: " + ssaVar.getRegNum());
				continue
			}
			lvtIndex++
			if (ssaVar == methodVarSsaVar) {
				return lvtIndex
			}
			lastRegNum = ssaVar.regNum
		}
		return null
	}
}
