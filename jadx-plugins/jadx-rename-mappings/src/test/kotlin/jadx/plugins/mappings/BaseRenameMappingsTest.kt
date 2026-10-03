package jadx.plugins.mappings

import jadx.api.JadxArgs
import jadx.api.JavaClass
import jadx.api.plugins.loader.JadxBasePluginLoader
import jadx.core.plugins.files.SingleDirFilesGetter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

/**
 * 重命名映射测试的公共基类。
 *
 * 负责准备 JUnit5 临时目录、[JadxArgs] 参数以及加载测试资源文件的工具方法，
 * 供具体的映射测试子类复用。
 *
 * 注意：JUnit5 的 `@TempDir` 注入的是**字段**，因此 Kotlin 里必须用
 * `@field:TempDir` 标注到 `lateinit var` 上，才能生成可被反射注入的实例字段。
 */
open class BaseRenameMappingsTest {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(BaseRenameMappingsTest::class.java)
	}

	/** JUnit5 为每个测试方法注入的临时目录。 */
	@field:TempDir
	private lateinit var testDir: Path

	/** 反编译输出目录（位于临时目录下）。 */
	private lateinit var outputDir: Path

	/** 供子类配置的 jadx 参数。 */
	protected lateinit var jadxArgs: JadxArgs

	/** 测试资源所在子目录，由子类在测试方法中设置。 */
	protected var testResDir: String = ""

	@BeforeEach
	fun setUp() {
		outputDir = testDir.resolve("output")
		jadxArgs = JadxArgs()
		jadxArgs.outDir = outputDir.toFile()
		jadxArgs.filesGetter = SingleDirFilesGetter(testDir)
		jadxArgs.pluginLoader = JadxBasePluginLoader()
	}

	/** 从 classpath 加载测试资源文件，资源不存在时断言失败。 */
	fun loadResourceFile(fileName: String): File {
		val path = "$testResDir/$fileName"
		try {
			val resource = javaClass.classLoader.getResource(path)
			assertThat(resource).isNotNull()
			return File(checkNotNull(resource).file)
		} catch (e: Exception) {
			throw RuntimeException("Failed to load resource file: $path", e)
		}
	}

	/** 调试输出所有类的反编译代码。 */
	fun printClassesCode(classes: List<JavaClass>) {
		LOG.debug("Printing code for {} classes:", classes.size)
		for (jCls in classes) {
			LOG.debug("Class: {}\n{}\n---\n", jCls.getFullName(), jCls.getCode())
		}
	}
}
