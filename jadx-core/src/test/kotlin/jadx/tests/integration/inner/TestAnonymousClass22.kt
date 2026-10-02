package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类捕获外部局部对象：应还原为对局部变量的引用，而非 `AnotherClass.this`。
 */
class TestAnonymousClass22 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnonymousClass22Fixture.TestCls::class.java))
			.code()
			.containsOne("return another.toString();")
			.doesNotContain("AnotherClass.this")
	}
}
