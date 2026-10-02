package jadx.api

import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import jadx.plugins.input.dex.DexInputPlugin
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class JadxDecompilerTest {

	@field:TempDir
	private lateinit var testDir: File

	@Test
	fun testExampleUsage() {
		val sampleApk = getFileFromSampleDir("app-with-fake-dex.apk")

		// test simple apk loading
		val args = JadxArgs()
		args.inputFiles.add(sampleApk)
		args.outDir = testDir

		JadxDecompiler(args).use { jadx ->
			jadx.load()
			jadx.save()
			jadx.printErrorsReport()

			// test class print
			for (cls in jadx.getClasses()) {
				println(cls.getCode())
			}

			assertThat(jadx.getClasses()).hasSize(3)
			assertThat(jadx.getErrorsCount()).isEqualTo(0)
		}
	}

	@Test
	@Throws(IOException::class)
	fun testDirectDexInput() {
		JadxDecompiler().use { jadx ->
			FileInputStream(getFileFromSampleDir("hello.dex")).use { input ->
				jadx.addCustomCodeLoader(DexInputPlugin().loadDexFromInputStream(input, "input"))
				jadx.load()
				for (cls in jadx.getClasses()) {
					println(cls.getCode())
				}
				assertThat(jadx.getClasses()).hasSize(1)
				assertThat(jadx.getErrorsCount()).isEqualTo(0)
			}
		}
	}

	@Test
	fun testResourcesLoad() {
		val sampleApk = getFileFromSampleDir("app-with-fake-dex.apk")

		val args = JadxArgs()
		args.inputFiles.add(sampleApk)
		args.outDir = testDir
		args.isSkipSources = true
		JadxDecompiler(args).use { jadx ->
			jadx.load()
			val resources = jadx.getResources()
			assertThat(resources).hasSize(8)
			val arsc = resources.first { it.getType() == ResourceType.ARSC }
			val resContainer = arsc.loadContent()
			val xmlRes = resContainer.getSubFiles().first { it.getName() == "res/values/colors.xml" }
			assertThat(xmlRes.getText())
				.code()
				.containsOne("<color name=\"colorPrimary\">#008577</color>")
		}
	}

	// TODO add more tests

	@Test
	fun testConvertPackageHierarchy() {
		val jadx = JadxDecompiler()
		val root = RootNode(jadx)
		val leafPkgNode = PackageNode.getOrBuild(root, "com.example.app")
		val rootPkgNode = checkNotNull(leafPkgNode.getParentPkg()?.getParentPkg())

		val rootPkg = jadx.convertPackageNode(rootPkgNode)

		assertThat(rootPkg.getFullName()).isEqualTo("com")
		assertThat(rootPkg.isRoot()).isTrue()
		assertThat(rootPkg.isLeaf()).isFalse()
		assertThat(rootPkg.getSubPackages())
			.extracting<String> { it.getFullName() }
			.containsExactly("com.example")
		val middlePkg = rootPkg.getSubPackages()[0]
		assertThat(middlePkg.getFullName()).isEqualTo("com.example")
		assertThat(middlePkg.getSubPackages())
			.extracting<String> { it.getFullName() }
			.containsExactly("com.example.app")
		val leafPkg = middlePkg.getSubPackages()[0]
		assertThat(leafPkg.getFullName()).isEqualTo("com.example.app")
		assertThat(leafPkg.isLeaf()).isTrue()
	}

	@Test
	fun testGetJavaNodeByRefReusesConvertedPackage() {
		val jadx = JadxDecompiler()
		val root = RootNode(jadx)
		val pkgNode = PackageNode.getOrBuild(root, "com.example")

		val javaNode = jadx.getJavaNodeByRef(pkgNode)

		assertThat(javaNode).isNotNull()
		assertThat(checkNotNull(javaNode).getCodeNodeRef()).isSameAs(pkgNode)
		assertThat(jadx.getJavaNodeByRef(pkgNode)).isSameAs(javaNode)
		assertThat(jadx.convertPackageNode(pkgNode)).isSameAs(javaNode)
	}

	companion object {
		private const val TEST_SAMPLES_DIR = "test-samples/"

		@JvmStatic
		fun getFileFromSampleDir(fileName: String): File {
			val resource = JadxDecompilerTest::class.java.classLoader.getResource(TEST_SAMPLES_DIR + fileName)
			assertThat(resource).isNotNull()
			val pathStr = checkNotNull(resource).getFile()
			return File(pathStr)
		}
	}
}
