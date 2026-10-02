package jadx.core.dex.visitors

import jadx.core.deobf.NameMapper
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.nodes.parser.SignatureParser
import jadx.core.dex.nodes.utils.TypeUtils
import jadx.core.dex.visitors.typeinference.TypeCompareEnum
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxException
import java.util.ArrayList
import java.util.HashSet
import java.util.Objects

/**
 * 解析并应用泛型签名（Signature 属性）。
 *
 * **做什么**：读取类/字段/方法上的泛型签名，展开类型变量、校验类型合法性，
 * 把解析出的泛型参数、父类、接口、方法参数/返回类型写回 AST 节点。
 * 若签名非法（混淆/损坏），给出警告并保留原始类型。
 *
 * **为什么重要**：DEX 字节码里泛型信息被擦除，只有签名属性保留了 `List<String>` 这类信息，
 * 反编译结果能否带上泛型完全依赖本 Pass。
 */
class SignatureProcessor : AbstractVisitor() {

	private lateinit var root: RootNode

	override fun init(root: RootNode) {
		this.root = root
	}

	@Throws(JadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		parseClassSignature(cls)
		for (field in cls.fields) {
			parseFieldSignature(field)
		}
		for (mth in cls.methods) {
			parseMethodSignature(mth)
		}
		return true
	}

	override fun getName(): String = "SignatureProcessor"

	private fun parseClassSignature(cls: ClassNode) {
		val sp = SignatureParser.fromNode(cls) ?: return
		try {
			val generics = sp.consumeGenericTypeParameters()
			val superClass = processSuperType(cls, sp.consumeType())
			val interfaces = processInterfaces(cls, sp.consumeTypeList())
			val resultGenerics = fixTypeParamDeclarations(cls, generics, superClass, interfaces)
			cls.updateGenericClsData(resultGenerics, superClass, interfaces)
		} catch (e: Exception) {
			cls.addWarnComment("Failed to parse class signature: " + sp.getSignature(), e)
		}
	}

	private fun processSuperType(cls: ClassNode, parsedType: ArgType?): ArgType {
		val superType = checkNotNull(cls.superClass)
		if (parsedType != null && Objects.equals(parsedType.getObject(), cls.classInfo.type.getObject())) {
			cls.addWarnComment("Incorrect class signature: super class is equals to this class")
			return superType
		}
		return bestClsType(cls, parsedType, superType)
	}

	/**
	 * 解析、校验并更新类的接口类型。
	 */
	private fun processInterfaces(cls: ClassNode, parsedTypes: List<ArgType>): List<ArgType> {
		val interfaces = cls.interfaces
		if (parsedTypes.isEmpty()) {
			return interfaces
		}
		val parsedCount = parsedTypes.size
		val interfacesCount = interfaces.size
		val result = ArrayList<ArgType>(interfacesCount)
		val count = minOf(interfacesCount, parsedCount)
		for (i in 0 until interfacesCount) {
			if (i < count) {
				result.add(bestClsType(cls, parsedTypes[i], interfaces[i]))
			} else {
				result.add(interfaces[i])
			}
		}
		if (interfacesCount < parsedCount) {
			cls.addWarnComment("Unexpected interfaces in signature: " + parsedTypes.subList(interfacesCount, parsedCount))
		}
		return result
	}

	private fun bestClsType(cls: ClassNode, candidateType: ArgType?, currentType: ArgType): ArgType {
		if (validateClsType(cls, candidateType)) {
			return checkNotNull(candidateType)
		}
		return currentType
	}

	private fun validateClsType(cls: ClassNode, candidateType: ArgType?): Boolean {
		if (candidateType == null) {
			return false
		}
		if (!candidateType.isObject()) {
			cls.addWarnComment("Incorrect class signature, class is not an object: $candidateType")
			return false
		}
		return true
	}

