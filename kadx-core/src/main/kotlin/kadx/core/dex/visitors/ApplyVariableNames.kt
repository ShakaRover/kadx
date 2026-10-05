package kadx.core.dex.visitors

import kadx.core.Consts
import kadx.core.deobf.NameMapper
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.CodeVar
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.regions.variables.ProcessVariables
import kadx.core.utils.StringUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxException

/**
 * 变量名推断访问者。
 *
 * **做什么**：当局部变量还没有合法名字时，根据它的类型、赋值指令（调用/构造器/数组长度等）
 * 猜测一个可读名字，例如 `String str`、`List list`、`int num`。
 *
 * **为什么**：DEX 没有局部变量名（除非有调试信息），推断出的名字能显著提升反编译可读性。
 */
@KadxVisitor(
	name = "ApplyVariableNames",
	desc = "Try to guess variable name from usage",
	runAfter = [ProcessVariables::class],
)
class ApplyVariableNames : AbstractVisitor() {

	private lateinit var root: RootNode

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		this.root = root
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		for (ssaVar in mth.SVars) {
			val codeVar = ssaVar.codeVar
			val newName = guessName(codeVar)
			if (newName != null) {
				codeVar.name = newName
			}
		}
	}

	private fun guessName(variable: CodeVar): String? {
		if (variable.isThis) {
			return RegisterArg.THIS_ARG_NAME
		}
		if (!variable.isDeclared) {
			// 名字不会在代码中出现
			return null
		}
		if (NameMapper.isValidAndPrintable(variable.name)) {
			// 当前名字已合法，保留
			return null
		}
		val ssaVars = variable.ssaVars
		if (Utils.notEmpty(ssaVars)) {
			var mthArg = false
			for (ssaVar in ssaVars) {
				if (ssaVar.assign.contains(AFlag.METHOD_ARGUMENT)) {
					mthArg = true
					break
				}
			}
			if (mthArg) {
				// 方法参数使用声明类型，忽略使用点
				return makeNameForType(checkNotNull(variable.type))
			}
			for (ssaVar in ssaVars) {
				val name = makeNameForSSAVar(ssaVar)
				if (name != null) {
					return name
				}
			}
		}
		return makeNameForType(checkNotNull(variable.type))
	}

	private fun makeNameForSSAVar(ssaVar: SSAVar): String? {
		val ssaVarName = ssaVar.getName()
		if (ssaVarName != null) {
			return ssaVarName
		}
		val assignInsn = ssaVar.assignInsn
		if (assignInsn != null) {
			val name = makeNameFromInsn(ssaVar, assignInsn)
			if (NameMapper.isValidAndPrintable(name)) {
				return name
			}
		}
		return null
	}

	private fun makeNameFromInsn(ssaVar: SSAVar, insn: InsnNode): String? {
		when (insn.type) {
			InsnType.INVOKE -> return makeNameFromInvoke(ssaVar, insn as InvokeNode)

			InsnType.CONSTRUCTOR -> {
				val co = insn as ConstructorInsn
				val callMth = root.getMethodUtils().resolveMethod(co)
				if (callMth != null && callMth.contains(AFlag.ANONYMOUS_CONSTRUCTOR)) {
					// 不要使用匿名类的名字
					return null
				}
				return makeNameForClass(co.classType)
			}

			InsnType.ARRAY_LENGTH -> return "length"

			InsnType.ARITH,
			InsnType.TERNARY,
			InsnType.CAST,
			-> {
				for (arg in insn.getArguments()) {
					if (arg.isInsnWrap) {
						val wrapInsn = (arg as InsnWrapArg).wrapInsn
						val wName = makeNameFromInsn(ssaVar, wrapInsn)
						if (wName != null) {
							return wName
						}
					}
				}
			}

			else -> {}
		}
		return null
	}

	private fun makeNameForType(type: ArgType): String {
		if (type.isPrimitive()) {
			return checkNotNull(type.getPrimitiveType()).shortName.lowercase()
		}
		if (type.isArray()) {
			return makeNameForType(type.getArrayRootElement()) + "Arr"
		}
		return makeNameForObject(type)
	}

	private fun makeNameForObject(type: ArgType): String {
		if (type.isGenericType()) {
			return StringUtils.escape(type.getObject().lowercase())
		}
		if (type.isObject()) {
			val alias = getAliasForObject(type.getObject())
			if (alias != null) {
				return alias
			}
			return makeNameForCheckedClass(ClassInfo.fromType(root, type))
		}
		return StringUtils.escape(type.toString())
	}

	private fun makeNameForCheckedClass(classInfo: ClassInfo): String {
		val shortName = classInfo.aliasShortName
		val vName = fromName(shortName)
		if (vName != null) {
			return vName
		}
		val lower = StringUtils.escape(shortName.lowercase())
		if (shortName == lower) {
			return lower + "Var"
		}
		return lower
	}

	private fun makeNameForClass(classInfo: ClassInfo): String {
		val alias = getAliasForObject(classInfo.fullName)
		if (alias != null) {
			return alias
		}
		return makeNameForCheckedClass(classInfo)
	}

	private fun makeNameFromInvoke(ssaVar: SSAVar, inv: InvokeNode): String? {
		val callMth: MethodInfo = inv.callMth
		val name = callMth.alias
		val declClass = callMth.declClass
		if ("getInstance" == name) {
			// 例如 Cipher.getInstance
			return makeNameForClass(declClass)
		}
		val shortName = cutPrefix(name)
		if (shortName != null) {
			return fromName(shortName)
		}
		if ("iterator" == name) {
			return "it"
		}
		if ("toString" == name) {
			return makeNameForClass(declClass)
		}
		if ("forName" == name && declClass.type == ArgType.CLASS) {
			return OBJ_ALIAS[Consts.CLASS_CLASS]
		}
		// 大多数情况下直接用方法名做变量名并不好
		if (!GOOD_VAR_NAMES.contains(name)) {
			val typeName = makeNameForType(checkNotNull(ssaVar.codeVar.type))
			if (!typeName.equals(name, ignoreCase = true)) {
				return typeName + StringUtils.capitalizeFirstChar(name)
			}
		}
		return name
	}

	private fun cutPrefix(name: String): String? {
		for (prefix in INVOKE_PREFIXES) {
			if (name.startsWith(prefix)) {
				return name.substring(prefix.length)
			}
		}
		return null
	}

	override fun getName(): String = "ApplyVariableNames"

	companion object {
		private val OBJ_ALIAS: Map<String, String> = Utils.newConstStringMap(
			Consts.CLASS_STRING, "str",
			Consts.CLASS_CLASS, "cls",
			Consts.CLASS_THROWABLE, "th",
			Consts.CLASS_OBJECT, "obj",
			"java.util.Iterator", "it",
			"java.util.HashMap", "map",
			"java.lang.Boolean", "bool",
			"java.lang.Short", "sh",
			"java.lang.Integer", "num",
			"java.lang.Character", "ch",
			"java.lang.Byte", "b",
			"java.lang.Float", "f",
			"java.lang.Long", "l",
			"java.lang.Double", "d",
			"java.lang.StringBuilder", "sb",
			"java.lang.Exception", "exc",
		)

		private val GOOD_VAR_NAMES: Set<String> = setOf("size", "length", "list", "map", "next")
		private val INVOKE_PREFIXES: List<String> = listOf("get", "set", "to", "parse", "read", "format")

		private fun getAliasForObject(name: String): String? = OBJ_ALIAS[name]

		private fun fromName(name: String?): String? {
			if (name == null || name.isEmpty()) {
				return null
			}
			if (name.uppercase() == name) {
				// 全为大写字符
				return name.lowercase()
			}
			val v1 = Character.toLowerCase(name[0]) + name.substring(1)
			if (v1 != name) {
				return v1
			}
			if (name.length < 3) {
				return name + "Var"
			}
			return null
		}
	}
}
