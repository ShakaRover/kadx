package kadx.tests.integration.names

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字段名与包名冲突：字段 `second` 与包 `second` 同名时，字段需被重命名以避免歧义。
 */
class TestFieldCollideWithPackage : SmaliTest() {
	// @formatter:off
	/*
		-----------------------------------------------------------
		package first;

		public class A {
			public A first;
			public second.A second;

			public String test() {
				return second.A.call(); // compiler treat 'second' as field name
			}
		}
		-----------------------------------------------------------
		package second;

		public class A {
			public static String call() {
				return null;
			}
		}
		-----------------------------------------------------------
	 */
	// @formatter:on

	@Test
	fun test() {
		val clsList = loadFromSmaliFiles()
		val firstA = searchCls(clsList, "first.A")
		val code = firstA.getCode().toString()

		assertThat(code)
			.contains("second.A")
			.doesNotContain("public second.A second;")
		// expect field to be renamed
	}

	@Test
	fun testWithoutImports() {
		args.isUseImports = false
		loadFromSmaliFiles()
	}

	@Test
	fun testWithDeobfuscation() {
		enableDeobfuscation()
		loadFromSmaliFiles()
	}
}
