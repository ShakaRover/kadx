package kadx.tests.api

import kadx.api.ICodeInfo
import kadx.api.KadxArgs
import kadx.api.ResourceFile
import kadx.api.ResourceFileContainer
import kadx.api.ResourceFileContent
import kadx.api.ResourceType
import kadx.api.impl.SimpleCodeInfo
import kadx.core.dex.nodes.RootNode
import kadx.core.export.ExportGradle
import kadx.core.export.ExportGradleType
import kadx.core.xmlgen.ResContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Gradle 导出测试基类：加载 manifest 资源并生成 Gradle 工程，供子类断言生成文件内容。
 */
abstract class ExportGradleTest {

	companion object {
		private const val MANIFEST_TESTS_DIR = "manifest"
	}

	private val root = RootNode(KadxArgs())

	@field:TempDir
	private lateinit var exportDir: File

	protected fun loadResource(filename: String): ICodeInfo = SimpleCodeInfo(loadResourceContent(MANIFEST_TESTS_DIR, filename))

	private fun loadFileContent(filePath: File): String = try {
		Files.readString(filePath.toPath())
	} catch (e: IOException) {
		fail<Nothing>("Loading file failed", e)
	}

	private fun loadResourceContent(dir: String, filename: String): String {
		val resPath = "$dir/$filename"
		return try {
			javaClass.classLoader.getResourceAsStream(resPath).use { input ->
				if (input == null) {
					fail<Nothing>("Resource not found: $resPath")
				} else {
					String(input.readAllBytes(), StandardCharsets.UTF_8)
				}
			}
		} catch (e: Exception) {
			fail<Nothing>("Loading file failed: $resPath", e)
		}
	}

	protected fun getRootNode(): RootNode = root

	protected fun exportGradle(manifestFilename: String, stringsFileName: String) {
		val androidManifest =
			ResourceFileContent("AndroidManifest.xml", ResourceType.MANIFEST, loadResource(manifestFilename))
		val strings = ResContainer.textResource(stringsFileName, loadResource(stringsFileName))
		val arsc = ResContainer.resourceTable("resources.arsc", listOf(strings), SimpleCodeInfo("empty"))
		val arscFile = ResourceFileContainer("resources.arsc", ResourceType.ARSC, arsc)
		val resources = listOf<ResourceFile>(androidManifest, arscFile)

		root.args.exportGradleType = ExportGradleType.ANDROID_APP
		val export = ExportGradle(root, exportDir, resources)
		val outDirs = export.init()
		assertThat(outDirs.srcOutDir).exists()
		assertThat(outDirs.resOutDir).exists()
		export.generateGradleFiles()
	}

	protected fun getAppGradleBuild(): String = loadFileContent(File(exportDir, "app/build.gradle"))

	protected fun getSettingsGradle(): String = loadFileContent(File(exportDir, "settings.gradle"))

	protected fun getGradleProperiesFile(): File = File(exportDir, "gradle.properties")

	protected fun getGradleProperties(): String = loadFileContent(getGradleProperiesFile())
}