	companion object {
		/**
		 * 把父类/接口中出现、但未声明的类型参数补到泛型声明里，保证生成代码可编译。
		 */
		private fun fixTypeParamDeclarations(
			cls: ClassNode,
			generics: List<ArgType>,
			superClass: ArgType,
			interfaces: List<ArgType>,
		): List<ArgType> {
			if (interfaces.isEmpty() && superClass == ArgType.OBJECT) {
				return generics
			}
			val typeParams = HashSet<String>()
			superClass.visitTypes { t -> addGenericType(typeParams, t) }
			interfaces.forEach { i -> i.visitTypes { t -> addGenericType(typeParams, t) } }
			if (typeParams.isEmpty()) {
				return generics
			}
			val knownTypeParams: List<ArgType> = if (cls.isInner()) {
				val list = ArrayList(generics)
				cls.visitParentClasses { p -> list.addAll(p.getGenericTypeParameters()) }
				list
			} else {
				generics
			}
			for (declTypeParam in knownTypeParams) {
				typeParams.remove(declTypeParam.getObject())
			}
			if (typeParams.isEmpty()) {
				return generics
			}
			cls.addInfoComment("Add missing generic type declarations: $typeParams")
			val fixedGenerics = ArrayList<ArgType>(generics.size + typeParams.size)
			fixedGenerics.addAll(generics)
			val sortedParams = ArrayList(typeParams)
			sortedParams.sort()
			for (p in sortedParams) {
				fixedGenerics.add(ArgType.genericType(p))
			}
			return fixedGenerics
		}

		private fun addGenericType(usedTypeParameters: MutableSet<String>, t: ArgType): Any? {
			if (t.isGenericType()) {
				usedTypeParameters.add(t.getObject())
			}
			return null
		}
	}

	private fun parseFieldSignature(field: FieldNode) {
		val sp = SignatureParser.fromNode(field) ?: return
		val cls = field.parentClass
		try {
			val signatureType = sp.consumeType() ?: return
			if (!validateInnerType(signatureType)) {
				field.addWarnComment("Incorrect inner types in field signature: " + sp.getSignature())
				return
			}
			val type = root.getTypeUtils().expandTypeVariables(cls, signatureType)
			if (!validateParsedType(type, field.type)) {
				field.addInfoComment("Incorrect field signature: " + sp.getSignature())
				return
			}
			field.updateType(type)
		} catch (e: Exception) {
			cls.addWarnComment("Field signature parse error: " + field.getName(), e)
		}
	}

	private fun parseMethodSignature(mth: MethodNode) {
		val sp = SignatureParser.fromNode(mth) ?: return
		try {
			val typeParameters = sp.consumeGenericTypeParameters()
			val parsedArgTypes = sp.consumeMethodArgs(mth.getMethodInfo().argsCount)
			val parsedRetType = checkNotNull(sp.consumeType())

			if (!validateInnerType(parsedRetType) || !validateInnerType(parsedArgTypes)) {
				mth.addWarnComment("Incorrect inner types in method signature: " + sp.getSignature())
				return
			}

			mth.updateTypeParameters(typeParameters) // 在展开参数前应用
			val typeUtils = root.getTypeUtils()
			val retType = typeUtils.expandTypeVariables(mth, parsedRetType)
			val argTypes = Utils.collectionMap(parsedArgTypes) { t -> typeUtils.expandTypeVariables(mth, t) }

			if (!validateAndApplyTypes(mth, sp, retType, argTypes)) {
				// 类型不合法 -> 重置类型参数
				mth.updateTypeParameters(emptyList())
			}
		} catch (e: Exception) {
			mth.addWarnComment("Failed to parse method signature: " + sp.getSignature(), e)
		}
	}

