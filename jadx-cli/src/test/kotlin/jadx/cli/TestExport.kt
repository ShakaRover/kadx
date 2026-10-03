package jadx.cli

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import java.util.function.Function

/**
 * 导出（普通输出与 Gradle 工程）的集成测试。
 *
 * **做什么**：验证 apk/aar/class 在不同导出模板下的输出文件集合。
 * 断言与原 Java 测试逐条保持一致。
 *
 * 说明：AssertJ 的 `map` 同时有 `Function` 与 `ThrowingExtractor` 两个重载，
 * Kotlin lambda 无法自动消歧，因此显式包一层 `Function { ... }`。
 */
class TestExport : BaseCliIntegrationTest() {

	@Test
	@Throws(Exception::class)
	fun testBasicExport() {
		val result = execJadxCli("samples/small.apk")
		assertThat(result).isEqualTo(0)
		assertThat(collectAllFilesInDir(outputDir))
			.map(Function { pathToUniformString(it) })
			.haveExactly(2, Condition<String>({ f -> f.startsWith("sources/") && f.endsWith(".java") }, "sources"))
			.haveExactly(10, Condition<String>({ f -> f.startsWith("resources/") }, "resources"))
			.haveExactly(1, Condition<String>({ f -> f == "resources/AndroidManifest.xml" }, "manifest"))
			.hasSize(12)
	}

	@Test
	@Throws(Exception::class)
	fun testGradleExportApk() {
		val result = execJadxCli("samples/small.apk", "--export-gradle")
		assertThat(result).isEqualTo(0)
		assertThat(collectAllFilesInDir(outputDir))
			.describedAs("check output files")
			.map(Function { pathToUniformString(it) })
			.haveExactly(2, Condition<String>({ f -> f.endsWith(".java") }, "java classes"))
			.haveExactly(0, Condition<String>({ f -> f.endsWith("classes.dex") }, "dex files"))
			.hasSize(15)
	}

	@Test
	@Throws(Exception::class)
	fun testGradleExportAAR() {
		val result = execJadxCli("samples/test-lib.aar", "--export-gradle")
		assertThat(result).isEqualTo(0)
		assertThat(collectAllFilesInDir(outputDir))
			.describedAs("check output files")
			.map(Function { printFileContent(it) })
			.map(Function { pathToUniformString(it) })
			.haveExactly(1, Condition<String>({ f -> f.startsWith("lib/src/main/java/") && f.endsWith(".java") }, "java"))
			.haveExactly(0, Condition<String>({ f -> f.endsWith(".jar") }, "jar files"))
			.hasSize(8)
	}

	@Test
	@Throws(Exception::class)
	fun testGradleExportSimpleJava() {
		val result = execJadxCli("samples/HelloWorld.class", "--export-gradle")
		assertThat(result).isEqualTo(0)
		assertThat(collectAllFilesInDir(outputDir))
			.describedAs("check output files")
			.map(Function { printFileContent(it) })
			.map(Function { pathToUniformString(it) })
			.haveExactly(1, Condition<String>({ f -> f.endsWith(".java") && f.startsWith("app/src/main/java/") }, "java"))
			.haveExactly(0, Condition<String>({ f -> f.endsWith(".class") }, "class files"))
			.haveExactly(1, Condition<String>({ f -> f == "settings.gradle.kts" }, "settings"))
			.haveExactly(1, Condition<String>({ f -> f == "app/build.gradle.kts" }, "build"))
			.hasSize(3)
	}

	@Test
	@Throws(Exception::class)
	fun testGradleExportInvalidType() {
		val result = execJadxCli("samples/HelloWorld.class", "--export-gradle-type", "android-app")
		assertThat(result).isEqualTo(0)
		// 期望使用 'android-app' 模板，但大部分字段会是 UNKNOWN
		assertThat(collectAllFilesInDir(outputDir))
			.describedAs("check output files")
			.map(Function { printFileContent(it) })
			.map(Function { pathToUniformString(it) })
			.haveExactly(1, Condition<String>({ f -> f.endsWith(".java") && f.startsWith("app/src/main/java/") }, "java"))
			.haveExactly(1, Condition<String>({ f -> f == "settings.gradle" }, "settings"))
			.haveExactly(1, Condition<String>({ f -> f == "build.gradle" }, "build"))
			.haveExactly(1, Condition<String>({ f -> f == "app/build.gradle" }, "app build"))
			.hasSize(4)
	}
}
