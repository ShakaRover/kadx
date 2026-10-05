package kadx.tests.integration.names

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 局部变量名与包名冲突：局部变量 `second` 与包 `second` 同名时，
 * 引用包内类需保留全限定名以免解析错误。
 */
class TestLocalVarCollideWithPackage : SmaliTest() {
	// @formatter:off
	/*
		-----------------------------------------------------------
		package first;

		import pkg.Second;

		public class A {
			public String test() {
				Second second = new Second();
				second.A.call(); // collision
				return second.str;
			}
		}
		-----------------------------------------------------------
		package pkg;

		public class Second {
			public String str;
		}
		-----------------------------------------------------------
		package second;

		public class A {
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
			.contains("second.A.call();")
			.doesNotContain("Second second = new Second();")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		loadFromSmaliFiles()
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