	private fun validateAndApplyTypes(mth: MethodNode, sp: SignatureParser, retType: ArgType, argTypes: List<ArgType>): Boolean {
		try {
			if (!validateParsedType(retType, mth.getMethodInfo().returnType)) {
				mth.addWarnComment("Incorrect return type in method signature: " + sp.getSignature())
				return false
			}
			val checkedArgTypes = checkArgTypes(mth, sp, argTypes) ?: return false
			mth.updateTypes(java.util.Collections.unmodifiableList(checkedArgTypes), retType)
			return true
		} catch (e: Exception) {
			mth.addWarnComment("Type validation failed for signature: " + sp.getSignature(), e)
			return false
		}
	}

	private fun checkArgTypes(mth: MethodNode, sp: SignatureParser, parsedArgTypes: List<ArgType>): List<ArgType>? {
		val mthInfo: MethodInfo = mth.getMethodInfo()
		val mthArgTypes = mthInfo.argumentsTypes
		val len = parsedArgTypes.size
		if (len != mthArgTypes.size) {
			if (mth.parentClass.accessFlags.isEnum()) {
				// 枚举忽略
				return null
			}
			if (mthInfo.isConstructor() && mthArgTypes.isNotEmpty() && parsedArgTypes.isNotEmpty()) {
				// 为外部类补一个合成参数（见测试 TestGeneric8）
				val newArgTypes = ArrayList(parsedArgTypes)
				newArgTypes.add(0, mthArgTypes[0])
				if (newArgTypes.size == mthArgTypes.size) {
					return newArgTypes
				}
			}
			mth.addDebugComment("Incorrect args count in method signature: " + sp.getSignature())
			return null
		}
		for (i in 0 until len) {
			val parsedType = parsedArgTypes[i]
			val mthArgType = mthArgTypes[i]
			if (!validateParsedType(parsedType, mthArgType)) {
				mth.addWarnComment("Incorrect types in method signature: " + sp.getSignature())
				return null
			}
		}
		return parsedArgTypes
	}

	private fun validateParsedType(parsedType: ArgType, currentType: ArgType): Boolean {
		val result = root.getTypeCompare().compareTypes(parsedType, currentType)
		if (result == TypeCompareEnum.UNKNOWN &&
			parsedType.isObject() &&
			!validateFullClsName(parsedType.getObject())
		) {
			// 忽略外部非法类名：可能是保留字或垃圾
			return false
		}
		return result != TypeCompareEnum.CONFLICT
	}

	private fun validateFullClsName(fullClsName: String): Boolean {
		if (!NameMapper.isValidFullIdentifier(fullClsName)) {
			return false
		}
		if (fullClsName.indexOf('.') > 0) {
			for (namePart in fullClsName.split(Regex("\\."))) {
				if (!NameMapper.isValidIdentifier(namePart)) {
					return false
				}
			}
		}
		return true
	}

	private fun validateInnerType(types: List<ArgType>): Boolean {
		for (type in types) {
			if (!validateInnerType(type)) {
				return false
			}
		}
		return true
	}

	private fun validateInnerType(type: ArgType): Boolean {
		val innerType = type.getInnerType() ?: return true
		// 检查外部类型是否确实把 innerType 当作内部类
		val outerType = checkNotNull(type.getOuterType())
		val outerCls = root.resolveClass(outerType) ?: return true
		val innerObj: String
		if (innerType.getOuterType() != null) {
			innerObj = checkNotNull(innerType.getOuterType()).getObject()
			// "next" 内部类型会在方法末尾继续处理
		} else {
			innerObj = innerType.getObject()
		}
		if (!innerObj.contains(".")) {
			// 短引用
			for (innerClass in outerCls.innerClasses) {
				if (innerClass.shortName == innerObj) {
					return true
				}
			}
			return false
		}
		// 全名
		val innerCls = root.resolveClass(innerObj) ?: return false
		if (innerCls.parentClass != outerCls) {
			// 不是内部类 => 修正
			outerCls.addInnerClass(innerCls)
			innerCls.classInfo.convertToInner(outerCls)
		}
		return validateInnerType(innerType)
	}
}
