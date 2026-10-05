package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类代码生成：接口方法不应带多余的 public 修饰符，抽象类修饰符顺序应正确。
 */
class TestClassGen : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestClassGenFixture.TestCls::class.java))
			.code()
			.contains("public interface I {")
			.contains(indent(2) + "int test();")
			.doesNotContain("public int test();")
			.contains(indent(2) + "int test3();")
			.contains("public static abstract class A {")
			.contains(indent(2) + "public abstract int test2();")
	}
}
