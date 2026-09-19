package jadx.core.dex.nodes

import jadx.api.DecompilationMode
import jadx.api.ICodeInfo
import jadx.api.JavaClass
import jadx.api.impl.SimpleCodeInfo
import jadx.api.impl.SimpleCodeWriter
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.api.metadata.annotations.VarRef
import jadx.api.plugins.input.data.IClassData
import jadx.api.plugins.input.data.IFieldData
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationDefaultAttr
import jadx.api.plugins.input.data.attributes.types.AnnotationDefaultClassAttr
import jadx.api.plugins.input.data.attributes.types.InnerClassesAttr
import jadx.api.plugins.input.data.attributes.types.SourceFileAttr
import jadx.api.plugins.input.data.impl.ListConsumer
import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.InlinedAttr
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.ListUtils.safeAdd
import jadx.core.utils.ListUtils.safeRemoveAndTrim
import jadx.core.utils.Utils.collectionMap
import jadx.core.utils.Utils.getOrElse
import jadx.core.utils.Utils.getStackTrace
import jadx.core.utils.Utils.notEmpty
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory

class ClassNode(
	val root: RootNode,
	private val cls: IClassData?,
) : NotificationAttrNode(),
	ILoadable,
	ICodeNode,
	IPackageUpdate,
	Comparable<ClassNode> {
	companion object {
		private val LOG = LoggerFactory.getLogger(ClassNode::class.java)
		private val DECOMPILE_WITH_MODE_SYNC = Any()

		fun addSyntheticClass(root: RootNode, name: String, accessFlags: Int): ClassNode {
			val classInfo = ClassInfo.fromName(root, name) ?: throw JadxRuntimeException("Invalid class name: $name")
			if (root.resolveClass(classInfo) != null) {
				throw JadxRuntimeException("Class already exist: $name")
			}
			return addSyntheticClass(root, classInfo, accessFlags)
		}

		fun addSyntheticClass(root: RootNode, classInfo: ClassInfo, accessFlags: Int): ClassNode {
			val cls = ClassNode(root, classInfo, accessFlags)
			cls.add(AFlag.SYNTHETIC)
			cls.inputFileName = "synthetic"
			cls.state = ProcessState.PROCESS_COMPLETE
			root.addClassNode(cls)
			return cls
		}

		private fun processSpecialClasses(cls: ClassNode) {
			if (cls.name == "package-info" && cls.fields.isEmpty() && cls.methods.isEmpty()) {
				cls.add(AFlag.PACKAGE_INFO)
				cls.add(AFlag.DONT_RENAME)
			}
		}

		private fun processAttributes(cls: ClassNode) {
			val defAttr = cls.get(JadxAttrType.ANNOTATION_DEFAULT_CLASS)
			if (defAttr != null) {
				cls.remove(JadxAttrType.ANNOTATION_DEFAULT_CLASS)
				for ((key, value) in defAttr.values) {
					val mth = cls.searchMethodByShortName(key)
					if (mth != null) {
						mth.addAttr(AnnotationDefaultAttr(value))
					} else {
						cls.addWarnComment("Method from annotation default annotation not found: $key")
					}
				}
			}
			if (!cls.checkSourceFilenameAttr()) {
				cls.remove(JadxAttrType.SOURCE_FILE)
			}
		}

		private fun processDefinitionAnnotations(codeInfo: ICodeInfo) {
			val annotations = codeInfo.codeMetadata.getAsMap()
			if (annotations.isEmpty()) return
			for ((pos, ann) in annotations as Map<Int, ICodeAnnotation>) {
				if (ann.getAnnType() == ICodeAnnotation.AnnType.DECLARATION) {
					val declareRef = ann as NodeDeclareRef
					declareRef.setDefPos(pos)
					declareRef.node.setDefPosition(pos)
				}
			}
			val values: MutableList<ICodeAnnotation> = ArrayList(annotations.values)
			values.removeIf { v ->
				if (v.getAnnType() == ICodeAnnotation.AnnType.VAR_REF) {
					val varRef = v as VarRef
					if (varRef.refPos == 0) {
						LOG.debug("Var reference '{}' incorrect (ref pos is zero) and was removed from metadata", varRef)
						return@removeIf true
					}
				}
				false
			}
		}
	}

	private lateinit var clsData: IClassData
	lateinit var classInfo: ClassInfo
	lateinit var packageNode: PackageNode
	override var accessFlags: AccessInfo = AccessInfo(0, AccessInfo.AFType.CLASS)
	var superClass: ArgType? = null
	lateinit var interfaces: List<ArgType>
	private var generics: List<ArgType> = emptyList()
	@get:JvmName("inputFileNameValue")
	var inputFileName: String? = null

	lateinit var methods: List<MethodNode>
	lateinit var fields: List<FieldNode>
	var innerClasses: MutableList<ClassNode> = ArrayList()
	private var inlinedClasses: MutableList<ClassNode> = ArrayList()

	private var smali: String? = null
	var parentClass: ClassNode = this

	@Volatile
	var state: ProcessState = ProcessState.NOT_LOADED
	var loadStage: LoadStage = LoadStage.NONE

	var dependencies: List<ClassNode> = emptyList()
	var codegenDeps: List<ClassNode> = emptyList()
	@get:JvmName("useInValue")
	var useIn: List<ClassNode> = emptyList()
	var useInMth: List<MethodNode> = emptyList()


	private var mthInfoMap: MutableMap<MethodInfo, MethodNode> = HashMap()
	var javaNode: JavaClass? = null

	init {
		if (cls != null) {
			this.clsData = cls.copy()
			this.classInfo = ClassInfo.fromType(root, ArgType.`object`(cls.getType()))!!
			load(this.clsData, false)
		}
	}

	private constructor(root: RootNode, classInfo: ClassInfo, accessFlags: Int) : this(root, null as IClassData?) {
		this.classInfo = classInfo
		this.accessFlags = AccessInfo(accessFlags, AccessInfo.AFType.CLASS)
		this.superClass = null
		this.interfaces = ArrayList()
		this.methods = ArrayList()
		this.fields = ArrayList()
		this.inputFileName = null
		this.packageNode = PackageNode.getForClass(root, classInfo.`package`, this)
	}

	private fun load(cls: IClassData?, reloading: Boolean) {
		try {
			addAttrs(cls!!.getAttributes())
			accessFlags = AccessInfo(getAccessFlags(cls), AccessInfo.AFType.CLASS)
			superClass = checkSuperType(cls)
			interfaces = collectionMap(cls.getInterfacesTypes()) { ArgType.`object`(it) }
			inputFileName = cls.getInputFileName()

			val fieldsConsumer = ListConsumer<IFieldData, FieldNode> { fld -> FieldNode.build(this, fld) }
			val methodsConsumer = ListConsumer<IMethodData, MethodNode> { mth -> MethodNode.build(this, mth) }
			cls.visitFieldsAndMethods(fieldsConsumer, methodsConsumer)
			fields = fieldsConsumer.getResult()
			methods = methodsConsumer.getResult()
			if (reloading) {
				restoreUsageData()
			}
			initStaticValues(fields)
			processAttributes(this)
			processSpecialClasses(this)
			buildCache()

			if (accessFlags.isModuleInfo()) {
				addWarnComment("Modules not supported yet")
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Error decode class: $classInfo", e)
		}
	}

	private fun restoreUsageData() {
		val usageInfoData = root.args.usageInfoCache.get(root)
		if (usageInfoData != null) {
			usageInfoData.applyForClass(this)
		} else {
			LOG.warn("Can't restore usage data for class: {}", this)
		}
	}

	private fun checkSuperType(cls: IClassData): ArgType? {
		val superType = cls.getSuperType()
		if (superType == null) {
			if (classInfo.getType().getObject() == Consts.CLASS_OBJECT) return null
			if (accessFlags.isModuleInfo()) return null
			throw JadxRuntimeException("No super class in ${classInfo.type}")
		}
		return ArgType.`object`(superType)
	}

	fun updateGenericClsData(generics: List<ArgType>, superClass: ArgType, interfaces: List<ArgType>) {
		this.generics = generics
		this.superClass = superClass
		this.interfaces = interfaces
	}

	private fun getAccessFlags(cls: IClassData): Int {
		val innerClassesAttr = get(JadxAttrType.INNER_CLASSES) as? InnerClassesAttr
		if (innerClassesAttr != null) {
			val innerClsInfo = innerClassesAttr.map[cls.getType()]
			if (innerClsInfo != null) return innerClsInfo.accessFlags
		}
		return cls.getAccessFlags()
	}

	private fun initStaticValues(fields: List<FieldNode>) {
		for (fld in fields) {
			val accFlags = fld.accFlags
			if (accFlags.isStatic() && accFlags.isFinal() && fld.get(JadxAttrType.CONSTANT_VALUE) == null) {
				fld.addAttr(EncodedValue.NULL)
			}
		}
	}

	private fun checkSourceFilenameAttr(): Boolean {
		val sourceFileAttr = get(JadxAttrType.SOURCE_FILE) as? SourceFileAttr ?: return true
		var fileName = sourceFileAttr.fileName
		if (fileName.endsWith(".java")) {
			fileName = fileName.substring(0, fileName.length - 5)
		}
		if (fileName.isEmpty() || fileName == "SourceFile") return false
		val name = classInfo.shortName
		if (fileName == name) return false
		var parentCls = classInfo.parentClass
		while (parentCls != null) {
			val parentName = parentCls.shortName
			if (parentName == fileName || parentName.startsWith("$fileName$")) return false
			parentCls = parentCls.parentClass
		}
		if (fileName.contains('$') && fileName.endsWith('$' + name)) return false
		if (name.contains('$') && name.startsWith(fileName)) return false
		return true
	}

	fun checkProcessed(): Boolean = getTopParentClass().state.isProcessComplete()

	fun ensureProcessed() {
		if (!checkProcessed()) {
			val top = getTopParentClass()
			throw JadxRuntimeException("Expected class to be processed at this point, class: $top, state: ${top.state}")
		}
	}

	fun decompile(): ICodeInfo = decompile(true)

	fun decompileWithMode(mode: DecompilationMode): ICodeInfo = when (mode) {
		DecompilationMode.AUTO, DecompilationMode.RESTRUCTURE -> decompile(true)

		DecompilationMode.SIMPLE, DecompilationMode.FALLBACK -> synchronized(DECOMPILE_WITH_MODE_SYNC) {
			unload()
			val code = root.processClasses.forceGenerateCodeForMode(this, mode)
			try {
				getOrElse(code, ICodeInfo.EMPTY)
			} finally {
				unload()
			}
		}

		else -> throw JadxRuntimeException("Unknown mode: $mode")
	}

	fun getCode(): ICodeInfo = decompile(true)

	fun reloadCode(): ICodeInfo {
		add(AFlag.CLASS_DEEP_RELOAD)
		return decompile(false)
	}

	fun unloadCode() {
		if (state == ProcessState.NOT_LOADED) return
		add(AFlag.CLASS_UNLOADED)
		unloadFromCache()
		deepUnload()
	}

	fun deepUnload() {
		if (clsData == null) return
		clearAttributes()
		unload()
		root.constValues.removeForClass(this)
		load(clsData, true)
		for (innerCls in innerClasses) {
			innerCls.deepUnload()
		}
	}

	fun unloadFromCache() {
		if (isInner()) return
		val codeCache = root.getCodeCache()
		codeCache.remove(rawName as String)
	}

	private fun decompile(searchInCache: Boolean): ICodeInfo {
		if (isInner()) return ICodeInfo.EMPTY
		val codeCache = root.getCodeCache()
		val clsRawName = rawName
		if (searchInCache) {
			val code = codeCache.get(clsRawName as String)
			if (code != ICodeInfo.EMPTY) return code
		}
		val codeInfo = generateClassCode()
		if (codeInfo != ICodeInfo.EMPTY) {
			codeCache.add(clsRawName, codeInfo)
		}
		return codeInfo
	}

	private fun generateClassCode(): ICodeInfo {
		try {
			if (Consts.DEBUG) {
				LOG.debug("Decompiling class: {}", this)
			}
			val codeInfo = root.processClasses.generateCode(this)
			processDefinitionAnnotations(codeInfo)
			return codeInfo
		} catch (e: StackOverflowError) {
			addError("Code generation failed", e)
			return SimpleCodeInfo(getStackTrace(e))
		} catch (e: Exception) {
			addError("Code generation failed", e)
			return SimpleCodeInfo(getStackTrace(e))
		}
	}

	fun getCodeFromCache(): ICodeInfo? {
		val codeCache = root.getCodeCache()
		val clsRawName = rawName
		val codeInfo = codeCache.get(clsRawName as String)
		if (codeInfo == ICodeInfo.EMPTY) return null
		return codeInfo
	}

	override fun load() {
		for (mth in methods) {
			try {
				mth.load()
			} catch (e: Exception) {
				mth.addError("Method load error", e)
			}
		}
		for (innerCls in innerClasses) {
			innerCls.load()
		}
		state = ProcessState.LOADED
	}

	override fun unload() {
		if (state == ProcessState.NOT_LOADED) return
		synchronized(classInfo) {
			for (mth in methods) mth.unload()
			for (innerCls in innerClasses) innerCls.unload()
			for (fld in fields) fld.unload()
			unloadAttributes()
			state = ProcessState.NOT_LOADED
			loadStage = LoadStage.NONE
			smali = null
		}
	}

	private fun buildCache() {
		mthInfoMap = HashMap(methods.size)
		for (mth in methods) {
			mthInfoMap[mth.mthInfo] = mth
		}
	}



	fun getGenericTypeParameters(): List<ArgType> = generics

	fun getType(): ArgType {
		val clsType = classInfo.type
		if (notEmpty(generics)) {
			return ArgType.generic(clsType, generics)
		}
		return clsType
	}

	fun addField(fld: FieldNode) {
		if (fields.isEmpty()) {
			fields = ArrayList(1)
		}
		(fields as MutableList<FieldNode>).add(fld)
	}

	fun getConstField(obj: Any): IFieldInfoRef? = root.constValues.getConstField(this, obj, true)

	fun getConstField(obj: Any, searchGlobal: Boolean): IFieldInfoRef? = root.constValues.getConstField(this, obj, searchGlobal)

	fun getConstFieldByLiteralArg(arg: jadx.core.dex.instructions.args.LiteralArg): IFieldInfoRef? = root.constValues.getConstFieldByLiteralArg(this, arg)

	fun searchField(field: FieldInfo): FieldNode? {
		for (f in fields) {
			if (f.fieldInfo == field) return f
		}
		return null
	}

	fun searchFieldByNameAndType(field: FieldInfo): FieldNode? {
		for (f in fields) {
			if (f.fieldInfo.equalsNameAndType(field)) return f
		}
		return null
	}

	fun searchFieldByName(name: String): FieldNode? {
		for (f in fields) {
			if (f.getName() == name) return f
		}
		return null
	}

	fun searchFieldByShortId(shortId: String): FieldNode? {
		for (f in fields) {
			if (f.fieldInfo.shortId == shortId) return f
		}
		return null
	}

	fun searchMethod(mth: MethodInfo): MethodNode? = mthInfoMap[mth]

	fun searchMethodByShortId(shortId: String): MethodNode? {
		for (m in methods) {
			if (m.mthInfo.shortId == shortId) return m
		}
		return null
	}

	fun searchMethodByShortName(name: String): MethodNode? {
		for (m in methods) {
			if (m.mthInfo.name == name) return m
		}
		return null
	}

	override fun getDeclaringClass(): ClassNode? = if (isInner()) parentClass else null


	fun notInner() {
		classInfo.notInner(root)
		parentClass = this
	}

	override fun rename(newName: String) {
		if (!newName.contains('.')) {
			classInfo.changeShortName(newName)
			return
		}
		val newClsInfo = ClassInfo.fromNameWithoutCache(root, newName, classInfo.isInner)!!
		val newPkg = newClsInfo.`package`
		val newShortName = newClsInfo.shortName
		if (classInfo.isInner) {
			if (newPkg != classInfo.`package`) {
				addWarn("Can't change package for inner class: $this to $newName")
			}
			classInfo.changeShortName(newShortName)
		} else {
			if (changeClassNodePackage(newPkg)) {
				classInfo.changePkgAndName(newPkg, newShortName)
			} else {
				classInfo.changeShortName(newShortName)
			}
		}
	}

	private fun changeClassNodePackage(fullPkg: String): Boolean {
		if (fullPkg == classInfo.aliasPkg) return false
		if (classInfo.isInner) throw JadxRuntimeException("Can't change package for inner class: $classInfo")
		root.removeClsFromPackage(packageNode, this)
		packageNode = PackageNode.getForClass(root, fullPkg, this)
		root.sortPackages()
		return true
	}

	fun removeAlias() {
		if (!classInfo.isInner) {
			changeClassNodePackage(classInfo.`package`)
		}
		classInfo.removeAlias()
	}

	override fun onParentPackageUpdate(updatedPkg: PackageNode) {
		if (classInfo.isInner) return
		classInfo.changePkg(packageNode.aliasPkgInfo.fullName)
	}

	fun getTopParentClass(): ClassNode {
		var parent = parentClass
		while (parent != this && parent.parentClass != parent) {
			parent = parent.parentClass
		}
		return if (parent == this) this else parent
	}

	fun visitParentClasses(consumer: java.util.function.Consumer<ClassNode>) {
		var currentCls = this
		var parentCls = currentCls.parentClass
		while (parentCls != currentCls) {
			consumer.accept(parentCls)
			currentCls = parentCls
			parentCls = currentCls.parentClass
		}
	}

	fun visitSuperTypes(consumer: java.util.function.BiConsumer<ArgType, ArgType>) {
		val typeUtils = root.typeUtils
		val thisType = getType()
		if (superClass != null && superClass != ArgType.OBJECT) {
			consumer.accept(thisType, superClass!!)
			typeUtils.visitSuperTypes(superClass!!, consumer)
		}
		for (iface in interfaces) {
			consumer.accept(thisType, iface)
			typeUtils.visitSuperTypes(iface, consumer)
		}
	}

	fun hasNotGeneratedParent(): Boolean {
		if (contains(AFlag.DONT_GENERATE)) return true
		val parent = parentClass
		return if (parent == this) false else parent.hasNotGeneratedParent()
	}


	fun getInlinedClasses(): List<ClassNode> = inlinedClasses

	fun getInnerAndInlinedClassesRecursive(resultClassesSet: MutableSet<ClassNode>) {
		for (innerCls in innerClasses) {
			if (resultClassesSet.add(innerCls)) {
				innerCls.getInnerAndInlinedClassesRecursive(resultClassesSet)
			}
		}
		for (inlinedCls in inlinedClasses) {
			if (resultClassesSet.add(inlinedCls)) {
				inlinedCls.getInnerAndInlinedClassesRecursive(resultClassesSet)
			}
		}
	}

	fun getInnerClassesRecursive(resultClassesSet: MutableSet<ClassNode>) {
		for (innerCls in innerClasses) {
			if (resultClassesSet.add(innerCls)) {
				innerCls.getInnerAndInlinedClassesRecursive(resultClassesSet)
			}
		}
	}

	fun addInnerClass(cls: ClassNode) {
		if (innerClasses.isEmpty()) {
			innerClasses = ArrayList(5)
		}
		innerClasses.add(cls)
		cls.parentClass = this
	}

	fun addInlinedClass(cls: ClassNode) {
		if (inlinedClasses.isEmpty()) {
			inlinedClasses = ArrayList(5)
		}
		cls.addAttr(InlinedAttr(this))
		inlinedClasses.add(cls)
	}

	fun isEnum(): Boolean = accessFlags.isEnum() && superClass != null && superClass!!.getObject() == ArgType.ENUM.getObject()

	fun isAnonymous(): Boolean = contains(AType.ANONYMOUS_CLASS)

	fun isSynthetic(): Boolean = contains(AFlag.SYNTHETIC)

	fun isInner(): Boolean = parentClass != this

	fun isTopClass(): Boolean = parentClass == this

	fun getClassInitMth(): MethodNode? = searchMethodByShortId("<clinit>()V")

	fun getDefaultConstructor(): MethodNode? {
		for (mth in methods) {
			if (mth.isDefaultConstructor()) return mth
		}
		return null
	}

	override fun root(): RootNode = root

	override fun typeName(): String = "class"

	val rawName: String get() = classInfo.rawName


	val name: String get() = classInfo.shortName

	val alias: String get() = classInfo.aliasShortName

	@Deprecated("Use getAlias()")
	val shortName: String get() = classInfo.aliasShortName

	val fullName: String get() = classInfo.aliasFullName

	val `package`: String get() = classInfo.aliasPkg

	fun getDisassembledCode(): String {
		if (smali == null) {
			val code = SimpleCodeWriter(root.args)
			getDisassembledCode(code)
			val allInlinedClasses = LinkedHashSet<ClassNode>()
			getInnerAndInlinedClassesRecursive(allInlinedClasses)
			for (innerClass in allInlinedClasses) {
				innerClass.getDisassembledCode(code)
			}
			smali = code.finish().codeStr
		}
		return smali!!
	}

	protected fun getDisassembledCode(code: SimpleCodeWriter) {
		if (clsData == null) {
			code.startLine("###### Class $fullName is created by jadx")
			return
		}
		code.startLine("###### Class $fullName ($rawName)")
		try {
			code.startLine(clsData!!.getDisassembledCode())
		} catch (e: Exception) {
			code.startLine("Failed to disassemble class:")
			code.startLine(getStackTrace(e))
		}
	}

	fun getClsData(): IClassData? = clsData

	fun reloadAtCodegenStage() {
		val topCls = getTopParentClass()
		if (topCls.loadStage == LoadStage.CODEGEN_STAGE) {
			throw JadxRuntimeException("Class not yet loaded at codegen stage: $topCls")
		}
		topCls.add(AFlag.RELOAD_AT_CODEGEN_STAGE)
	}

	fun removeDependency(dep: ClassNode) {
		dependencies = safeRemoveAndTrim(dependencies, dep)
	}

	fun addCodegenDep(dep: ClassNode) {
		if (!codegenDeps.contains(dep)) {
			codegenDeps = safeAdd(codegenDeps, dep)
		}
	}

	fun getTotalDepsCount(): Int = dependencies.size + codegenDeps.size





	override fun getInputFileName(): String? = inputFileName

	override fun getUseIn(): List<out ICodeNode> = useIn

	override fun getAnnType() = ICodeAnnotation.AnnType.CLASS

	override fun hashCode(): Int = classInfo.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ClassNode) return false
		return classInfo == other.classInfo
	}

	override fun compareTo(o: ClassNode): Int = classInfo.compareTo(o.classInfo)

	override fun toString(): String = classInfo.fullName
}
