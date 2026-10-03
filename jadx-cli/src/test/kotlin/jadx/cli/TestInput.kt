package jadx.cli

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import java.util.function.Function

/**
 * 输入类型与基础选项的集成测试。
 *
 * **做什么**：覆盖 apk/dex/smali/class、多输入、fallback/simple 模式、仅资源等场景。
 * 断言与原 Java 测试逐条保持一致。
 */
class TestInput : BaseCliIntegrationTest() {

	@Test
	fun testHelp() {
		val result = execJadxCli(arrayOf("--help"))
		assertThat(result).isEqualTo(0)
	}

	@Test
	@Throws(Exception::class)
	fun testApkInput() {
		val result = execJadxCli(buildArgs(listOf(), "samples/small.apk"))
		assertThat(result).isEqualTo(0)
		assertThat(collectAllFilesInDir(outputDir))
			.describedAs("check output files")
			.map(Function { it.fileName.toString() })
			.haveExactly(2, Condition<String>({ f -> f.endsWith(".java") }, "java classes"))
			.haveExactly(9, Condition<String>({ f -> f.endsWith(".xml") }, "xml resources"))
			.haveExactly(1, Condition<String>({ f -> f == "AndroidManifest.xml" }, "manifest"))
			.hasSize(12)
	}

	@Test
	@Throws(Exception::class)
	fun testDexInput() {
		decompile("samples/hello.dex")
	}

	@Test
	@Throws(Exception::class)
	fun testSmaliInput() {
		decompile("samples/HelloWorld.smali")
	}

	@Test
	@Throws(Exception::class)
	fun testClassInput() {
		decompile("samples/HelloWorld.class")
	}

	@Test
	@Throws(Exception::class)
	fun testMultipleInput() {
		decompile("samples/hello.dex", "samples/HelloWorld.smali")
	}

	@Test
	@Throws(Exception::class)
	fun testFallbackMode() {
		val result = execJadxCli(buildArgs(listOf("-f"), "samples/hello.dex"))
		assertThat(result).isEqualTo(0)
		val files = collectJavaFilesInDir(outputDir)
		assertThat(files).hasSize(1)
	}

	@Test
	@Throws(Exception::class)
	fun testSimpleMode() {
		val result = execJadxCli(buildArgs(listOf("--decompilation-mode", "simple"), "samples/hello.dex"))
		assertThat(result).isEqualTo(0)
		val files = collectJavaFilesInDir(outputDir)
		assertThat(files).hasSize(1)
	}

	@Test
	@Throws(Exception::class)
	fun testResourceOnly() {
		val result = execJadxCli(buildArgs(listOf(), "samples/resources-only.apk"))
		assertThat(result).isEqualTo(0)
		val files = collectFilesInDir(outputDir) { path ->
			path.fileName.toString().equals("AndroidManifest.xml", ignoreCase = true)
		}
		assertThat(files).isNotEmpty()
	}

	@Test
	@Throws(Exception::class)
	fun testNoRenameForDefPkg() {
		val result = execJadxCli(buildArgs(listOf("--rename-flags", "none"), "samples/defpkg.smali"))
		assertThat(result).isEqualTo(0)
		val files = collectJavaFilesInDir(outputDir)
		assertThat(files).hasSize(1)
	}
}
