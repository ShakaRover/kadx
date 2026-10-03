package jadx.core.dex.nodes.utils

import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.ClassTypeVarsAttr
import jadx.core.dex.attributes.nodes.MethodTypeVarsAttr
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils

/**
 * 泛型类型变量（type variable）处理工具。
 *
 * 负责：展开类型变量、收集方法/类作用域内的已知类型变量、在调用点做泛型替换，
 * 以及构建类的类型变量映射属性 [ClassTypeVarsAttr]。
 *
 * Kotlin 转换说明：本类只有普通业务方法（无 getter/静态方法），保持为函数即可；
 * 两个原 Java 私有静态辅助方法放入 companion。
 */
class TypeUtils(private val root: RootNode) {

	/** 获取类声明的泛型参数列表（应用内类或 classpath 类）。 */
	fun getClassGenerics(type: ArgType): List<ArgType> {
		val classNode = root.resolveClass(type)
		if (classNode != null) {
			return classNode.getGenericTypeParameters()
		}
		val clsDetails = checkNotNull(root.getClsp()).getClsDetails(type)
		if (clsDetails == null || clsDetails.typeParameters.isEmpty()) {
			return emptyList()
		}
		return clsDetails.typeParameters
	}

	/** 获取类的类型变量属性；不存在时构建并缓存。 */
	fun getClassTypeVars(type: ArgType): ClassTypeVarsAttr? {
		val classNode = root.resolveClass(type) ?: return null
		val typeVarsAttr = classNode.get(AType.CLASS_TYPE_VARS)
		if (typeVarsAttr != null) {
			return typeVarsAttr
		}
		return buildClassTypeVarsAttr(classNode)
	}

	/** 在类作用域内展开 [type] 中的类型变量。 */
	fun expandTypeVariables(cls: ClassNode, type: ArgType): ArgType {
		if (type.containsTypeVariable()) {
			expandTypeVar(cls, type, getKnownTypeVarsAtClass(cls))
		}
		return type
	}

	/** 在方法作用域内展开 [type] 中的类型变量。 */
	fun expandTypeVariables(mth: MethodNode, type: ArgType): ArgType {
		if (type.containsTypeVariable()) {
			expandTypeVar(mth, type, getKnownTypeVarsAtMethod(mth))
		}
		return type
	}

	/** 用已知类型变量的 extends 约束填充 [type] 里的类型变量。 */
	private fun expandTypeVar(node: NotificationAttrNode, type: ArgType, typeVars: Collection<ArgType>) {
		if (typeVars.isEmpty()) {
			return
		}
		var allExtendsEmpty = true
		for (argType in typeVars) {
			if (Utils.notEmpty(argType.getExtendTypes())) {
				allExtendsEmpty = false
				break
			}
		}
		if (allExtendsEmpty) {
			return
		}
		type.visitTypes { t ->
			if (t.isGenericType()) {
				val typeVarName = t.getObject()
				for (typeVar in typeVars) {
					if (typeVar.getObject() == typeVarName) {
						t.setExtendTypes(typeVar.getExtendTypes())
						return@visitTypes null
					}
				}
				node.addWarnComment("Unknown type variable: $typeVarName in type: $type")
			}
			null
		}
	}

	/** 获取方法作用域内已知的类型变量集合（带缓存属性）。 */
	fun getKnownTypeVarsAtMethod(mth: MethodNode): Set<ArgType> {
		val typeVarsAttr = mth.get(AType.METHOD_TYPE_VARS)
		if (typeVarsAttr != null) {
			return typeVarsAttr.getTypeVars()
		}
		val typeVars = collectKnownTypeVarsAtMethod(mth)
		val varsAttr = MethodTypeVarsAttr.build(typeVars)
		mth.addAttr(varsAttr)
		return varsAttr.getTypeVars()
	}

	/**
	 * 查找 [checkType] 中第一个未知的类型变量。
	 *
	 * @return 未知类型变量；全部已知时返回 null
	 */
	fun checkForUnknownTypeVars(mth: MethodNode, checkType: ArgType): ArgType? {
		val knownTypeVars = getKnownTypeVarsAtMethod(mth)
		return checkType.visitTypes { type ->
			if (type.isGenericType() && !knownTypeVars.contains(type)) {
				type
			} else {
				null
			}
		}
	}

	fun containsUnknownTypeVar(mth: MethodNode, type: ArgType): Boolean = checkForUnknownTypeVars(mth, type) != null

	companion object {
		/** 收集类作用域内的已知类型变量（内部类会合并外部类的类型变量）。 */
		private fun getKnownTypeVarsAtClass(cls: ClassNode): Collection<ArgType> {
			if (cls.isInner()) {
				val typeVars: MutableSet<ArgType> = HashSet(cls.getGenericTypeParameters())
				cls.visitParentClasses { parent -> typeVars.addAll(parent.getGenericTypeParameters()) }
				return typeVars
			}
			return cls.getGenericTypeParameters()
		}

		/** 收集方法作用域内的已知类型变量（类类型变量 + 方法类型变量）。 */
		private fun collectKnownTypeVarsAtMethod(mth: MethodNode): Set<ArgType> {
			val typeVars: MutableSet<ArgType> = HashSet()
			typeVars.addAll(getKnownTypeVarsAtClass(mth.parentClass))
			typeVars.addAll(mth.getTypeParameters())
			return if (typeVars.isEmpty()) emptySet() else typeVars
		}
	}

