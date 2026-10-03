package jadx.core.codegen

import jadx.api.CommentsLevel
import jadx.api.ICodeInfo
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.args.IntegerFormat
import jadx.api.metadata.annotations.NodeEnd
import jadx.api.plugins.input.data.AccessFlags
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.Consts
import jadx.core.codegen.utils.CodeGenUtils
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.FieldInitInsnAttr
import jadx.core.dex.attributes.nodes.EnumClassAttr
import jadx.core.dex.attributes.nodes.EnumClassAttr.EnumField
import jadx.core.dex.attributes.nodes.MethodInlineAttr
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.EncodedValueUtils
import jadx.core.utils.Utils
import jadx.core.utils.android.AndroidResourcesUtils
import jadx.core.utils.exceptions.CodegenException
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.HashSet
import java.util.Objects

/**
 * 类代码生成器：把 [ClassNode] 渲染成完整 Java 源码（包声明、import、类声明、字段、方法、内部类）。
 *
 * **导入管理**：通过 [imports] 收集使用到的类，尽量用短名；遇到同名冲突时退回全限定名。
 *
 * **Kotlin 转换说明**：三个构造器对应主构造器与两个次构造器，JVM 签名与原 Java 一致；
 * 静态方法（`addMthUsageInfo`）放入 companion 并用 `internal` 供同模块调用。
 */
