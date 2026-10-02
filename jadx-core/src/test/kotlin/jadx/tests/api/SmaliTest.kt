package jadx.tests.api

import jadx.api.JadxInternalAccess
import jadx.core.dex.nodes.ClassNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * smali 输入测试基类：负责定位并加载 `src/test/smali` 下的 `.smali` 文件。
 *
 * 保持与 Java 版本完全一致的 public/protected 方法签名，供 Java/Kotlin 子类使用。
 */
abstract class SmaliTest : IntegrationTest() {

	companion object {
		private const val SMALI_TESTS_DIR = "src/test/smali"
		private const val SMALI_TESTS_EXT = ".smali"
	}

	private var currentProject = "jadx-core"

	fun setCurrentProject(currentProject: String) {
		this.currentProject = currentProject
	}

	@BeforeEach
	override fun init() {
		Assumptions.assumeFalse(IntegrationTest.USE_JAVA_INPUT, "skip smali test for java input tests")
		super.init()
		this.useDexInput()
	}

	protected fun getClassNodeFromSmali(file: String, clsName: String): ClassNode {
		val smaliFile = getSmaliFile(file)
		return getClassNodeFromFiles(listOf(smaliFile), clsName)
	}

	/**
	 * 单文件 smali 测试的推荐入口。
	 */
	protected fun getClassNodeFromSmali(): ClassNode = getClassNodeFromSmaliWithPkg(getTestPkg(), getTestName())

	protected fun getClassNodeFromSmaliWithClsName(fullClsName: String): ClassNode = getClassNodeFromSmali(getTestPkg() + File.separatorChar + getTestName(), fullClsName)

	protected fun getClassNodeFromSmaliWithPath(path: String, clsName: String): ClassNode = getClassNodeFromSmali(path + File.separatorChar + clsName, clsName)

	protected fun getClassNodeFromSmaliWithPkg(pkg: String, clsName: String): ClassNode = getClassNodeFromSmali(pkg + File.separatorChar + clsName, pkg + '.' + clsName)

	protected fun getClassNodeFromSmaliFiles(pkg: String, testName: String, clsName: String): ClassNode = getClassNodeFromFiles(collectSmaliFiles(pkg, testName), pkg + '.' + clsName)

	protected fun getClassNodeFromSmaliFiles(clsName: String): ClassNode = searchCls(loadFromSmaliFiles(), getTestPkg() + '.' + clsName)

	protected fun getClassNodeFromSmaliFiles(): ClassNode = searchCls(loadFromSmaliFiles(), getTestPkg() + '.' + getTestName())

	protected fun loadFromSmaliFiles(): List<ClassNode> {
		jadxDecompiler = loadFiles(collectSmaliFiles(getTestPkg(), getTestName()))
		val root = JadxInternalAccess.getRoot(jadxDecompiler)
		val classes = root.getClasses(false)
		decompileAndCheck(classes)
		return classes
	}

	private fun collectSmaliFiles(pkg: String, testDir: String?): List<File> {
		val smaliFilesDir = if (testDir == null) {
			pkg + File.separatorChar
		} else {
			pkg + File.separatorChar + testDir + File.separatorChar
		}
		val smaliDir = getSmaliDir(smaliFilesDir)
		val smaliFileNames = smaliDir.list { _, name -> name.endsWith(".smali") }
		assertThat(smaliFileNames).`as`("Smali files not found in $smaliDir").isNotNull()
		return Stream.of(*checkNotNull(smaliFileNames))
			.map { file -> File(smaliDir, file) }
			.collect(Collectors.toList())
	}

	private fun getSmaliFile(baseName: String): File {
		val smaliFile = File(SMALI_TESTS_DIR, baseName + SMALI_TESTS_EXT)
		if (smaliFile.exists()) {
			return smaliFile
		}
		val pathFromRoot = File(currentProject, smaliFile.path)
		if (pathFromRoot.exists()) {
			return pathFromRoot
		}
		throw AssertionError("Smali file not found: " + smaliFile.path)
	}

	private fun getSmaliDir(baseName: String): File {
		val smaliDir = File(SMALI_TESTS_DIR, baseName)
		if (smaliDir.exists()) {
			return smaliDir
		}
		val pathFromRoot = File(currentProject, smaliDir.path)
		if (pathFromRoot.exists()) {
			return pathFromRoot
		}
		throw AssertionError("Smali dir not found: " + smaliDir.path)
	}
}