	/**
	 * 用实例类型替换 [typeWithGeneric] 中的类泛型变量。
	 *
	 * 示例：`instanceType: Set<String>`、`typeWithGeneric: Iterator<E>` → `Iterator<String>`。
	 */
	fun replaceClassGenerics(instanceType: ArgType, typeWithGeneric: ArgType): ArgType? = replaceClassGenerics(instanceType, instanceType, typeWithGeneric)

	/** 带“泛型来源类型”的替换版本（用于父类/接口的泛型推导）。 */
	fun replaceClassGenerics(instanceType: ArgType, genericSourceType: ArgType, typeWithGeneric: ArgType): ArgType? {
		var typeVarsMap: Map<ArgType, ArgType> = emptyMap()
		val typeVars = getClassTypeVars(instanceType)
		if (typeVars != null) {
			typeVarsMap = mergeTypeMaps(typeVarsMap, typeVars.getTypeVarsMapFor(genericSourceType))
		}
		typeVarsMap = mergeTypeMaps(typeVarsMap, getTypeVariablesMapping(instanceType))
		var outerType = instanceType.getOuterType()
		while (outerType != null) {
			typeVarsMap = mergeTypeMaps(typeVarsMap, getTypeVariablesMapping(outerType))
			outerType = outerType.getOuterType()
		}
		return replaceTypeVariablesUsingMap(typeWithGeneric, typeVarsMap)
	}

	/**
	 * 合并两张类型变量映射。
	 *
	 * 语义（与原 Java 一致）：若 base 中某个 key 的 value 出现在 addition 中，
	 * 则用 addition 里对应的类型覆盖；最后把 addition 剩余项全部并入。
	 * 原 Java 会就地修改 addition，这里改用副本，避免污染调用方传入的缓存映射。
	 */
	private fun mergeTypeMaps(base: Map<ArgType, ArgType>, addition: Map<ArgType, ArgType>): Map<ArgType, ArgType> {
		if (base.isEmpty()) {
			return addition
		}
		if (addition.isEmpty()) {
			return base
		}
		val map = HashMap<ArgType, ArgType>(base.size + addition.size)
		val additionCopy = HashMap(addition)
		for ((key, value) in base) {
			val type = additionCopy.remove(value)
			if (type != null) {
				map[key] = type
			} else {
				map[key] = value
			}
		}
		map.putAll(additionCopy)
		return map
	}

	/** 建立“类泛型参数 → 实际类型”的映射（如 `List<T>` → `T -> String`）。 */
	fun getTypeVariablesMapping(clsType: ArgType): Map<ArgType, ArgType> {
		if (!clsType.isGeneric()) {
			return emptyMap()
		}
		val typeParameters = root.getTypeUtils().getClassGenerics(clsType)
		if (typeParameters.isEmpty()) {
			return emptyMap()
		}
		val actualTypes = clsType.getGenericTypes()
		if (actualTypes == null || actualTypes.isEmpty()) {
			return emptyMap()
		}
		val genericParamsCount = actualTypes.size
		if (genericParamsCount != typeParameters.size) {
			return emptyMap()
		}
		val replaceMap = HashMap<ArgType, ArgType>(genericParamsCount)
		for (i in 0 until genericParamsCount) {
			val actualType = actualTypes[i]
			var typeVar = typeParameters[i]
			// 强制短形式（只保留类型变量名；getExtendTypes() 永不为 null）
			typeVar = ArgType.genericType(typeVar.getObject())
			replaceMap[typeVar] = actualType
		}
		return replaceMap
	}

	/** 根据调用指令的实际参数/返回值，建立方法类型变量的映射。 */
	fun getTypeVarMappingForInvoke(invokeInsn: BaseInvokeNode): Map<ArgType, ArgType> {
		val mthDetails = root.getMethodUtils().getMethodDetails(invokeInsn) ?: return emptyMap()
		val map = HashMap<ArgType, ArgType>(1 + invokeInsn.getArgsCount())
		addTypeVarMapping(map, mthDetails.getReturnType(), invokeInsn.getResult())
		val argCount = Math.min(mthDetails.getArgTypes().size, invokeInsn.getArgsCount() - invokeInsn.getFirstArgOffset())
		for (i in 0 until argCount) {
			addTypeVarMapping(map, mthDetails.getArgTypes()[i], invokeInsn.getArg(i + invokeInsn.getFirstArgOffset()))
		}
		return map
	}

	/** 把单个类型变量与其实际类型写入映射。 */
	private fun addTypeVarMapping(map: MutableMap<ArgType, ArgType>, typeVar: ArgType?, arg: InsnArg?) {
		if (arg == null || typeVar == null || !typeVar.isTypeKnown()) {
			return
		}
		if (typeVar.isGenericType()) {
			map[typeVar] = arg.getType()
		}
		// TODO: 解析嵌套类型变量：'List<T> -> List<String>' → 'T -> String'
	}

