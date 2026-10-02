package jadx.core.codegen

import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.nodes.LoopLabelAttr
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.NamedArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import java.util.HashSet
import java.util.regex.Pattern

/**
 * 变量名生成器：为一个方法内的局部变量 / 参数分配不冲突的名字。
 *
 * **为什么需要它**：DEX 只有寄存器号，没有变量名。若调试信息缺失或名字非法，
 * 需要按 `r0`、`r1`、`v0`… 生成占位名；同时还要避免与字段名、内部类名、包名冲突。
 *
 * **Kotlin 转换说明**：`var` 是 Kotlin 关键字，故把原 Java 的 `CodeVar var` 参数改名为 `codeVar`（仅参数名，不影响 API）。
 */
class NameGen(mth: MethodNode, classGen: ClassGen) {
	private val mth: MethodNode = mth
	private val fallback: Boolean = classGen.isFallbackMode()
	private val varNames: MutableSet<String> = HashSet()

	init {
		val outerNameGen = classGen.getOuterNameGen()
		if (outerNameGen != null) {
			inheritUsedNames(outerNameGen)
		}
		addNamesUsedInClass()
	}

	/** 继承外部方法已用过的变量名（内部类 / lambda 方法内联时使用）。 */
	fun inheritUsedNames(otherNameGen: NameGen) {
		varNames.addAll(otherNameGen.varNames)
	}

	/** 把当前类中静态字段名、内部类短名与顶层包名都加入已用集合，避免冲突。 */
	private fun addNamesUsedInClass() {
		val parentClass = mth.parentClass
		for (field in parentClass.fields) {
			if (field.isStatic()) {
				varNames.add(field.getAlias())
			}
		}
		for (innerClass in parentClass.innerClasses) {
			varNames.add(innerClass.classInfo.aliasShortName)
		}
		// add all root package names to avoid collisions with full class names
		varNames.addAll(mth.root().getCacheStorage().rootPkgs)
	}

	/** 为方法参数 / 局部变量分配唯一名字并写回 CodeVar。 */
	fun assignArg(codeVar: CodeVar): String {
		if (fallback) {
			return getFallbackName(codeVar)
		}
		if (codeVar.isThis) {
			return RegisterArg.THIS_ARG_NAME
		}
		val name = getUniqueVarName(makeArgName(codeVar))
		codeVar.name = name
		return name
	}

	/** 为具名参数（如 catch 到的异常）分配唯一名字。 */
	fun assignNamedArg(arg: NamedArg): String {
		val name = arg.name
		if (fallback) {
			return checkNotNull(name)
		}
		val uniqName = getUniqueVarName(checkNotNull(name))
		arg.name = uniqName
		return uniqName
	}

	/** 读取寄存器参数已有名字；无名字或 fallback 模式时生成占位名。 */
	fun useArg(arg: RegisterArg): String {
		val name = arg.name
		if (name == null || fallback) {
			return getFallbackName(arg)
		}
		return name
	}

	// TODO: avoid name collision with variables names

	/** 循环标签名，如 `loop1`。 */
	fun getLoopLabel(attr: LoopLabelAttr): String {
		val name = "loop" + attr.loop.id
		varNames.add(name)
		return name
	}

	private fun getUniqueVarName(name: String): String {
		if (!varNames.contains(name)) {
			varNames.add(name)
			return name
		}
		// code duplication reuse same variable in different places
		// parse variable name and increment index
		val base: String
		var i: Int
		val matcher = ENDS_WITH_NUMBER.matcher(name)
		if (matcher.matches()) {
			base = name.substring(0, matcher.start(1))
			i = 1 + matcher.group(1).toInt()
		} else {
			base = name
			i = 2
		}
		while (true) {
			val newName = base + i++
			if (!varNames.contains(newName)) {
				varNames.add(newName)
				return newName
			}
		}
	}

	private fun makeArgName(codeVar: CodeVar): String {
		val name = codeVar.name
		if (NameMapper.isValidAndPrintable(name)) {
			return checkNotNull(name)
		}
		return getFallbackName(codeVar)
	}

	private fun getFallbackName(codeVar: CodeVar): String {
		val ssaVars = codeVar.ssaVars
		if (ssaVars.isEmpty()) {
			return "v"
		}
		return getFallbackName(ssaVars[0].assign)
	}

	private fun getFallbackName(arg: RegisterArg): String = "r" + arg.regNum

	companion object {
		private val ENDS_WITH_NUMBER: Pattern = Pattern.compile(".*(\\d+)$")
	}
}