class ClassGen(
	private val cls: ClassNode,
	private val parentGenRef: ClassGen?,
	private val useImports: Boolean,
	private val fallback: Boolean,
	private val showInconsistentCode: Boolean,
	private val integerFormat: IntegerFormat,
) {
	constructor(cls: ClassNode, jadxArgs: JadxArgs) : this(
		cls,
		null,
		jadxArgs.isUseImports,
		jadxArgs.isFallbackMode,
		jadxArgs.isShowInconsistentCode,
		jadxArgs.integerFormat,
	)

	constructor(cls: ClassNode, parentClsGen: ClassGen) : this(
		cls,
		parentClsGen,
		parentClsGen.useImports,
		parentClsGen.fallback,
		parentClsGen.showInconsistentCode,
		parentClsGen.integerFormat,
	)

	val annotationGen: AnnotationGen = AnnotationGen(cls, this)
	private val importsSet: MutableSet<ClassInfo> = HashSet()
	private var clsDeclOffset: Int = 0

	var isBodyGenStarted: Boolean = false

	var outerNameGen: NameGen? = null

	val classNode: ClassNode get() = cls

	@Throws(CodegenException::class)
	fun makeClass(): ICodeInfo {
		if (cls.contains(AFlag.PACKAGE_INFO)) {
			return makePackageInfo()
		}
		val clsBody = cls.root().makeCodeWriter()
		addClassCode(clsBody)

		val clsCode = cls.root().makeCodeWriter()
		addPackage(clsCode)
		clsCode.newLine()
		addImports(clsCode)
		clsCode.add(clsBody)
		return clsCode.finish()
	}

	private fun addPackage(clsCode: ICodeWriter) {
		if (cls.`package`.isEmpty()) {
			clsCode.add("// default package")
		} else {
			clsCode.add("package ").add(cls.`package`).add(';')
		}
	}

	private fun addImports(clsCode: ICodeWriter) {
		val importsCount = importsSet.size
		if (importsCount != 0) {
			val sortedImports = importsSet.sortedBy { it.aliasFullName }
			for (classInfo in sortedImports) {
				clsCode.startLine("import ")
				val classNode = cls.root().resolveClass(classInfo)
				if (classNode != null) {
					clsCode.attachAnnotation(classNode)
				}
				clsCode.add(classInfo.aliasFullName)
				clsCode.add(';')
			}
			clsCode.newLine()
			importsSet.clear()
		}
	}

	private fun makePackageInfo(): ICodeInfo {
		val code = cls.root().makeCodeWriter()
		annotationGen.addForClass(code)
		code.newLine()
		code.attachDefinition(cls)
		addPackage(code)
		code.newLine()
		addImports(code)
		return code.finish()
	}

	@Throws(CodegenException::class)
	fun addClassCode(code: ICodeWriter) {
		if (cls.contains(AFlag.DONT_GENERATE)) {
			return
		}
		if (Consts.DEBUG_USAGE) {
			addClassUsageInfo(code, cls)
		}
		addClassDeclaration(code)
		addClassBody(code)
	}

	fun addClassDeclaration(clsCode: ICodeWriter) {
		var af: AccessInfo = cls.accessFlags
		if (af.isInterface()) {
			af = af.remove(AccessFlags.ABSTRACT)
				.remove(AccessFlags.STATIC)
		}
		// 'static' and 'private' modifier not allowed for top classes (not inner)
		if (!cls.classInfo.isInner) {
			af = af.remove(AccessFlags.STATIC).remove(AccessFlags.PRIVATE)
		}

		CodeGenUtils.addComments(clsCode, cls)
		CodeGenUtils.addClassRenamedComment(clsCode, cls)
		CodeGenUtils.addErrors(clsCode, cls)
		CodeGenUtils.addSourceFileInfo(clsCode, cls)
		CodeGenUtils.addInputFileInfo(clsCode, cls)

		annotationGen.addForClass(clsCode)
		clsCode.startLineWithNum(cls.getSourceLine()).add(af.makeString(cls.checkCommentsLevel(CommentsLevel.INFO)))
		if (af.isInterface()) {
			if (af.isAnnotation()) {
				clsCode.add('@')
			}
			clsCode.add("interface ")
		} else if (af.isEnum()) {
			clsCode.add("enum ")
		} else {
			clsCode.add("class ")
		}
		clsCode.attachDefinition(cls)
		clsCode.add(cls.classInfo.aliasShortName)

		addGenericTypeParameters(clsCode, cls.getGenericTypeParameters(), true)
		clsCode.add(' ')

		val sup = cls.superClass
		if (sup != null &&
			sup != ArgType.OBJECT &&
			!cls.contains(AFlag.REMOVE_SUPER_CLASS)
		) {
			clsCode.add("extends ")
			useClass(clsCode, sup)
			clsCode.add(' ')
		}

		if (!cls.interfaces.isEmpty() && !af.isAnnotation()) {
			if (cls.accessFlags.isInterface()) {
				clsCode.add("extends ")
			} else {
				clsCode.add("implements ")
			}
			val it = cls.interfaces.iterator()
			while (it.hasNext()) {
				val interf = it.next()
				useClass(clsCode, interf)
				if (it.hasNext()) {
					clsCode.add(", ")
				}
			}
			if (!cls.interfaces.isEmpty()) {
				clsCode.add(' ')
			}
		}
	}

	fun addGenericTypeParameters(code: ICodeWriter, generics: List<ArgType>?, classDeclaration: Boolean): Boolean {
		if (generics == null || generics.isEmpty()) {
			return false
		}
		code.add('<')
		var i = 0
		for (genericInfo in generics) {
			if (i != 0) {
				code.add(", ")
			}
			if (genericInfo.isGenericType()) {
				code.add(genericInfo.getObject())
			} else {
				useClass(code, genericInfo)
			}
			val list = genericInfo.getExtendTypes()
			if (!list.isEmpty()) {
				code.add(" extends ")
				val it = list.iterator()
				while (it.hasNext()) {
					val g = it.next()
					if (g.isGenericType()) {
						code.add(g.getObject())
					} else {
						useClass(code, g)
						if (classDeclaration &&
							!cls.classInfo.isInner &&
							cls.root().getArgs().isUseImports
						) {
							addImport(ClassInfo.fromType(cls.root(), g))
						}
					}
					if (it.hasNext()) {
						code.add(" & ")
					}
				}
			}
			i++
		}
		code.add('>')
		return true
	}

	@Throws(CodegenException::class)
	fun addClassBody(clsCode: ICodeWriter) {
		addClassBody(clsCode, false)
	}

	/**
	 * @param printClassName 是否把原类名以注释形式打印（用于内联类）
	 */
	@Throws(CodegenException::class)
	fun addClassBody(clsCode: ICodeWriter, printClassName: Boolean) {
		clsCode.add('{')
		if (printClassName && cls.checkCommentsLevel(CommentsLevel.INFO)) {
			clsCode.add(" // from class: " + cls.classInfo.fullName)
		}
		isBodyGenStarted = true
		clsDeclOffset = clsCode.getLength()
		clsCode.incIndent()
		addFields(clsCode)
		addInnerClsAndMethods(clsCode)
		clsCode.decIndent()
		clsCode.startLine('}')
		clsCode.attachAnnotation(NodeEnd.VALUE)
	}

	private fun addInnerClsAndMethods(clsCode: ICodeWriter) {
		val nodes = ArrayList<NotificationAttrNode>(cls.innerClasses.size + cls.methods.size)
		nodes.addAll(cls.innerClasses)
		nodes.addAll(cls.methods)
		nodes.removeIf { node -> skipNode(node) }
		nodes.sortBy { it.getSourceLine() }
		for (node in nodes) {
			if (node is ClassNode) {
				addInnerClass(clsCode, node)
			} else {
				addMethod(clsCode, node as MethodNode)
			}
		}
	}

	private fun skipNode(node: NotificationAttrNode): Boolean {
		if (fallback) {
			return false
		}
		if (Consts.DEBUG_ATTRIBUTES) {
			if (node.contains(AType.JADX_COMMENTS)) {
				return false
			}
		}
		return node.contains(AFlag.DONT_GENERATE)
	}

	private fun addInnerClass(code: ICodeWriter, innerCls: ClassNode) {
		try {
			val inClGen = ClassGen(innerCls, parentGen)
			code.newLine()
			inClGen.addClassCode(code)
			importsSet.addAll(inClGen.imports)
		} catch (e: Exception) {
			innerCls.addError("Inner class code generation error", e)
		}
	}

	private val isInnerClassesPresents: Boolean
		get() {
			for (innerCls in cls.innerClasses) {
				if (!innerCls.contains(AType.ANONYMOUS_CLASS)) {
					return true
				}
			}
			return false
		}

	private fun addMethod(code: ICodeWriter, mth: MethodNode) {
		if (skipMethod(mth)) {
			return
		}
		if (code.getLength() != clsDeclOffset) {
			code.newLine()
		}
		val savedIndent = code.getIndent()
		try {
			addMethodCode(code, mth)
		} catch (e: Exception) {
			if (mth.parentClass.getTopParentClass().contains(AFlag.RESTART_CODEGEN)) {
				throw JadxRuntimeException("Method generation error", e)
			}
			mth.addError("Method generation error", e)
			CodeGenUtils.addErrors(code, mth)
			code.setIndent(savedIndent)
		}
	}

	/**
	 * 内联方法是否需要跳过输出的额外检查。
	 */
	private fun skipMethod(mth: MethodNode): Boolean {
		if (cls.root().getArgs().decompilationMode.isSpecial()) {
			// show all methods for special decompilation modes
			return false
		}
		val inlineAttr = mth.get(AType.METHOD_INLINE)
		if (inlineAttr == null || inlineAttr.notNeeded()) {
			return false
		}
		try {
			if (mth.getUseIn().isEmpty()) {
				mth.add(AFlag.DONT_GENERATE)
				return true
			}
			val useInCompleted = mth.getUseIn().filter { m -> m.getTopParentClass().state.isProcessComplete() }
			if (useInCompleted.isEmpty()) {
				mth.add(AFlag.DONT_GENERATE)
				return true
			}
			mth.addDebugComment("Method not inlined, still used in: $useInCompleted")
			return false
		} catch (e: Exception) {
			// check failed => keep method
			mth.addWarnComment("Failed to check method usage", e)
			return false
		}
	}

	private val isMethodsPresents: Boolean
		get() {
			for (mth in cls.methods) {
				if (!mth.contains(AFlag.DONT_GENERATE)) {
					return true
				}
			}
			return false
		}

	@Throws(CodegenException::class)
	fun addMethodCode(code: ICodeWriter, mth: MethodNode) {
		CodeGenUtils.addErrorsAndComments(code, mth)
		if (mth.isNoCode()) {
			val mthGen = MethodGen(this, mth)
			mthGen.addDefinition(code)
			code.add(';')
		} else {
			var badCode = mth.contains(AFlag.INCONSISTENT_CODE)
			if (badCode && showInconsistentCode) {
				badCode = false
			}
			val mthGen: MethodGen
			if (badCode || fallback || mth.contains(AType.JADX_ERROR)) {
				mthGen = MethodGen.getFallbackMethodGen(mth)
			} else {
				mthGen = MethodGen(this, mth)
			}
			if (mthGen.addDefinition(code)) {
				code.add(' ')
			}
			code.add('{')
			code.incIndent()
			mthGen.addInstructions(code)
			code.decIndent()
			code.startLine('}')
			code.attachAnnotation(NodeEnd.VALUE)
		}
	}

	@Throws(CodegenException::class)
	private fun addFields(code: ICodeWriter) {
		addEnumFields(code)
		for (f in cls.fields) {
			addField(code, f)
		}
	}

	fun addField(code: ICodeWriter, f: FieldNode) {
		if (f.contains(AFlag.DONT_GENERATE)) {
			return
		}
		if (f.contains(JadxAttrType.ANNOTATION_LIST) ||
			f.contains(AType.JADX_COMMENTS) ||
			f.contains(AType.CODE_COMMENTS) ||
			f.getFieldInfo().hasAlias()
		) {
			code.newLine()
		}
		if (Consts.DEBUG_USAGE) {
			addFieldUsageInfo(code, f)
		}
		CodeGenUtils.addComments(code, f)
		if (f.getFieldInfo().hasAlias()) {
			CodeGenUtils.addRenamedComment(code, f, f.getName())
		}
		annotationGen.addForField(code, f)

		code.startLine(f.accessFlags.makeString(f.checkCommentsLevel(CommentsLevel.INFO)))
		useType(code, f.type)
		code.add(' ')
		code.attachDefinition(f)
		code.add(f.getAlias())

		val initInsnAttr = f.get(AType.FIELD_INIT_INSN)
		if (initInsnAttr != null) {
			val insnGen = makeInsnGen(initInsnAttr.getInsnMth())
			code.add(" = ")
			addInsnBody(insnGen, code, initInsnAttr.insn)
		} else {
			val constVal = f.get(JadxAttrType.CONSTANT_VALUE)
			if (constVal != null) {
				code.add(" = ")
				if (constVal.type == EncodedType.ENCODED_NULL) {
					code.add(TypeGen.literalToString(0L, f.type, cls, fallback))
				} else {
					val value = EncodedValueUtils.convertToConstValue(constVal)
					if (value is LiteralArg) {
						val lit = value.literal
						code.add(getIntegerString(lit, f.type))
					} else {
						annotationGen.encodeValue(cls.root(), code, constVal)
					}
				}
			}
		}
		code.add(';')
	}

	private fun getIntegerString(lit: Long, type: ArgType): String {
		if (integerFormat != IntegerFormat.DECIMAL && AndroidResourcesUtils.isResourceFieldValue(cls, type)) {
			return String.format("0x%08x", lit)
		}
		// force literal type to be same as field (java bytecode can use different type)
		return TypeGen.literalToString(lit, type, cls, fallback)
	}

	private val isFieldsPresents: Boolean
		get() {
			for (field in cls.fields) {
				if (!field.contains(AFlag.DONT_GENERATE)) {
					return true
				}
			}
			return false
		}

	@Throws(CodegenException::class)
	private fun addEnumFields(code: ICodeWriter) {
		val enumFields = cls.get(AType.ENUM_CLASS)
		if (enumFields == null) {
			return
		}
		var igen: InsnGen? = null
		val it = enumFields.fields.iterator()
		while (it.hasNext()) {
			val f = it.next()

			CodeGenUtils.addComments(code, f.field)
			code.startLine(f.field.getAlias())
			val constrInsn = f.constrInsn
			val callMth = cls.root().resolveMethod(constrInsn.callMth)
			val skipCount = getEnumCtrSkipArgsCount(callMth)
			if (constrInsn.getArgsCount() > skipCount) {
				if (igen == null) {
					igen = makeInsnGen(checkNotNull(enumFields.staticMethod))
				}
				igen.generateMethodArguments(code, constrInsn, 0, callMth)
			}
			val enumCls = f.cls
			if (enumCls != null) {
				code.add(' ')
				ClassGen(enumCls, this).addClassBody(code, true)
			}
			if (it.hasNext()) {
				code.add(',')
			}
		}
		if (isMethodsPresents || isFieldsPresents || isInnerClassesPresents) {
			if (enumFields.fields.isEmpty()) {
				code.startLine()
			}
			code.add(';')
			if (isFieldsPresents) {
				code.newLine()
			}
		}
	}

	private fun getEnumCtrSkipArgsCount(callMth: MethodNode?): Int {
		if (callMth != null) {
			val skipArgsAttr = callMth.get(AType.SKIP_MTH_ARGS)
			if (skipArgsAttr != null) {
				return skipArgsAttr.getSkipCount()
			}
		}
		return 0
	}

	private fun makeInsnGen(mth: MethodNode): InsnGen {
		val mthGen = MethodGen(this, mth)
		return InsnGen(mthGen, false)
	}

	private fun addInsnBody(insnGen: InsnGen, code: ICodeWriter, insn: InsnNode) {
		try {
			insnGen.makeInsn(insn, code, InsnGen.Flags.BODY_ONLY_NOWRAP)
		} catch (e: Exception) {
			cls.addError("Failed to generate init code", e)
		}
	}

	fun useType(code: ICodeWriter, type: ArgType) {
		val stype = type.getPrimitiveType()
		if (stype == null) {
			code.add(type.toString())
		} else if (stype == PrimitiveType.OBJECT) {
			if (type.isGenericType()) {
				code.add(type.getObject())
			} else {
				useClass(code, type)
			}
		} else if (stype == PrimitiveType.ARRAY) {
			useType(code, checkNotNull(type.getArrayElement()))
			code.add("[]")
		} else {
			code.add(stype.longName)
		}
	}

	fun useClass(code: ICodeWriter, rawCls: String) {
		useClass(code, ArgType.`object`(rawCls))
	}

	fun useClass(code: ICodeWriter, type: ArgType) {
		val outerType = type.getOuterType()
		if (outerType != null) {
			useClass(code, outerType)
			code.add('.')
			addInnerType(code, type)
			return
		}
		useClass(code, ClassInfo.fromType(cls.root(), type))
		addGenerics(code, type)
	}

	private fun addInnerType(code: ICodeWriter, baseType: ArgType) {
		val innerType = checkNotNull(baseType.getInnerType())
		val outerType = innerType.getOuterType()
		if (outerType != null) {
			useClassWithShortName(code, baseType, outerType)
			code.add('.')
			addInnerType(code, innerType)
			return
		}
		useClassWithShortName(code, baseType, innerType)
	}

	private fun useClassWithShortName(code: ICodeWriter, baseType: ArgType, type: ArgType) {
		val fullNameObj: String
		if (type.getObject().contains(".")) {
			fullNameObj = type.getObject()
		} else {
			fullNameObj = baseType.getObject()
		}
		val classInfo = ClassInfo.fromName(cls.root(), fullNameObj)
		val classNode = cls.root().resolveClass(classInfo)
		if (classNode != null) {
			code.attachAnnotation(classNode)
		}
		code.add(classInfo.aliasShortName)
		addGenerics(code, type)
	}

	private fun addGenerics(code: ICodeWriter, type: ArgType) {
		val generics = type.getGenericTypes()
		if (generics != null) {
			code.add('<')
			val len = generics.size
			for (i in 0 until len) {
				if (i != 0) {
					code.add(", ")
				}
				val gt = generics[i]
				val wt = gt.getWildcardType()
				if (wt != null) {
					val bound = gt.getWildcardBound()
					code.add(checkNotNull(bound).str)
					if (bound != ArgType.WildcardBound.UNBOUND) {
						useType(code, wt)
					}
				} else {
					useType(code, gt)
				}
			}
			code.add('>')
		}
	}

	fun useClass(code: ICodeWriter, classInfo: ClassInfo) {
		val classNode = cls.root().resolveClass(classInfo)
		if (classNode != null) {
			useClass(code, classNode)
		} else {
			addClsName(code, classInfo)
		}
	}

	fun useClass(code: ICodeWriter, classNode: ClassNode) {
		code.attachAnnotation(classNode)
		addClsName(code, classNode.classInfo)
	}

	fun addClsName(code: ICodeWriter, classInfo: ClassInfo) {
		val clsName = useClassInternal(cls.classInfo, classInfo)
		code.add(clsName)
	}

	fun addClsShortNameForced(code: ICodeWriter, classInfo: ClassInfo) {
		code.add(classInfo.aliasShortName)
		if (!isBothClassesInOneTopClass(cls.classInfo, classInfo)) {
			addImport(classInfo)
		}
	}

	private fun useClassInternal(useCls: ClassInfo, extClsInfo: ClassInfo): String {
		var fullName = extClsInfo.aliasFullName
		if (fallback || !useImports) {
			return fullName
		}
		val shortName = extClsInfo.aliasShortName
		if (useCls == extClsInfo) {
			return shortName
		}
		if (extClsInfo.aliasPkg.isEmpty()) {
			// omit import for default package
			return shortName
		}
		if (isClassInnerFor(useCls, extClsInfo)) {
			return shortName
		}
		if (extClsInfo.isInner) {
			return expandInnerClassName(useCls, extClsInfo)
		}
		if (checkInnerCollision(cls.root(), useCls, extClsInfo) ||
			checkInPackageCollision(cls.root(), useCls, extClsInfo)
		) {
			return fullName
		}
		if (isBothClassesInOneTopClass(useCls, extClsInfo)) {
			return shortName
		}
		// don't add import for top classes from 'java.lang' package (subpackages excluded)
		if (extClsInfo.getPackage() == "java.lang" && extClsInfo.parentClass == null) {
			return shortName
		}
		if (extClsInfo.aliasPkg == useCls.aliasPkg) {
			if (!extClsInfo.isInner) {
				// don't add import if this class from same package
				return shortName
			}
			fullName = extClsInfo.aliasNameWithoutPackage
		}
		for (importCls in imports) {
			if (importCls != extClsInfo && importCls.aliasShortName == shortName) {
				if (extClsInfo.isInner) {
					val parent = useClassInternal(useCls, checkNotNull(extClsInfo.parentClass))
					return "$parent.$shortName"
				} else {
					return fullName
				}
			}
		}
		addImport(extClsInfo)
		return shortName
	}

	private fun expandInnerClassName(useCls: ClassInfo, extClsInfo: ClassInfo): String {
		val clsList = ArrayList<ClassInfo>()
		clsList.add(extClsInfo)
		var parentCls = extClsInfo.parentClass
		var addImport = true
		while (parentCls != null) {
			if (parentCls === useCls || isClassInnerFor(useCls, parentCls)) {
				addImport = false
				break
			}
			clsList.add(parentCls)
			parentCls = parentCls.parentClass
		}
		clsList.reverse()
		if (addImport) {
			val top = clsList[0]
			if (top !== extClsInfo) {
				val usedName = useClassInternal(useCls, top)
				if (usedName != top.aliasShortName) {
					// short top name can't be used, use full name as fallback
					return extClsInfo.aliasFullName
				}
			} else {
				addImport(top)
			}
		}
		return Utils.listToString(clsList, ".") { it.aliasShortName }
	}

	private fun addImport(classInfo: ClassInfo) {
		if (parentGenRef != null) {
			parentGenRef.addImport(classInfo)
		} else {
			importsSet.add(classInfo)
		}
	}

	val imports: Set<ClassInfo> get() = parentGenRef?.imports ?: importsSet

	companion object {
		private fun isBothClassesInOneTopClass(useCls: ClassInfo, extClsInfo: ClassInfo): Boolean {
			val a = useCls.topParentClass
			val b = extClsInfo.topParentClass
			if (a != null) {
				return a == b
			}
			// useCls - is a top class
			return useCls == b
		}

		private fun isClassInnerFor(inner: ClassInfo, parent: ClassInfo): Boolean {
			if (inner.isInner) {
				val p = checkNotNull(inner.parentClass)
				return p == parent || isClassInnerFor(p, parent)
			}
			return false
		}

		private fun checkInnerCollision(root: RootNode, useCls: ClassInfo?, searchCls: ClassInfo): Boolean {
			if (useCls == null) {
				return false
			}
			val shortName = searchCls.aliasShortName
			if (useCls.aliasShortName == shortName) {
				return true
			}
			val classNode = root.resolveClass(useCls)
			if (classNode != null) {
				for (inner in classNode.innerClasses) {
					if (inner.shortName == shortName &&
						inner.fullName != searchCls.aliasFullName
					) {
						return true
					}
				}
			}
			return checkInnerCollision(root, useCls.parentClass, searchCls)
		}

		/**
		 * 检查当前包中是否已存在同名类。
		 */
		private fun checkInPackageCollision(root: RootNode, useCls: ClassInfo, searchCls: ClassInfo): Boolean {
			val currentPkg = useCls.aliasPkg
			if (currentPkg == searchCls.aliasPkg) {
				// search class already from current package
				return false
			}
			val shortName = searchCls.aliasShortName
			return checkNotNull(root.getClsp()).isClsKnown("$currentPkg.$shortName")
		}

		private fun addClassUsageInfo(code: ICodeWriter, cls: ClassNode) {
			val deps = cls.dependencies
			code.startLine("// deps - ").add(deps.size.toString())
			for (depCls in deps) {
				code.startLine("//  ").add(depCls.classInfo.fullName)
			}
			val useIn = cls.getUseIn()
			code.startLine("// use in - ").add(useIn.size.toString())
			for (useCls in useIn) {
				code.startLine("//  ").add(useCls.classInfo.fullName)
			}
			val useInMths = cls.useInMth
			code.startLine("// use in methods - ").add(useInMths.size.toString())
			for (useMth in useInMths) {
				code.startLine("//  ").add(useMth.toString())
			}
		}

		internal fun addMthUsageInfo(code: ICodeWriter, mth: MethodNode) {
			val useInMths = mth.getUseIn()
			code.startLine("// use in methods - ").add(useInMths.size.toString())
			for (useMth in useInMths) {
				code.startLine("//  ").add(useMth.toString())
			}
		}

		private fun addFieldUsageInfo(code: ICodeWriter, fieldNode: FieldNode) {
			val useInMths = fieldNode.getUseIn()
			code.startLine("// use in methods - ").add(useInMths.size.toString())
			for (useMth in useInMths) {
				code.startLine("//  ").add(useMth.toString())
			}
		}
	}

	val parentGen: ClassGen get() = parentGenRef ?: this

	val isFallbackMode: Boolean get() = fallback
}
