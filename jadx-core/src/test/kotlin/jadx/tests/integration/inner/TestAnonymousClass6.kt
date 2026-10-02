package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类捕获 final 基本类型参数：形参 `final` 修饰与捕获变量应正确还原。
 */
class TestAnonymousClass6 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestAnonymousClass6Fixture.TestCls::class.java))
			.code()
			.containsOne("public Runnable test(final double d) {")
			.containsOne("return new Runnable() {")
			.containsOne("public void run() {")
			.containsOne("System.out.println(d);")
			.doesNotContain("synthetic")
	}
}
