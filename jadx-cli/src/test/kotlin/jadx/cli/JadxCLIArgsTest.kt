package jadx.cli

import jadx.core.utils.Utils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections

/**
 * [JadxCLIArgs] 的参数解析与配置合并测试。
 *
 * **做什么**：验证反向布尔选项、`--no-src`、参数覆盖，以及 `-P` 插件选项的 Map 合并行为。
 *
 * 注意：`@get:JvmName("isXxx")` 只影响 JVM 方法名，Kotlin 侧仍用属性名 `xxx` 访问。
 */
class JadxCLIArgsTest {

	@Test
	fun testInvertedBooleanOption() {
		assertThat(parse("--no-replace-consts").replaceConsts).isFalse()
		assertThat(parse("").replaceConsts).isTrue()
	}

	@Test
	fun testEscapeUnicodeOption() {
		assertThat(parse("--escape-unicode").escapeUnicode).isTrue()
		assertThat(parse("").escapeUnicode).isFalse()
	}

	@Test
	fun testSrcOption() {
		assertThat(parse("--no-src").skipSources).isTrue()
		assertThat(parse("-s").skipSources).isTrue()
		assertThat(parse("").skipSources).isFalse()
	}

	@Test
	fun testOptionsOverride() {
		assertThat(override(JadxCLIArgs(), "--no-imports").useImports).isFalse()
		assertThat(override(JadxCLIArgs(), "--no-debug-info").debugInfo).isFalse()
		assertThat(override(JadxCLIArgs(), "").useImports).isTrue()

		var args = JadxCLIArgs()
		args.useImports = false
		assertThat(override(args, "--no-imports").useImports).isFalse()
		args.debugInfo = false
		assertThat(override(args, "--no-debug-info").debugInfo).isFalse()

		args = JadxCLIArgs()
		args.useImports = false
		assertThat(override(args, "").useImports).isFalse()
	}

	@Test
	fun testPluginOptionsOverride() {
		// 向空 base map 添加 key
		checkPluginOptionsMerge(
			Collections.emptyMap(),
			"-Poption=otherValue",
			Utils.newConstStringMap("option", "otherValue"),
		)

		// 覆盖一个 key
		checkPluginOptionsMerge(
			Utils.newConstStringMap("option", "value"),
			"-Poption=otherValue",
			Utils.newConstStringMap("option", "otherValue"),
		)

		// 合并不同的 key
		checkPluginOptionsMerge(
			Collections.singletonMap("option1", "value1"),
			"-Poption2=otherValue2",
			Utils.newConstStringMap("option1", "value1", "option2", "otherValue2"),
		)

		// 合并并覆盖
		checkPluginOptionsMerge(
			Utils.newConstStringMap("option1", "value1", "option2", "value2"),
			"-Poption2=otherValue2",
			Utils.newConstStringMap("option1", "value1", "option2", "otherValue2"),
		)
	}

	private fun checkPluginOptionsMerge(baseMap: Map<String, String>, providedArgs: String, expectedMap: Map<String, String>) {
		val args = JadxCLIArgs()
		args.pluginOptions = baseMap
		val resultMap = override(args, providedArgs).pluginOptions
		assertThat(resultMap).isEqualTo(expectedMap)
	}

	private fun parse(vararg args: String): JadxCLIArgs = parse(JadxCLIArgs(), *args)

	private fun parse(jadxArgs: JadxCLIArgs, vararg args: String): JadxCLIArgs {
		val argsArray = arrayOf(*args)
		return check(jadxArgs, jadxArgs.processArgs(argsArray))
	}

	private fun override(jadxArgs: JadxCLIArgs, vararg args: String): JadxCLIArgs {
		val argsArray = arrayOf(*args)
		return check(jadxArgs, overrideProvided(jadxArgs, argsArray))
	}

	private fun overrideProvided(jadxArgs: JadxCLIArgs, args: Array<String>): Boolean {
		val jcw = JCommanderWrapper(JadxCLIArgs())
		if (!jcw.parse(args)) {
			return false
		}
		jcw.overrideProvided(jadxArgs)
		return jadxArgs.process(jcw)
	}

	private fun check(jadxArgs: JadxCLIArgs, res: Boolean): JadxCLIArgs {
		assertThat(res).isTrue()
		LOG.info("Jadx args: {}", jadxArgs.toJadxArgs())
		return jadxArgs
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxCLIArgsTest::class.java)
	}
}
