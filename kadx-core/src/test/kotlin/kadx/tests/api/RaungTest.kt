package kadx.tests.api

import kadx.api.KadxInternalAccess
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * raung 输入测试基类：负责定位并加载 `src/test/raung` 下的 `.raung` 文件。
 */
abstract class RaungTest : IntegrationTest() {

	companion object {
		private const val RAUNG_TESTS_PROJECT = "kadx-core"
		private const val RAUNG_TESTS_DIR = "src/test/raung"
		private const val RAUNG_TESTS_EXT = ".raung"

		private fun getRaungFile(baseName: String): File {
			val raungFile = File(RAUNG_TESTS_DIR, baseName + RAUNG_TESTS_EXT)
			if (raungFile.exists()) {
				return raungFile
			}
			val pathFromRoot = File(RAUNG_TESTS_PROJECT, raungFile.path)
			if (pathFromRoot.exists()) {
				return pathFromRoot
			}
			throw AssertionError("Raung file not found: " + raungFile.path)
		}

		private fun getRaungDir(baseName: String): File {
			val raungDir = File(RAUNG_TESTS_DIR, baseName)
			if (raungDir.exists()) {
				return raungDir
			}
			val pathFromRoot = File(RAUNG_TESTS_PROJECT, raungDir.path)
			if (pathFromRoot.exists()) {
				return pathFromRoot
			}
			throw AssertionError("Raung dir not found: " + raungDir.path)
		}
	}

	@BeforeEach
	override fun init() {
		super.init()
		this.useJavaInput()
	}

	/**
	 * 单文件 raung 测试的推荐入口。
	 */
	protected fun getClassNodeFromRaung(): ClassNode {
		val pkg = getTestPkg()
		val clsName = getTestName()
		return getClassNodeFromRaung(pkg + File.separatorChar + clsName, pkg + '.' + clsName)
	}

	protected fun getClassNodeFromRaung(file: String, clsName: String): ClassNode {
		val raungFile = getRaungFile(file)
		return getClassNodeFromFiles(listOf(raungFile), clsName)
	}

	protected fun loadFromRaungFiles(): List<ClassNode> {
		kadxDecompiler = loadFiles(collectRaungFiles(getTestPkg(), getTestName()))
		val root = KadxInternalAccess.getRoot(kadxDecompiler)
		val classes = root.getClasses(false)
		decompileAndCheck(classes)
		return classes
	}

	private fun collectRaungFiles(pkg: String, testDir: String): List<File> {
		val raungFilesDir = pkg + File.separatorChar + testDir + File.separatorChar
		val raungDir = getRaungDir(raungFilesDir)
		val raungFileNames = raungDir.list { _, name -> name.endsWith(".raung") }
		assertThat(raungFileNames).`as`("Raung files not found in $raungDir").isNotNull()
		return Stream.of(*checkNotNull(raungFileNames))
			.map { file -> File(raungDir, file) }
			.collect(Collectors.toList())
	}
}
