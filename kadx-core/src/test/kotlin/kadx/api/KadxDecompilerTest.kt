package kadx.api

import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode
import kadx.plugins.input.dex.DexInputPlugin
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class KadxDecompilerTest {

	@field:TempDir
	private lateinit var testDir: File

	@Test
	fun testExampleUsage() {
		val sampleApk = getFileFromSampleDir("app-with-fake-dex.apk")

		// test simple apk loading
		val args = KadxArgs()
		args.inputFiles.add(sampleApk)
		args.outDir = testDir

		KadxDecompiler(args).use { kadx ->
			kadx.load()
			kadx.save()
			kadx.printErrorsReport()

			// test class print
			for (cls in kadx.getClasses()) {
				println(cls.getCode())
			}

			assertThat(kadx.getClasses()).hasSize(3)
			assertThat(kadx.getErrorsCount()).isEqualTo(0)
		}
	}

	@Test
	@Throws(IOException::class)
	fun testDirectDexInput() {
		KadxDecompiler().use { kadx ->
			FileInputStream(getFileFromSampleDir("hello.dex")).use { input ->
				kadx.addCustomCodeLoader(DexInputPlugin().loadDexFromInputStream(input, "input"))
				kadx.load()
				for (cls in kadx.getClasses()) {
					println(cls.getCode())
				}
				assertThat(kadx.getClasses()).hasSize(1)
				assertThat(kadx.getErrorsCount()).isEqualTo(0)
			}
		}
	}

	@Test
	fun testResourcesLoad() {
		val sampleApk = getFileFromSampleDir("app-with-fake-dex.apk")

		val args = KadxArgs()
		args.inputFiles.add(sampleApk)
		args.outDir = testDir
		args.isSkipSources = true
		KadxDecompiler(args).use { kadx ->
			kadx.load()
			val resources = kadx.getResources()
			assertThat(resources).hasSize(8)
			val arsc = resources.first { it.getType() == ResourceType.ARSC }
			val resContainer = arsc.loadContent()
			val xmlRes = resContainer.subFiles.first { it.name == "res/values/colors.xml" }
			assertThat(xmlRes.text)
				.code()
				.containsOne("<color name=\"colorPrimary\">#008577</color>")
		}
	}

	// TODO add more tests

	@Test
	fun testConvertPackageHierarchy() {
		val kadx = KadxDecompiler()
		val root = RootNode(kadx)
		val leafPkgNode = PackageNode.getOrBuild(root, "com.example.app")
		val rootPkgNode = checkNotNull(leafPkgNode.getParentPkg()?.getParentPkg())

		val rootPkg = kadx.convertPackageNode(rootPkgNode)

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
		val kadx = KadxDecompiler()
		val root = RootNode(kadx)
		val pkgNode = PackageNode.getOrBuild(root, "com.example")

		val javaNode = kadx.getJavaNodeByRef(pkgNode)

		assertThat(javaNode).isNotNull()
		assertThat(checkNotNull(javaNode).getCodeNodeRef()).isSameAs(pkgNode)
		assertThat(kadx.getJavaNodeByRef(pkgNode)).isSameAs(javaNode)
		assertThat(kadx.convertPackageNode(pkgNode)).isSameAs(javaNode)
	}

	companion object {
		private const val TEST_SAMPLES_DIR = "test-samples/"

		@JvmStatic
		fun getFileFromSampleDir(fileName: String): File {
			val resource = KadxDecompilerTest::class.java.classLoader.getResource(TEST_SAMPLES_DIR + fileName)
			assertThat(resource).isNotNull()
			val pathStr = checkNotNull(resource).getFile()
			return File(pathStr)
		}
	}
}
