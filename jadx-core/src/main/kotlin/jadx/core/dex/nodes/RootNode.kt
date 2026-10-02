package jadx.core.dex.nodes

import jadx.api.DecompilationMode
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.ResourcesLoader
import jadx.api.data.ICodeData
import jadx.api.impl.passes.DecompilePassWrapper
import jadx.api.impl.passes.PreparePassWrapper
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.data.IClassData
import jadx.api.plugins.pass.JadxPass
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.api.plugins.pass.types.JadxPassType
import jadx.api.plugins.pass.types.JadxPreparePass
import jadx.core.Jadx
import jadx.core.ProcessClass
import jadx.core.clsp.ClspGraph
import jadx.core.dex.attributes.AttributeStorage
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.ConstStorage
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.InfoStorage
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.info.PackageInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.utils.MethodUtils
import jadx.core.dex.nodes.utils.SelectFromDuplicates
import jadx.core.dex.nodes.utils.TypeUtils
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.dex.visitors.typeinference.TypeCompare
import jadx.core.dex.visitors.typeinference.TypeUpdate
import jadx.core.export.GradleInfoStorage
import jadx.core.utils.CacheStorage
import jadx.core.utils.ErrorsCounter
import jadx.core.utils.PassMerge
import jadx.core.utils.StringUtils
import jadx.core.utils.Utils.checkThreadInterrupt
import jadx.core.utils.android.AndroidResourcesUtils.searchAppResClass
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.xmlgen.IResTableParser
import jadx.core.xmlgen.ManifestAttributes
import jadx.core.xmlgen.ResourceStorage
import jadx.core.xmlgen.entry.ValuesParser
import org.slf4j.LoggerFactory
import jadx.core.dex.visitors.DepthTraversal.visit as depthVisit
import jadx.core.utils.DebugChecks.insertPasses as insertDebugPasses

