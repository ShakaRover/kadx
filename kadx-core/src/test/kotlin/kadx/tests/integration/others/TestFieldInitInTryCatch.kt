package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try/catch 中的字段初始化：static 块里的初始化应保留在 try/catch 内，不搬移。
 */
class TestFieldInitInTryCatch : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldInitInTryCatchFixture.TestCls::class.java))
			.code()
			.containsOne("public static final URL A;")
			.containsOne("A = new URL(\"http://www.example.com/\");")
			.containsLines(
				2,
				"try {",
				TestUtils.indent(1) + "A = new URL(\"http://www.example.com/\");",
				"} catch (MalformedURLException e) {",
			)
	}

	@Test
	fun test2() {
		assertThat(getClassNode(TestFieldInitInTryCatchFixture.TestCls2::class.java))
			.code()
			.containsLines(
				2,
				"try {",
				TestUtils.indent(1) + "A = new URL[]{new URL(\"http://www.example.com/\")};",
				"} catch (MalformedURLException e) {",
			)
	}

	@Test
	fun test3() {
		assertThat(getClassNode(TestFieldInitInTryCatchFixture.TestCls3::class.java))
			.code()
			// don't move code from try/catch
			.containsOne("public static final String[] A;")
			.containsOne("A = new String[]{\"a\"};")
	}
}
