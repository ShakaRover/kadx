package jadx.tests.api

import jadx.api.CommentsLevel
import jadx.api.DecompilationMode
import jadx.api.ICodeInfo
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JadxInternalAccess
import jadx.api.JavaClass
import jadx.api.JavaMethod
import jadx.api.JavaVariable
import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.ResourcesLoader
import jadx.api.args.GeneratedRenamesMappingFileMode
import jadx.api.data.IJavaNodeRef
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.api.impl.SimpleCodeInfo
import jadx.api.metadata.ICodeMetadata
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.api.metadata.annotations.VarNode
import jadx.api.plugins.CustomResourcesLoader
import jadx.api.plugins.resources.IResTableParserProvider
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.core.xmlgen.BinaryXMLStrings
import jadx.core.xmlgen.IResTableParser
import jadx.core.xmlgen.ResContainer
import jadx.core.xmlgen.ResourceStorage
import jadx.core.xmlgen.entry.ResourceEntry
import jadx.tests.api.compiler.CompilerOptions
import jadx.tests.api.compiler.JavaUtils
import jadx.tests.api.compiler.TestCompiler
import jadx.tests.api.utils.TestFilesGetter
import jadx.tests.api.utils.TestUtils
import org.apache.commons.lang3.StringUtils.leftPad
import org.apache.commons.lang3.StringUtils.rightPad
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.regex.Pattern

/**
 * 集成测试基类：约 600 个测试类继承它。
 *
 * **做什么**：负责把测试 Java 源码（或内嵌在 Kotlin fixture 的 `JAVA_SOURCE` 常量）
 * 编译成 class、交给 jadx 反编译、自动运行源码/反编译代码里的 `check()` 方法并断言结果。
 *
 * **为什么保留原有 public/protected 形态**：Java 子类（如 jadx-gui 的测试）与 ECJ
 * 编译的 Java fixture 都依赖这些成员的方法名、签名与可见性；可覆写成员（[init] 等）
 * 标注 `open`，受检异常用 `@Throws` 保留。
 */
