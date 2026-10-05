package kadx.cli

import kadx.core.utils.Utils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections

/**
 * [KadxCLIArgs] 的参数解析与配置合并测试。
 *
 * **做什么**：验证反向布尔选项、`--no-src`、参数覆盖，以及 `-P` 插件选项的 Map 合并行为。
 *
 * 注意：`@get:JvmName("isXxx")` 只影响 JVM 方法名，Kotlin 侧仍用属性名 `xxx` 访问。
 */
class KadxCLIArgsTest {

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
		assertThat(override(KadxCLIArgs(), "--no-imports").useImports).isFalse()
		assertThat(override(KadxCLIArgs(), "--no-debug-info").debugInfo).isFalse()
		assertThat(override(KadxCLIArgs(), "").useImports).isTrue()

		var args = KadxCLIArgs()
		args.useImports = false
		assertThat(override(args, "--no-imports").useImports).isFalse()
		args.debugInfo = false
		assertThat(override(args, "--no-debug-info").debugInfo).isFalse()

		args = KadxCLIArgs()
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
		val args = KadxCLIArgs()
		args.pluginOptions = baseMap
		val resultMap = override(args, providedArgs).pluginOptions
		assertThat(resultMap).isEqualTo(expectedMap)
	}

	private fun parse(vararg args: String): KadxCLIArgs = parse(KadxCLIArgs(), *args)

	private fun parse(kadxArgs: KadxCLIArgs, vararg args: String): KadxCLIArgs {
		val argsArray = arrayOf(*args)
		return check(kadxArgs, kadxArgs.processArgs(argsArray))
	}

	private fun override(kadxArgs: KadxCLIArgs, vararg args: String): KadxCLIArgs {
		val argsArray = arrayOf(*args)
		return check(kadxArgs, overrideProvided(kadxArgs, argsArray))
	}

	private fun overrideProvided(kadxArgs: KadxCLIArgs, args: Array<String>): Boolean {
		val jcw = JCommanderWrapper(KadxCLIArgs())
		if (!jcw.parse(args)) {
			return false
		}
		jcw.overrideProvided(kadxArgs)
		return kadxArgs.process(jcw)
	}

	private fun check(kadxArgs: KadxCLIArgs, res: Boolean): KadxCLIArgs {
		assertThat(res).isTrue()
		LOG.info("Kadx args: {}", kadxArgs.toKadxArgs())
		return kadxArgs
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxCLIArgsTest::class.java)
	}
}
