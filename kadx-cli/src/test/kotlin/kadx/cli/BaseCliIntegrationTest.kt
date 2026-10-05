package kadx.cli

import kadx.api.plugins.loader.KadxBasePluginLoader
import kadx.core.plugins.files.SingleDirFilesGetter
import kadx.core.utils.Utils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.ArrayList
import java.util.stream.Collectors

/**
 * CLI 集成测试基类：提供统一的参数构造、执行与结果收集工具。
 *
 * **做什么**：在临时目录里执行 [KadxCLI]，并收集输出文件用于断言。
 *
 * **为什么保留 `open`**：子测试类（TestInput/TestExport）需要继承它。
 */
open class BaseCliIntegrationTest {

	@TempDir
	lateinit var testDir: Path

	lateinit var outputDir: Path

	@BeforeEach
	fun setUp() {
		outputDir = testDir.resolve("output")
	}

	fun execKadxCli(sampleName: String, vararg options: String): Int = execKadxCli(buildArgs(options.toList(), sampleName))

	fun execKadxCli(args: Array<String>): Int = KadxCLI.execute(args) { kadxArgs ->
		// 不使用全局配置与插件
		kadxArgs.filesGetter = SingleDirFilesGetter(testDir)
		kadxArgs.pluginLoader = KadxBasePluginLoader()
	}

	fun buildArgs(options: List<String>, vararg inputSamples: String): Array<String> {
		val args = ArrayList<String>(options)
		args.add("-v")
		args.add("-d")
		args.add(outputDir.toAbsolutePath().toString())

		for (inputSample in inputSamples) {
			try {
				val resource = javaClass.classLoader.getResource(inputSample)
				assertThat(resource).isNotNull()
				val sampleFile = resource.toURI().rawPath
				args.add(sampleFile)
			} catch (e: URISyntaxException) {
				fail("Failed to load sample: $inputSample", e)
			}
		}
		return args.toTypedArray()
	}

	@Throws(IOException::class)
	fun decompile(vararg inputSamples: String) {
		val result = execKadxCli(buildArgs(listOf(), *inputSamples))
		assertThat(result).isEqualTo(0)
		val resultJavaFiles = collectJavaFilesInDir(outputDir)
		assertThat(resultJavaFiles).isNotEmpty()

		// 不要把输入文件本身当作资源复制到输出
		for (path in collectFilesInDir(outputDir, LOG_ALL_FILES)) {
			for (inputSample in inputSamples) {
				assertThat(path.toAbsolutePath().toString()).doesNotContain(inputSample)
			}
		}
	}

	fun pathToUniformString(path: Path): String = path.toString().replace('\\', '/')

	fun printFileContent(file: Path): Path = try {
		val content = Files.readString(outputDir.resolve(file))
		val spacer = Utils.strRepeat("=", 70)
		LOG.info("File content: {}\n{}\n{}\n{}", file, spacer, content, spacer)
		file
	} catch (e: IOException) {
		throw RuntimeException("Failed to load file: $file", e)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(BaseCliIntegrationTest::class.java)

		private val LOG_ALL_FILES = PathMatcher { path ->
			LOG.debug("File in result dir: {}", path)
			true
		}

		fun printFiles(files: List<Path>) {
			LOG.info("Output files (count: {}):", files.size)
			for (file in files) {
				LOG.info(" {}", file)
			}
			LOG.info("")
		}

		@Throws(IOException::class)
		fun collectJavaFilesInDir(dir: Path): List<Path> {
			val javaMatcher = dir.fileSystem.getPathMatcher("glob:**.java")
			return collectFilesInDir(dir, javaMatcher)
		}

		@Throws(IOException::class)
		fun collectAllFilesInDir(dir: Path): List<Path> {
			Files.walk(dir).use { pathStream ->
				val files = pathStream
					.filter { Files.isRegularFile(it) }
					.map { dir.relativize(it) }
					.collect(Collectors.toList())
				printFiles(files)
				return files
			}
		}

		@Throws(IOException::class)
		fun collectFilesInDir(dir: Path, matcher: PathMatcher): List<Path> {
			Files.walk(dir).use { pathStream ->
				val files = pathStream
					.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
					.filter { matcher.matches(it) }
					.collect(Collectors.toList())
				printFiles(files)
				return files
			}
		}
	}
}
