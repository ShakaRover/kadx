package jadx.tests.api.compiler

import jadx.core.dex.nodes.ClassNode
import jadx.core.utils.files.FileUtils
import jadx.tests.api.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.tools.DiagnosticListener
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/**
 * 测试用 Java 编译器封装。
 *
 * **做什么**：编译源码文件（[compileFiles]）或反编译得到的代码（[compileNodes]），
 * 并把结果加载进内存类加载器供反射调用（[invoke] / [getMethod]）。
 *
 * **Java 兼容性**（被 `IntegrationTest.java` 直接使用）：
 * - 构造器 `TestCompiler(CompilerOptions)` 保持不变；
 * - [compileFiles] / [compileNodes] / [invoke] / [getClass] / [getMethod] 签名不变；
 * - 受检异常用 `@Throws` 保留（`IOException` / `ClassNotFoundException` / `NoSuchMethodException`）。
 */
class TestCompiler(private val options: CompilerOptions) : Closeable {

	private val compiler: JavaCompiler
	private val fileManager: ClassFileManager

	init {
		val javaVersion = options.javaVersion
		if (!JavaUtils.checkJavaVersion(javaVersion)) {
			throw IllegalArgumentException(
				"Current java version not meet requirement: " +
					"current: " + JavaUtils.JAVA_VERSION_INT + ", required: " + javaVersion,
			)
		}
		compiler = if (options.isUseEclipseCompiler) {
			EclipseCompilerUtils.newInstance()
		} else {
			ToolProvider.getSystemJavaCompiler()
				?: throw IllegalStateException("Can not find compiler, please use JDK instead")
		}
		val diagnosticListener = DiagnosticListener<JavaFileObject> { diagnostic ->
			System.out.println(diagnostic)
		}
		fileManager = ClassFileManager(
			compiler.getStandardFileManager(diagnosticListener, Locale.ENGLISH, StandardCharsets.UTF_8),
		)
	}

	@Throws(IOException::class)
	fun compileFiles(sourceFiles: List<File>, outTmp: Path): List<File> {
		compile(fileManager.getJavaFileObjectsFromFiles(sourceFiles))
		val files = ArrayList<File>()
		for (classObject in fileManager.getClassLoader().getClassObjects()) {
			val path = outTmp.resolve(classObject.getName().replace('.', '/') + ".class")
			FileUtils.makeDirsForFile(path)
			Files.write(path, classObject.getBytes())
			files.add(path.toFile())
		}
		return files
	}

	fun compileNodes(clsNodeList: List<ClassNode>) {
		val jfObjects = ArrayList<JavaFileObject>(clsNodeList.size)
		for (clsNode in clsNodeList) {
			jfObjects.add(StringJavaFileObject(clsNode.fullName, clsNode.getCode().getCodeStr()))
		}
		compile(jfObjects)
	}

	private fun compile(jfObjects: List<JavaFileObject>) {
		val arguments = ArrayList<String>()
		arguments.add(if (options.isIncludeDebugInfo) "-g" else "-g:none")
		val javaVersion = options.javaVersion
		if (javaVersion != 0) {
			val javaVerStr = if (javaVersion <= 8) "1." + javaVersion else javaVersion.toString()
			arguments.add("-source")
			arguments.add(javaVerStr)
			arguments.add("-target")
			arguments.add(javaVerStr)
		}
		arguments.addAll(options.getArguments())

		val sb = StringBuilder()
		val diagnostic = DiagnosticListener<JavaFileObject> { diagObj ->
			val msg = "Compiler diagnostic: $diagObj"
			sb.append('\n').append(msg)
			System.out.println(msg)
		}
		val out: Writer = PrintWriter(System.out)
		val compilerTask = compiler.getTask(out, fileManager, diagnostic, arguments, null, jfObjects)
		if (compilerTask.call() == false) {
			throw RuntimeException("Compilation failed: $sb")
		}
	}

	private fun getClassLoader(): ClassLoader = fileManager.getClassLoader()

	@Throws(ClassNotFoundException::class)
	fun getClass(clsFullName: String): Class<*> = getClassLoader().loadClass(clsFullName)

	@Throws(NoSuchMethodException::class)
	fun getMethod(cls: Class<*>, methodName: String, types: Array<Class<*>>): Method = cls.getMethod(methodName, *types)

	fun invoke(clsFullName: String, methodName: String, types: Array<Class<*>>, args: Array<Any?>): Any? {
		try {
			for (type in types) {
				checkType(type)
			}
			val cls = getClass(clsFullName)
			val mth = getMethod(cls, methodName, types)
			val inst = cls.getConstructor().newInstance()
			assertThat(mth).`as`("Failed to get method " + methodName + '(' + types.contentToString() + ')').isNotNull()
			return mth.invoke(inst, *args)
		} catch (e: Throwable) {
			IntegrationTest.rethrow("Invoke error for method: " + methodName, e)
			return null
		}
	}

	@Throws(ClassNotFoundException::class)
	private fun checkType(type: Class<*>): Class<*> {
		if (type.isPrimitive) {
			return type
		}
		if (type.isArray) {
			return checkType(type.componentType)
		}
		val cls = getClassLoader().loadClass(type.name)
		if (type !== cls) {
			throw IllegalArgumentException("Internal test class cannot be used in method invoke")
		}
		return cls
	}

	@Throws(IOException::class)
	override fun close() {
		fileManager.close()
	}
}
