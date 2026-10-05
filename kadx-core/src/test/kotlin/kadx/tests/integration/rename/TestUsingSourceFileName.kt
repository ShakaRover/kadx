package kadx.tests.integration.rename

import kadx.api.args.UseSourceNameAsClassNameAlias
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 使用源文件名作为类名别名：NEVER / IF_BETTER / ALWAYS 三种模式，
 * 以及开启混淆还原后 `compiled from: a.java` 的表现。
 */
class TestUsingSourceFileName : SmaliTest() {

	@Test
	fun testNeverUseSourceName() {
		args.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.NEVER
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class b {")
	}

	@Test
	fun testIfBetterUseSourceName() {
		args.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.IF_BETTER
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class a {")
	}

	@Test
	fun testAlwaysUseSourceName() {
		args.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.ALWAYS
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class a {")
	}

	@Test
	fun testNeverUseSourceNameWithDeobf() {
		args.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.NEVER
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class C0000b {")
			.containsOne("compiled from: a.java")
	}

	@Test
	fun testIfBetterUseSourceNameWithDeobf() {
		args.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.IF_BETTER
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class a {")
			.containsOne("compiled from: a.java")
	}

	@Test
	fun testAlwaysUseSourceNameWithDeobf() {
		args.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.ALWAYS
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class a {")
			.containsOne("compiled from: a.java")
	}

	@Test
	fun testDeprecatedDontUseSourceName() {
		// noinspection deprecation
		args.isUseSourceNameAsClassAlias = false
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class b {")
	}

	@Test
	fun testDeprecatedUseSourceName() {
		// noinspection deprecation
		args.isUseSourceNameAsClassAlias = true
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class a {")
	}

	@Test
	fun testDeprecatedDontUseSourceNameWithDeobf() {
		// noinspection deprecation
		args.isUseSourceNameAsClassAlias = false
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class C0000b {")
			.containsOne("compiled from: a.java")
	}

	@Test
	fun testDeprecatedUseSourceNameWithDeobf() {
		// noinspection deprecation
		args.isUseSourceNameAsClassAlias = true
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything
		assertThat(searchCls(loadFromSmaliFiles(), "b"))
			.code()
			.containsOne("class a {")
			.containsOne("compiled from: a.java")
	}
}