class RootNode private constructor(
	decompilerRef: JadxDecompiler?,
	jadxArgs: JadxArgs,
) {
	constructor(decompiler: JadxDecompiler) : this(decompiler, decompiler.args)

	/**
	 * 已废弃：优先使用 [RootNode]（传入 JadxDecompiler）。
	 * 保留此构造器以兼容仅持有 JadxArgs 的调用方（例如测试代码）。
	 */
	@Deprecated("Prefer RootNode(JadxDecompiler)")
	constructor(args: JadxArgs) : this(null, args)

	companion object {
		private val LOG = LoggerFactory.getLogger(RootNode::class.java)
	}

	@get:JvmName("argsValue")
	val args: JadxArgs
	private val errorsCounter = ErrorsCounter()
	private val stringUtils: StringUtils

	@get:JvmName("constValuesValue")
	val constValues: ConstStorage
	private val infoStorage = InfoStorage()
	private val cacheStorage = CacheStorage()
	private val typeUpdate: TypeUpdate

	@get:JvmName("methodUtilsValue")
	val methodUtils: MethodUtils

	@get:JvmName("typeUtilsValue")
	val typeUtils: TypeUtils
	private val attributes = AttributeStorage()

	private val codeDataUpdateListeners = ArrayList<ICodeDataUpdateListener>()
	private val gradleInfoStorage = GradleInfoStorage()

	private val clsMap = HashMap<ClassInfo, ClassNode>()
	private val rawClsMap = HashMap<String, ClassNode>()

	@get:JvmName("classesValue")
	var classes: List<ClassNode> = ArrayList()

	private val pkgMap = HashMap<String, PackageNode>()

	// 显式 getter getPackages() 已存在，属性 getter 改名以避免 JVM 同名冲突
	@get:JvmName("packagesValue")
	val packages = ArrayList<PackageNode>()

	private var preDecompilePasses: MutableList<IDexTreeVisitor>

	@get:JvmName("processClassesValue")
	var processClasses: ProcessClass

	private var clsp: ClspGraph? = null

	@get:JvmName("appPackageValue")
	var appPackage: String? = null

	@get:JvmName("appResClassValue")
	var appResClass: ClassNode? = null

	private val decompiler: JadxDecompiler?
	private var manifestAttributes: ManifestAttributes? = null

	init {
		this.decompiler = decompilerRef
		args = jadxArgs
		preDecompilePasses = Jadx.getPreDecompilePassesList()
		processClasses = ProcessClass(Jadx.getPassesList(args))
		stringUtils = StringUtils(args)
		constValues = ConstStorage(args)
		typeUpdate = TypeUpdate(this)
		methodUtils = MethodUtils(this)
		this.typeUtils = TypeUtils(this)
	}

	fun init() {
		if (args.isDeobfuscationOn || args.renameFlags.isNotEmpty()) {
			args.aliasProvider.init(this)
		}
		if (args.isDeobfuscationOn) {
			args.renameCondition.init(this)
		}
	}

	fun loadClasses(loadedInputs: List<ICodeLoader>) {
		for (codeLoader in loadedInputs) {
			codeLoader.visitClasses { cls ->
				try {
					addClassNode(ClassNode(this, cls))
				} catch (e: Exception) {
					addDummyClass(cls, e)
				}
				checkThreadInterrupt()
			}
		}
	}

	fun finishClassLoad() {
		if (classes.size != clsMap.size) {
			fixDuplicatedClasses()
		}
		classes = ArrayList(clsMap.values)

		val mthCount = classes.sumOf { it.methods.size }
		val insnsCount = classes.flatMap { it.methods }.sumOf { it.insnsCount }
		LOG.info("Loaded classes: {}, methods: {}, instructions: {}", classes.size, mthCount, insnsCount)

		java.util.Collections.sort(classes, java.util.Comparator.comparing({ cn -> cn.rawName }))

		if (args.isMoveInnerClasses) {
			initInnerClasses()
		}
		packages.sort()
	}

	private fun addDummyClass(classData: IClassData, exc: Exception) {
		try {
			val typeStr = classData.getType()
			var name: String? = null
			try {
				val clsInfo = ClassInfo.fromName(this, typeStr)
				if (clsInfo != null) {
					name = clsInfo.shortName
				}
			} catch (e: Exception) {
				LOG.error("Failed to get name for class with type {}", typeStr, e)
			}
			if (name.isNullOrEmpty()) {
				name = "CLASS_$typeStr"
			}
			val clsNode = ClassNode.addSyntheticClass(this, name, classData.getAccessFlags())
			jadx.core.utils.ErrorsCounter.error(clsNode, "Load error", exc)
		} catch (innerExc: Exception) {
			LOG.error("Failed to load class from file: {}", classData.getInputFileName(), exc)
		}
	}

	private fun fixDuplicatedClasses() {
		val grouped = classes.groupBy({ cn -> cn.classInfo })
		for ((clsInfo, dupClsList) in grouped) {
			if (dupClsList.size <= 1) continue
			val selectedCls = checkNotNull(SelectFromDuplicates.process(dupClsList))
			clsMap[clsInfo] = selectedCls
			rawClsMap[selectedCls.rawName] = selectedCls

			val sourceList = ArrayList<String?>()
			for (cn in dupClsList) {
				sourceList.add(cn.getInputFileName())
			}
			java.util.Collections.sort(sourceList, null as java.util.Comparator<String?>?)
			val sources = sourceList.joinToString("\n  ")
			LOG.warn(
				"Found duplicated class: {}, count: {}, sources:\n  {}\n Keep class with source: {}, others will be removed.",
				clsInfo,
				dupClsList.size,
				sources,
				selectedCls.inputFileName,
			)
			selectedCls.addWarnComment("Classes with same name are omitted, all sources:\n  $sources\n")
		}
	}

	fun addClassNode(clsNode: ClassNode) {
		(classes as MutableList<ClassNode>).add(clsNode)
		clsMap[clsNode.classInfo] = clsNode
		rawClsMap[clsNode.rawName] = clsNode
	}

	fun loadResources(resLoader: ResourcesLoader, resources: List<ResourceFile>) {
		val arsc = getResourceFile(resources) ?: run {
			LOG.debug("'resources.arsc' or 'resources.pb' file not found")
			return
		}
		try {
			val parser = ResourcesLoader.decodeStream(arsc) { size, inputStream -> resLoader.decodeTable(arsc, inputStream) }
			if (parser != null) {
				processResources(parser.resStorage)
				updateObfuscatedFiles(parser, resources)
				initManifestAttributes().updateAttributes(parser)
			}
		} catch (e: Exception) {
			LOG.error("Failed to parse 'resources.pb'/'.arsc' file", e)
		}
	}

	private fun getResourceFile(resources: List<ResourceFile>): ResourceFile? {
		for (rf in resources) {
			if (rf.type == ResourceType.ARSC) return rf
		}
		return null
	}

	fun processResources(resStorage: ResourceStorage) {
		constValues.setResourcesNames(resStorage.resourcesNames)
		appPackage = resStorage.appPackage
		appResClass = searchAppResClass(this, resStorage)
	}

	fun initClassPath() {
		try {
			if (clsp == null) {
				val newClsp = ClspGraph(this)
				if (args.isLoadJadxClsSetFile) {
					newClsp.loadClsSetFile()
				}
				newClsp.addApp(classes)
				newClsp.initCache()
				clsp = newClsp
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Error loading jadx class set", e)
		}
	}

	private fun updateObfuscatedFiles(parser: IResTableParser, resources: List<ResourceFile>) {
		if (args.isSkipResources) return
		val useHeaders = args.isUseHeadersForDetectResourceExtensions
		val start = System.currentTimeMillis()
		var renamedCount = 0
		val resStorage = parser.resStorage
		val valuesParser = ValuesParser(parser.strings, resStorage.resourcesNames)
		val entryNames = HashMap<String, jadx.core.xmlgen.entry.ResourceEntry>()
		for (resEntry in resStorage.resources) {
			val valStr = valuesParser.getSimpleValueString(resEntry)
			if (valStr != null) {
				entryNames[valStr] = resEntry
			}
		}
		for (resource in resources) {
			val resEntry = entryNames[resource.originalName]
			if (resEntry != null && resource.setAlias(resEntry, useHeaders)) {
				renamedCount++
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("Renamed obfuscated resources: {}, duration: {}ms", renamedCount, System.currentTimeMillis() - start)
		}
	}

	private fun initInnerClasses() {
		val inner = ArrayList<ClassNode>()
		for (cls in classes) {
			if (cls.classInfo.isInner) {
				inner.add(cls)
			}
		}
		val updated = ArrayList<ClassNode>()
		for (cls in inner) {
			val clsInfo = cls.classInfo
			val parent = resolveParentClass(clsInfo)
			if (parent == null) {
				updated.add(cls)
				cls.notInner()
			} else {
				parent.addInnerClass(cls)
			}
		}
		for (updCls in updated) {
			for (innerCls in updCls.innerClasses) {
				innerCls.classInfo.updateNames(this)
			}
		}
		for (pkg in packages) {
			pkg.classes.removeIf { it.classInfo.isInner }
		}
	}

	fun mergePasses(customPasses: Map<JadxPassType, List<JadxPass>>) {
		val mode = args.decompilationMode
		if (mode == DecompilationMode.FALLBACK || mode == DecompilationMode.SIMPLE) return

		PassMerge(preDecompilePasses).merge(customPasses[JadxPreparePass.TYPE]) { p -> PreparePassWrapper(p as JadxPreparePass) }
		PassMerge(processClasses.passes).merge(customPasses[JadxDecompilePass.TYPE]) { p -> DecompilePassWrapper(p as JadxDecompilePass) }

		if (args.isRunDebugChecks) {
			preDecompilePasses = insertDebugPasses(preDecompilePasses)
			processClasses = ProcessClass(insertDebugPasses(processClasses.passes))
		}
		val disabledPasses = args.disabledPasses
		if (disabledPasses.isNotEmpty()) {
			val disabledSet = HashSet(disabledPasses)
			preDecompilePasses.removeIf { p ->
				if (disabledSet.contains(p.name)) {
					LOG.debug("Disable pass: {}", p.name)
					true
				} else {
					false
				}
			}
			processClasses.passes.removeIf { p ->
				if (disabledSet.contains(p.name)) {
					LOG.debug("Disable pass: {}", p.name)
					true
				} else {
					false
				}
			}
		}
	}

	fun runPreDecompileStage() {
		val debugEnabled = LOG.isDebugEnabled()
		for (pass in preDecompilePasses) {
			checkThreadInterrupt()
			val start = if (debugEnabled) System.currentTimeMillis() else 0
			try {
				pass.init(this)
			} catch (e: Exception) {
				LOG.error("Visitor init failed: {}", pass.javaClass.simpleName, e)
			}
			for (cls in classes) {
				if (cls.isInner()) continue
				depthVisit(pass, cls)
			}
			if (debugEnabled) {
				LOG.debug("Prepare pass: '{}' - {}ms", pass, System.currentTimeMillis() - start)
			}
		}
	}

	fun runPreDecompileStageForClass(cls: ClassNode) {
		for (pass in preDecompilePasses) {
			depthVisit(pass, cls)
		}
	}

	fun resetPasses() {
		preDecompilePasses.clear()
		preDecompilePasses.addAll(Jadx.getPreDecompilePassesList())
		processClasses.passes.clear()
		processClasses.passes.addAll(Jadx.getPassesList(args))
	}

	fun restartVisitors() {
		for (cls in classes) {
			cls.unload()
			cls.clearAttributes()
			cls.unloadFromCache()
		}
		runPreDecompileStage()
	}

	fun getClasses(): List<ClassNode> = classes

	fun getClassesWithoutInner(): List<ClassNode> = getClasses(false)

	fun getClasses(includeInner: Boolean): List<ClassNode> {
		if (includeInner) return classes
		val notInnerClasses = ArrayList<ClassNode>()
		for (cls in classes) {
			if (!cls.classInfo.isInner) {
				notInnerClasses.add(cls)
			}
		}
		return notInnerClasses
	}

	fun getPackages(): List<PackageNode> = packages

	fun resolvePackage(fullPkg: String): PackageNode? = pkgMap[fullPkg]

	fun resolvePackage(pkgInfo: PackageInfo?): PackageNode? {
		if (pkgInfo == null) return null
		return pkgMap[pkgInfo.fullName]
	}

	fun addPackage(pkg: PackageNode) {
		pkgMap[pkg.pkgInfo.fullName] = pkg
		packages.add(pkg)
	}

	fun removePackage(pkg: PackageNode) {
		if (pkgMap.remove(pkg.pkgInfo.fullName) != null) {
			packages.remove(pkg)
			val parentPkg = pkg.parentPkg
			if (parentPkg != null) {
				parentPkg.subPackages.remove(pkg)
				if (parentPkg.isEmpty()) {
					removePackage(parentPkg)
				}
			}
			for (subPkg in pkg.subPackages) {
				removePackage(subPkg)
			}
		}
	}

	fun sortPackages() {
		packages.sort()
	}

	fun removeClsFromPackage(pkg: PackageNode, cls: ClassNode) {
		if (pkg.classes.remove(cls) && pkg.isEmpty()) {
			removePackage(pkg)
		}
	}

	fun runPackagesUpdate() {
		for (pkg in packages) {
			if (pkg.isRoot()) {
				pkg.updatePackages()
			}
		}
	}

	fun resolveClass(clsInfo: ClassInfo): ClassNode? = clsMap[clsInfo]

	fun resolveClass(clsType: ArgType): ClassNode? {
		if (!clsType.isTypeKnown() || clsType.isGenericType()) return null
		if (clsType.getWildcardBound() == ArgType.WildcardBound.UNBOUND) return null
		var type = clsType
		if (type.isGeneric()) {
			type = ArgType.`object`(type.getObject())
		}
		return resolveClass(ClassInfo.fromType(this, type))
	}

	fun resolveClass(fullName: String): ClassNode? {
		val clsInfo = ClassInfo.fromName(this, fullName) ?: return null
		return resolveClass(clsInfo)
	}

	fun resolveRawClass(rawFullName: String): ClassNode? = rawClsMap[rawFullName]

	fun resolveParentClass(clsInfo: ClassInfo): ClassNode? {
		val parentInfo = clsInfo.parentClass
		var parentNode = resolveClass(parentInfo!!)
		if (parentNode == null) {
			val parClsName = parentInfo.fullName
			val sep = parClsName.lastIndexOf('.')
			if (sep > 0 && sep != parClsName.length - 1) {
				val mthName = parClsName.substring(sep + 1)
				val upperParClsName = parClsName.substring(0, sep)
				val tmpParent = resolveClass(upperParClsName)
				if (tmpParent != null && tmpParent.searchMethodByShortName(mthName) != null) {
					parentNode = tmpParent
					clsInfo.convertToInner(parentNode)
				}
			}
		}
		return parentNode
	}

	fun searchClassByFullAlias(fullName: String): ClassNode? {
		for (cls in classes) {
			val classInfo = cls.classInfo
			if (classInfo.fullName == fullName || classInfo.aliasFullName == fullName) {
				return cls
			}
		}
		return null
	}

	fun buildFullAliasClassCache(): Map<String, ClassNode> {
		val classNameCache = HashMap<String, ClassNode>(classes.size)
		for (cls in classes) {
			val classInfo = cls.classInfo
			val fullName = classInfo.fullName
			classNameCache[fullName] = cls
			val alias = classInfo.aliasFullName
			if (alias != null && fullName != alias) {
				classNameCache[alias] = cls
			}
		}
		return classNameCache
	}

	fun searchClassByShortName(shortName: String): List<ClassNode> {
		val list = ArrayList<ClassNode>()
		for (cls in classes) {
			if (cls.classInfo.shortName == shortName) {
				list.add(cls)
			}
		}
		return list
	}

	fun resolveMethod(mth: MethodInfo): MethodNode? {
		val cls = resolveClass(mth.declClass) ?: return null
		val methodNode = cls.searchMethod(mth)
		if (methodNode != null) return methodNode
		return deepResolveMethod(cls, mth.makeSignature(false))
	}

	fun resolveDirectMethod(rawClsName: String, mthShortId: String): MethodNode {
		val clsNode = resolveRawClass(rawClsName) ?: throw RuntimeException("Class not found: $rawClsName")
		val methodNode = clsNode.searchMethodByShortId(mthShortId)
			?: throw RuntimeException("Method not found: $rawClsName.$mthShortId")
		return methodNode
	}

	private fun deepResolveMethod(cls: ClassNode, signature: String): MethodNode? {
		for (m in cls.methods) {
			if (m.mthInfo.shortId.startsWith(signature)) return m
		}
		val superClass = cls.superClass
		if (superClass != null) {
			val superNode = resolveClass(superClass)
			if (superNode != null) {
				val found = deepResolveMethod(superNode, signature)
				if (found != null) return found
			}
		}
		for (iFaceType in cls.interfaces) {
			val iFaceNode = resolveClass(iFaceType)
			if (iFaceNode != null) {
				val found = deepResolveMethod(iFaceNode, signature)
				if (found != null) return found
			}
		}
		return null
	}

	fun resolveField(field: FieldInfo): FieldNode? {
		val cls = resolveClass(field.declClass) ?: return null
		val fieldNode = cls.searchField(field)
		if (fieldNode != null) return fieldNode
		return deepResolveField(cls, field)
	}

	private fun deepResolveField(cls: ClassNode, fieldInfo: FieldInfo): FieldNode? {
		val field = cls.searchFieldByNameAndType(fieldInfo)
		if (field != null) return field
		val superClass = cls.superClass
		if (superClass != null) {
			val superNode = resolveClass(superClass)
			if (superNode != null) {
				val found = deepResolveField(superNode, fieldInfo)
				if (found != null) return found
			}
		}
		for (iFaceType in cls.interfaces) {
			val iFaceNode = resolveClass(iFaceType)
			if (iFaceNode != null) {
				val found = deepResolveField(iFaceNode, fieldInfo)
				if (found != null) return found
			}
		}
		return null
	}

	fun getProcessClasses(): ProcessClass = processClasses

	fun getPasses(): List<IDexTreeVisitor> = processClasses.passes

	fun getPreDecompilePasses(): List<IDexTreeVisitor> = preDecompilePasses

	fun initPasses() {
		processClasses.initPasses(this)
	}

	fun makeCodeWriter(): ICodeWriter = args.getCodeWriterProvider().apply(args)

	fun registerCodeDataUpdateListener(listener: ICodeDataUpdateListener) {
		codeDataUpdateListeners.add(listener)
	}

	fun notifyCodeDataListeners() {
		val codeData = args.codeData
		for (l in codeDataUpdateListeners) {
			l.updated(codeData)
		}
	}

	fun getClsp(): ClspGraph? = clsp

	fun getErrorsCounter(): ErrorsCounter = errorsCounter

	fun getAppPackage(): String? = appPackage

	fun getAppResClass(): ClassNode? = appResClass

	fun getStringUtils(): StringUtils = stringUtils

	fun getConstValues(): ConstStorage = constValues

	fun getInfoStorage(): InfoStorage = infoStorage

	fun getCacheStorage(): CacheStorage = cacheStorage

	fun getArgs(): JadxArgs = args

	fun getDecompiler(): JadxDecompiler? = decompiler

	fun getTypeUpdate(): TypeUpdate = typeUpdate

	fun getTypeCompare(): TypeCompare = typeUpdate.typeCompare

	fun getCodeCache() = args.codeCache

	fun getMethodUtils(): MethodUtils = methodUtils

	fun getTypeUtils(): TypeUtils = typeUtils

	fun getAttributes(): AttributeStorage = attributes

	fun getGradleInfoStorage(): GradleInfoStorage = gradleInfoStorage

	@Synchronized
	fun initManifestAttributes(): ManifestAttributes {
		var attrs = manifestAttributes
		if (attrs == null) {
			attrs = ManifestAttributes(args.security)
			manifestAttributes = attrs
		}
		return attrs
	}
}
