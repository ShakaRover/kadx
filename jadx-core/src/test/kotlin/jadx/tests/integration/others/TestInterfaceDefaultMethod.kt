package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 接口默认方法：`default`/`static` 修饰符应保留，`abstract` 应被省略。
 */
class TestInterfaceDefaultMethod : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInterfaceDefaultMethodFixture.TestCls::class.java))
			.code()
			.doesNotContain("static default")
			.doesNotContain("abstract")
			.containsOne("void test1();")
			.containsOne("default void test2() {")
			.containsOne("static void test3() {")
	}
}
