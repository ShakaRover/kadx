package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 静态方法中的匿名类捕获 final 参数：静态上下文应正确还原。
 */
class TestAnonymousClass7 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestAnonymousClass7Fixture.TestCls::class.java))
			.code()
			.containsOne("public static Runnable test(final double d) {")
			.containsOne("return new Runnable() {")
			.containsOne("public void run() {")
			.containsOne("System.out.println(d);")
			.doesNotContain("synthetic")
	}
}
