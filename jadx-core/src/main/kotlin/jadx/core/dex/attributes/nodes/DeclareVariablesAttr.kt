package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.utils.Utils

/**
 * “待声明变量”属性：挂在区域（region）上，列出该区域开头需要声明的局部变量。
 *
 * **用途**：变量声明位置由区域分析决定，区域代码生成时读取本属性即可知道要先输出哪些
 * 变量声明（例如 `int a;`）。
 *
 * **Kotlin 转换说明**：原 Java 的 `getVars()` 返回 `Iterable<CodeVar>`（而非 List），
 * 为保持 JVM 签名完全一致，这里保留显式函数形式，底层用私有可变列表。
 */
class DeclareVariablesAttr : IJadxAttribute {

	private val vars: MutableList<CodeVar> = ArrayList()

	fun getVars(): Iterable<CodeVar> = vars

	fun addVar(arg: CodeVar) {
		vars.add(arg)
	}

	override fun getAttrType(): AType<DeclareVariablesAttr> = AType.DECLARE_VARIABLES

	override fun toString(): String = "DECL_VAR: " + Utils.listToString(vars)
}