abstract class IntegrationTest : TestUtils() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(IntegrationTest::class.java)

		private const val TEST_DIRECTORY = "src/test/java"
		private const val TEST_DIRECTORY2 = "jadx-core/$TEST_DIRECTORY"
		private const val DEFAULT_INPUT_PLUGIN = "dx"

		/**
		 * 设置 `TEST_INPUT_PLUGIN` 环境变量为 `java` 或 `dx` 可切换测试输入插件。
		 */
		@JvmField
		val USE_JAVA_INPUT: Boolean = Utils.getOrElse(System.getenv("TEST_INPUT_PLUGIN"), DEFAULT_INPUT_PLUGIN) == "java"

		/** 自动检查方法名：源码/反编译类里名为 `check` 的 public 无参实例方法会被执行。 */
		private const val CHECK_METHOD_NAME = "check"

		/** Kotlin fixture 里保存原始 Java 源码的静态字段名。 */
		private const val JAVA_SOURCE_FIELD_NAME = "JAVA_SOURCE"

		/** 匹配嵌入 Java 源码里的根类声明，用于生成临时 `.java` 文件名。 */
		private val ROOT_CLASS_PATTERN = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+(\\w+)")

		/** 把异常（可能被反射包装）重新抛出为更易读的异常。 */
		@JvmStatic
		fun rethrow(msg: String?, e: Throwable?) {
			if (e is InvocationTargetException) {
				rethrow(msg, e.cause)
			} else if (e is ExecutionException) {
				rethrow(msg, e.cause)
			} else if (e is AssertionError) {
				System.err.println(msg)
				throw e
			} else {
				throw RuntimeException(msg, e)
			}
		}
	}

	@JvmField
	var args: JadxArgs = JadxArgs()

	fun getArgs(): JadxArgs = args

	protected var compile: Boolean = false
	private lateinit var compilerOptions: CompilerOptions

	private var saveTestJar = false

	@JvmField
	protected var resMap: Map<Int, String> = emptyMap()

	fun setResMap(resMap: Map<Int, String>) {
		this.resMap = resMap
	}

	private var allowWarnInCode = false
	private var printLineNumbers = false
	private var printOffsets = false
	private var printDisassemble = false
	private var useJavaInput: Boolean? = null
	private var removeParentClassOnInput = true

	private var sourceCompiler: TestCompiler? = null
	private var decompiledCompiler: TestCompiler? = null

	/**
	 * 即使源码里没有 `check` 方法，也运行反编译代码里的 `check`（供 Smali 测试使用）。
	 */
	private var forceDecompiledCheck = false

	protected lateinit var jadxDecompiler: JadxDecompiler

	@field:TempDir
	lateinit var testDir: Path

	@BeforeEach
	open fun init() {
		this.compile = true
		this.compilerOptions = CompilerOptions()
		this.resMap = emptyMap()
		this.removeParentClassOnInput = true
		this.useJavaInput = null

		args = JadxArgs()
		args.outDir = testDir.toFile()
		args.isShowInconsistentCode = true
		args.threadsCount = 1
		args.isSkipResources = true
		args.commentsLevel = CommentsLevel.DEBUG
		args.isDeobfuscationOn = false
		args.generatedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.IGNORE
		args.isRunDebugChecks = true
		args.filesGetter = TestFilesGetter(testDir)

		// 所有系统使用相同的配置，保证测试输出稳定
		args.isFsCaseSensitive = false
		args.codeNewLineStr = "\n"
		args.codeIndentStr = JadxArgs.DEFAULT_INDENT_STR
	}

	@AfterEach
	@Throws(IOException::class)
	open fun after() {
		if (this::jadxDecompiler.isInitialized) {
			close(jadxDecompiler)
		}
		close(sourceCompiler)
		close(decompiledCompiler)
	}

	@Throws(IOException::class)
	private fun close(closeable: Closeable?) {
		if (closeable != null) {
			closeable.close()
		}
	}

	fun setOutDirSuffix(suffix: String) {
		args.outDir = File(testDir.toFile(), suffix)
	}

	fun getTestName(): String = this.javaClass.simpleName

	fun getTestPkg(): String = this.javaClass.getPackage().name.replace("jadx.tests.integration.", "")

	fun getClassNode(clazz: Class<*>): ClassNode {
		try {
			val files = compileClass(clazz)
			assertThat(files).`as`("File list is empty").isNotEmpty()
			return getClassNodeFromFiles(files, clazz.name)
		} catch (e: Exception) {
			LOG.error("Failed to get class node", e)
			return fail<Nothing>(e.message)
		}
	}

	fun getClassNodes(vararg classes: Class<*>): List<ClassNode> {
		try {
			assertThat(classes).`as`("Class list is empty").isNotEmpty()
			val srcFiles = ArrayList<File>()
			for (cls in classes) {
				val embeddedSource = findEmbeddedJavaSource(cls)
				srcFiles.add(if (embeddedSource != null) writeJavaSource(embeddedSource) else getSourceFileForClass(cls))
			}
			val clsFiles = compileSourceFiles(srcFiles)
			assertThat(clsFiles).`as`("Class files list is empty").isNotEmpty()
			return decompileFiles(clsFiles)
		} catch (e: Exception) {
			LOG.error("Failed to get class node", e)
			return fail<Nothing>(e.message)
		}
	}

	fun getClassNodeFromFiles(files: List<File>, clsName: String): ClassNode {
		jadxDecompiler = loadFiles(files)
		val root = JadxInternalAccess.getRoot(jadxDecompiler)

		val cls = root.resolveClass(clsName)
		assertThat(cls).`as`("Class not found: $clsName").isNotNull()
		val clsNode = checkNotNull(cls)
		if (removeParentClassOnInput) {
			assertThat(clsName).isEqualTo(clsNode.classInfo.fullName)
		} else {
			LOG.info("Convert back to top level: {}", clsNode)
			clsNode.getTopParentClass().decompile() // 保持正确的处理顺序
			clsNode.notInner()
		}
		decompileAndCheck(clsNode)
		return clsNode
	}

	fun decompileFiles(files: List<File>): List<ClassNode> {
		jadxDecompiler = loadFiles(files)
		val sortedClsNodes = ArrayList<ClassNode>()
		for (batch in jadxDecompiler.getDecompileScheduler().buildBatches(jadxDecompiler.getClasses())) {
			for (javaClass in batch) {
				sortedClsNodes.add(javaClass.getClassNode())
			}
		}
		decompileAndCheck(sortedClsNodes)
		return sortedClsNodes
	}

	fun searchTestCls(list: List<ClassNode>, shortClsName: String): ClassNode = searchCls(list, getTestPkg() + '.' + shortClsName)

	fun searchCls(list: List<ClassNode>, clsName: String): ClassNode {
		for (cls in list) {
			if (cls.classInfo.fullName == clsName) {
				return cls
			}
		}
		for (cls in list) {
			if (cls.classInfo.shortName == clsName) {
				return cls
			}
		}
		return fail<Nothing>("Class not found by name $clsName in list: $list")
	}

	protected fun loadFiles(inputFiles: List<File>): JadxDecompiler {
		args.inputFiles = ArrayList(inputFiles)
		val useDx = !isJavaInput()
		LOG.info(if (useDx) "Using dex input" else "Using java input, current java version: " + JavaUtils.JAVA_VERSION_INT)
		args.isUseDxInput = useDx

		val d = JadxDecompiler(args)
		try {
			insertResources(d)
			d.load()
			return d
		} catch (e: Exception) {
			LOG.error("Load failed", e)
			d.close()
			return fail<Nothing>(e.message)
		}
	}

	protected fun decompileAndCheck(cls: ClassNode) {
		decompileAndCheck(listOf(cls))
	}

	protected fun decompileAndCheck(clsList: List<ClassNode>) {
		clsList.forEach { it.add(AFlag.DONT_UNLOAD_CLASS) } // 保留错误与警告属性
		clsList.forEach { it.decompile() }

		for (cls in clsList) {
			println("-----------------------------------------------------------")
			val code = cls.getCode()
			if (printLineNumbers) {
				printCodeWithLineNumbers(code)
			} else if (printOffsets) {
				printCodeWithOffsets(code)
			} else {
				println(code)
			}
		}
		println("-----------------------------------------------------------")
		if (printDisassemble) {
			clsList.forEach { printDisasm(it) }
		}
		runChecks(clsList)
	}

	fun runChecks(cls: ClassNode) {
		runChecks(listOf(cls))
	}

	protected fun runChecks(clsList: List<ClassNode>) {
		clsList.forEach { TestUtils.checkCode(it, allowWarnInCode) }
		compileClassNode(clsList)
		clsList.forEach { runAutoCheck(it) }
	}

	private fun printDisasm(cls: ClassNode) {
		println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
		println(cls.getDisassembledCode())
		println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
	}

	private fun printCodeWithLineNumbers(code: ICodeInfo) {
		val codeStr = code.getCodeStr()
		val lineMapping = code.getCodeMetadata().getLineMapping()
		val lines = codeStr.split(Regex("\\R"))
		for (i in lines.indices) {
			val line = lines[i]
			val curLine = i + 1
			var lineNumStr = "/* " + leftPad(curLine.toString(), 3) + " */"
			val sourceLine = lineMapping[curLine]
			if (sourceLine != null) {
				lineNumStr += " /* " + sourceLine + " */"
			}
			println(rightPad(lineNumStr, 20) + line)
		}
	}

	private fun printCodeWithOffsets(code: ICodeInfo) {
		val codeStr = code.getCodeStr()
		val metadata: ICodeMetadata = code.getCodeMetadata()
		var lineStartPos = 0
		val newLineStr = args.codeNewLineStr
		val newLineLen = newLineStr.length
		for (line in codeStr.split(newLineStr)) {
			val ann = metadata.getAt(lineStartPos)
			var offsetStr = ""
			if (ann is InsnCodeOffset) {
				val offset = ann.getOffset()
				offsetStr = "/* " + leftPad(offset.toString(), 5) + " */"
			}
			println(rightPad(offsetStr, 12) + line)
			lineStartPos += line.length + newLineLen
		}
	}

	/**
	 * 插入模拟的资源表数据（`.arsc` 文件）。
	 */
	@Throws(IOException::class)
	private fun insertResources(decompiler: JadxDecompiler) {
		if (resMap.isEmpty()) {
			return
		}
		val resTableName = "test-res-table"
		val resTablePath = testDir.resolve(resTableName)
		val resTableFile = resTablePath.toFile().absoluteFile
		FileUtils.makeDirsForFile(resTableFile)
		Files.writeString(resTablePath, resTableName)
		val jadxArgs = decompiler.getArgs()
		jadxArgs.inputFiles.add(resTableFile)

		// 把模拟文件当作 'arsc' 加载
		decompiler.addCustomResourcesLoader(object : CustomResourcesLoader {
			override fun load(loader: ResourcesLoader, list: MutableList<ResourceFile>, file: File): Boolean {
				if (file == resTableFile) {
					list.add(ResourceFile.createResourceFile(decompiler, resTableFile, ResourceType.ARSC))
					return true
				}
				return false
			}

			override fun close() {
			}
		})

		// 把资源映射转换为资源存储对象
		val resStorage = ResourceStorage(jadxArgs.security)
		for ((id, name) in resMap) {
			val parts = name.split(Regex("\\."))
			resStorage.add(ResourceEntry(id, "", parts[0], parts[1], ""))
		}

		// 模拟资源表解析器：直接返回构造好的资源存储
		val resTableParser = object : IResTableParser {
			override fun decode(inputStream: InputStream) {
			}

			override fun getResStorage(): ResourceStorage = resStorage

			override fun decodeFiles(): ResContainer = ResContainer.textResource(resTableName, SimpleCodeInfo(resTableName))

			override fun getStrings(): BinaryXMLStrings = BinaryXMLStrings()
		}

		decompiler.getResourcesLoader().addResTableParserProvider(object : IResTableParserProvider {
			override fun getParser(resFile: ResourceFile): IResTableParser? = if (resFile.getOriginalName() == resTableFile.absolutePath) {
				resTableParser
			} else {
				null
			}
		})
	}

	private fun runAutoCheck(cls: ClassNode) {
		val clsName = cls.classInfo.rawName.replace('/', '.')
		try {
			// 先运行原类里的 'check' 方法
			val sourceCheckFound = runSourceAutoCheck(clsName)

			// 再运行反编译类里的 'check' 方法
			if (compile && (sourceCheckFound || forceDecompiledCheck)) {
				runDecompiledAutoCheck(cls)
			}
		} catch (e: Exception) {
			LOG.error("Auto check failed", e)
			fail<Nothing>("Auto check exception: " + e.message)
		}
	}

	private fun runSourceAutoCheck(clsName: String): Boolean {
		val compiler = sourceCompiler
		if (compiler == null) {
			println("Source check: no code")
			return false
		}
		val origCls: Class<*>
		try {
			origCls = compiler.getClass(clsName)
		} catch (e: ClassNotFoundException) {
			rethrow("Missing class: $clsName", e)
			return false
		}
		val checkMth: Method
		try {
			checkMth = compiler.getMethod(origCls, CHECK_METHOD_NAME, arrayOf<Class<*>>())
		} catch (e: NoSuchMethodException) {
			// 忽略：没有 check 方法
			return false
		}
		if (checkMth.returnType != Void.TYPE ||
			!Modifier.isPublic(checkMth.modifiers) ||
			Modifier.isStatic(checkMth.modifiers)
		) {
			fail<Nothing>("Wrong 'check' method")
			return false
		}
		try {
			limitExecTime { checkMth.invoke(origCls.getConstructor().newInstance()) }
			println("Source check: PASSED")
			return true
		} catch (e: Throwable) {
			throw JadxRuntimeException("Source check failed", e)
		}
	}

	fun runDecompiledAutoCheck(cls: ClassNode) {
		try {
			limitExecTime { invoke(checkNotNull(decompiledCompiler), cls.fullName, CHECK_METHOD_NAME) }
			println("Decompiled check: PASSED")
		} catch (e: Throwable) {
			rethrow("Decompiled check failed", e)
		}
	}

	private fun <T> limitExecTime(call: Callable<T>): T? {
		val executor = Executors.newSingleThreadExecutor()
		val future = executor.submit(call)
		try {
			return future.get(5, TimeUnit.SECONDS)
		} catch (ex: TimeoutException) {
			future.cancel(true)
			rethrow("Execution timeout", ex)
		} catch (ex: Throwable) {
			rethrow(ex.message, ex)
		} finally {
			executor.shutdownNow()
		}
		return null
	}

	protected fun getMethod(cls: ClassNode, methodName: String): MethodNode {
		for (mth in cls.methods) {
			if (mth.getName() == methodName) {
				return mth
			}
		}
		return fail<Nothing>("Method not found $methodName in class $cls")
	}

	protected fun getField(cls: ClassNode, fieldName: String): FieldNode {
		for (fld in cls.fields) {
			if (fld.getName() == fieldName) {
				return fld
			}
		}
		return fail<Nothing>("Field not found $fieldName in class $cls")
	}

	fun compileClassNode(clsList: List<ClassNode>) {
		if (!compile) {
			return
		}
		try {
			// TODO: eclipse 使用 Java 9 添加的文件/编译单元 provider
			compilerOptions.isUseEclipseCompiler = false
			val compiler = TestCompiler(compilerOptions)
			decompiledCompiler = compiler
			compiler.compileNodes(clsList)
			println("Compilation: PASSED")
		} catch (e: Exception) {
			fail<Nothing>(e)
		}
	}

	@Throws(Exception::class)
	fun invoke(compiler: TestCompiler, clsFullName: String, method: String): Any? {
		assertThat(compiler).`as`("compiler not ready").isNotNull()
		return compiler.invoke(clsFullName, method, arrayOf<Class<*>>(), arrayOfNulls<Any>(0))
	}

	@Throws(IOException::class)
	private fun compileClass(cls: Class<*>): MutableList<File> {
		val embeddedSource = findEmbeddedJavaSource(cls)
		val clsFiles: MutableList<File>
		if (embeddedSource != null) {
			clsFiles = compileJavaSource(embeddedSource)
		} else {
			val sourceFile = getSourceFileForClass(cls)
			clsFiles = compileSourceFiles(listOf(sourceFile))
		}
		if (removeParentClassOnInput) {
			// 移除作为测试类父级的类
			val clsFullName = cls.name
			val clsName = clsFullName.substring(clsFullName.lastIndexOf('.') + 1)
			clsFiles.removeIf { !it.name.contains(clsName) }
		}
		return clsFiles
	}

	private fun getSourceFileForClass(cls: Class<*>): File {
		val clsFullName = cls.name
		val innerEnd = clsFullName.indexOf('$')
		val rootClsName = if (innerEnd == -1) clsFullName else clsFullName.substring(0, innerEnd)
		val javaFileName = rootClsName.replace('.', '/') + ".java"
		val file = File(TEST_DIRECTORY, javaFileName)
		if (file.exists()) {
			return file
		}
		val file2 = File(TEST_DIRECTORY2, javaFileName)
		if (file2.exists()) {
			return file2
		}
		throw JadxRuntimeException("Test source not found for class: $clsFullName")
	}

	/**
	 * 在类自身或其外层类中查找内嵌的 Java 源码。
	 *
	 * 已转换为 Kotlin 的 fixture 把原始 Java 源码保存在静态 `JAVA_SOURCE` 字段里，
	 * 这样测试 driver 可以保持不变。
	 */
	private fun findEmbeddedJavaSource(cls: Class<*>): String? {
		var c: Class<*>? = cls
		while (c != null) {
			val current: Class<*> = c
			val field: Field? = try {
				current.getDeclaredField(JAVA_SOURCE_FIELD_NAME)
			} catch (e: NoSuchFieldException) {
				null
			}
			if (field != null && field.type == String::class.java && Modifier.isStatic(field.modifiers)) {
				try {
					field.isAccessible = true
					return field.get(null) as String?
				} catch (e: IllegalAccessException) {
					throw JadxRuntimeException("Failed to access JAVA_SOURCE field in " + current.name, e)
				}
			}
			c = current.enclosingClass
		}
		return null
	}

	@Throws(IOException::class)
	private fun compileJavaSource(source: String): MutableList<File> = compileSourceFiles(listOf(writeJavaSource(source)))

	@Throws(IOException::class)
	private fun writeJavaSource(source: String): File {
		val rootClsName = parseRootClassName(source)
		val srcDir = Files.createTempDirectory(testDir, "jadx-tmp-src")
		val sourceFile = srcDir.resolve(rootClsName + ".java")
		Files.writeString(sourceFile, source)
		return sourceFile.toFile()
	}

	private fun parseRootClassName(source: String): String {
		val matcher = ROOT_CLASS_PATTERN.matcher(source)
		if (!matcher.find()) {
			throw JadxRuntimeException("Failed to find root class name in embedded Java source")
		}
		return matcher.group(1)
	}

	@Throws(IOException::class)
	private fun compileSourceFiles(compileFileList: List<File>): MutableList<File> {
		val outTmp = Files.createTempDirectory(testDir, "jadx-tmp-classes")
		val compiler = TestCompiler(compilerOptions)
		sourceCompiler = compiler
		val files = ArrayList(compiler.compileFiles(compileFileList, outTmp))
		if (saveTestJar) {
			saveToJar(files, outTmp)
		}
		return files
	}

	@Throws(IOException::class)
	private fun saveToJar(files: List<File>, baseDir: Path) {
		val jarFile = Files.createTempFile("tests-" + getTestName() + '-', ".jar")
		JarOutputStream(Files.newOutputStream(jarFile)).use { jar ->
			for (file in files) {
				val fullPath = file.toPath()
				val relativePath = baseDir.relativize(fullPath)
				val entry = JarEntry(relativePath.toString())
				jar.putNextEntry(entry)
				jar.write(Files.readAllBytes(fullPath))
				jar.closeEntry()
			}
		}
		LOG.info("Test jar saved to: {}", jarFile.toAbsolutePath())
	}

	fun getCompilerOptions(): CompilerOptions = compilerOptions

	protected fun noDebugInfo() {
		this.compilerOptions.isIncludeDebugInfo = false
	}

	fun useEclipseCompiler() {
		Assumptions.assumeTrue(JavaUtils.checkJavaVersion(11), "eclipse compiler library using Java 11")
		this.compilerOptions.isUseEclipseCompiler = true
	}

	fun useTargetJavaVersion(version: Int) {
		Assumptions.assumeTrue(JavaUtils.checkJavaVersion(version), "skip test for higher java version")
		this.compilerOptions.javaVersion = version
	}

	protected fun setFallback() {
		disableCompilation()
		this.args.decompilationMode = DecompilationMode.FALLBACK
	}

	protected fun disableCompilation() {
		this.compile = false
	}

	protected fun forceDecompiledCheck() {
		this.forceDecompiledCheck = true
	}

	protected fun enableDeobfuscation() {
		args.isDeobfuscationOn = true
		args.generatedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.IGNORE
		args.deobfuscationMinLength = 2
		args.deobfuscationMaxLength = 64
	}

	protected fun allowWarnInCode() {
		allowWarnInCode = true
	}

	protected fun printLineNumbers() {
		printLineNumbers = true
	}

	protected fun printOffsets() {
		printOffsets = true
	}

	fun useJavaInput() {
		this.useJavaInput = true
	}

	fun useDexInput() {
		Assumptions.assumeFalse(USE_JAVA_INPUT, "skip dex input tests")
		this.useJavaInput = false
	}

	fun useDexInput(mode: String) {
		useDexInput()
		@Suppress("UNCHECKED_CAST")
		val options = getArgs().pluginOptions as MutableMap<String, String>
		options["java-convert.mode"] = mode
	}

	protected fun isJavaInput(): Boolean = Utils.getOrElse(useJavaInput, USE_JAVA_INPUT)

	fun keepParentClassOnInput() {
		this.removeParentClassOnInput = false
	}

	// 仅用于调试
	protected fun printDisassemble() {
		this.printDisassemble = true
	}

	// 仅用于调试
	protected fun saveTestJar() {
		this.saveTestJar = true
	}

	protected fun addClsRename(fullClsName: String, newName: String) {
		val clsRef = JadxNodeRef.forCls(fullClsName)
		addRename(JadxCodeRename(clsRef, newName))
	}

	protected fun addMthRename(fullClsName: String, mthSignature: String, newName: String) {
		val mthRef = JadxNodeRef(IJavaNodeRef.RefType.METHOD, fullClsName, mthSignature)
		addRename(JadxCodeRename(mthRef, newName))
	}

	protected fun addFldRename(fullClsName: String, fldSignature: String, newName: String) {
		val fldRef = JadxNodeRef(IJavaNodeRef.RefType.FIELD, fullClsName, fldSignature)
		addRename(JadxCodeRename(fldRef, newName))
	}

	private fun addRename(rename: JadxCodeRename) {
		val codeData = getCodeData()
		val renames = ArrayList(codeData.getRenames())
		renames.add(rename)
		codeData.setRenames(renames)
	}

	protected fun getCodeData(): JadxCodeData {
		var codeData = getArgs().codeData as JadxCodeData?
		if (codeData == null) {
			codeData = JadxCodeData()
			codeData.setRenames(ArrayList())
			codeData.setComments(ArrayList())
			getArgs().codeData = codeData
		}
		return codeData
	}

	protected fun toJavaClass(cls: ClassNode): JavaClass {
		val javaClass = JadxInternalAccess.convertClassNode(jadxDecompiler, cls)
		assertThat(javaClass).isNotNull()
		return javaClass
	}

	protected fun toJavaMethod(mth: MethodNode): JavaMethod {
		val javaMethod = JadxInternalAccess.convertMethodNode(jadxDecompiler, mth)
		assertThat(javaMethod).isNotNull()
		return javaMethod
	}

	protected fun toJavaVariable(varNode: VarNode): JavaVariable {
		val javaVariable = jadxDecompiler.getJavaNodeByCodeAnnotation(null, varNode) as JavaVariable
		assertThat(javaVariable).isNotNull()
		return javaVariable
	}

	fun getResourceFile(filePath: String): File {
		val resource = javaClass.classLoader.getResource(filePath)
		assertThat(resource).`as`("Resource not found: %s", filePath).isNotNull()
		val resPath = checkNotNull(resource).file
		return File(resPath)
	}
}