	/** 用调用指令的实参类型替换方法泛型变量（当前仅处理直接匹配的参数）。 */
	fun replaceMethodGenerics(invokeInsn: BaseInvokeNode, details: IMethodDetails, typeWithGeneric: ArgType): ArgType? {
		val methodArgTypes = details.getArgTypes()
		if (methodArgTypes.isEmpty()) {
			return null
		}
		val firstArgOffset = invokeInsn.getFirstArgOffset()
		val argsCount = methodArgTypes.size
		for (i in 0 until argsCount) {
			val methodArgType = methodArgTypes[i]
			val insnArg = invokeInsn.getArg(i + firstArgOffset)
			val insnType = insnArg.getType()
			if (methodArgType == typeWithGeneric) {
				return insnType
			}
		}
		// TODO: 构建完整的方法类型变量映射
		return null
	}

	/**
	 * 根据映射表替换 [replaceType] 中的类型变量。
	 *
	 * 递归处理：类型变量、数组、通配符、带泛型的外部类/内部类。
	 */
	fun replaceTypeVariablesUsingMap(replaceType: ArgType, replaceMap: Map<ArgType, ArgType>): ArgType? {
		if (replaceMap.isEmpty()) {
			return null
		}
		if (replaceType.isGenericType()) {
			return replaceMap[replaceType]
		}
		if (replaceType.isArray()) {
			val element = replaceType.getArrayElement() ?: return null
			val replaced = replaceTypeVariablesUsingMap(element, replaceMap) ?: return null
			return ArgType.array(replaced)
		}

		val wildcardType = replaceType.getWildcardType()
		if (wildcardType != null && wildcardType.containsTypeVariable()) {
			val newWildcardType = replaceTypeVariablesUsingMap(wildcardType, replaceMap) ?: return null
			return ArgType.wildcard(newWildcardType, checkNotNull(replaceType.getWildcardBound()))
		}

		if (replaceType.isGeneric()) {
			val outerType = replaceType.getOuterType()
			if (outerType != null) {
				val replacedOuter = replaceTypeVariablesUsingMap(outerType, replaceMap) ?: return null
				val innerType = replaceType.getInnerType()
				val replacedInner = replaceTypeVariablesUsingMap(checkNotNull(innerType), replaceMap)
				return ArgType.outerGeneric(replacedOuter, replacedInner ?: checkNotNull(innerType))
			}
			val genericTypes = replaceType.getGenericTypes()
			if (Utils.notEmpty(genericTypes)) {
				val newTypes = Utils.collectionMap(genericTypes) { t ->
					replaceTypeVariablesUsingMap(t, replaceMap) ?: t
				}
				return ArgType.generic(replaceType, newTypes)
			}
		}
		return null
	}

	/** 构建并缓存类的类型变量属性（含父类/接口的类型变量映射）。 */
	private fun buildClassTypeVarsAttr(cls: ClassNode): ClassTypeVarsAttr {
		val map = HashMap<String, Map<ArgType, ArgType>>()
		val currentClsType = cls.classInfo.type
		map[currentClsType.getObject()] = getTypeVariablesMapping(currentClsType)

		cls.visitSuperTypes { parent, type ->
			val currentVars = type.getGenericTypes()
			if (currentVars == null || currentVars.isEmpty()) {
				return@visitSuperTypes
			}
			val varsCount = currentVars.size
			val sourceTypeVars = getClassGenerics(type)
			if (varsCount == sourceTypeVars.size) {
				val parentTypeMap = map[parent.getObject()]
				val varsMap = HashMap<ArgType, ArgType>(varsCount)
				for (i in 0 until varsCount) {
					val currentTypeVar = currentVars[i]
					val resultType = parentTypeMap?.get(currentTypeVar)
					varsMap[sourceTypeVars[i]] = resultType ?: currentTypeVar
				}
				map[type.getObject()] = varsMap
			}
		}
		val currentTypeVars = cls.getGenericTypeParameters()
		val typeVarsAttr = ClassTypeVarsAttr(currentTypeVars, map)
		cls.addAttr(typeVarsAttr)
		return typeVarsAttr
	}

	/** 递归访问某个类型的所有父类型（应用内类走 [ClassNode]，否则走 classpath）。 */
	fun visitSuperTypes(type: ArgType, consumer: (ArgType, ArgType) -> Unit) {
		val cls = root.resolveClass(type)
		if (cls != null) {
			cls.visitSuperTypes(consumer)
		} else {
			val clspClass = checkNotNull(root.getClsp()).getClsDetails(type)
			if (clspClass != null) {
				for (parent in clspClass.parents.orEmpty()) {
					val superType = checkNotNull(parent)
					if (superType !== ArgType.OBJECT) {
						consumer(type, superType)
						visitSuperTypes(superType, consumer)
					}
				}
			}
		}
	}
}
